import asyncio
import json
import os
import pwd
import shutil
import time

import decky

from version import read_version
from device import build_device
from settings_store import SettingsStore
from effects import (
    BATTERY_BANDS,
    TEMPERATURE_BANDS,
    EffectEngine,
    interpolate_gradient,
)
from lighting_profiles import LightingProfileStore
from ambilight import Ambilight
from audio import AudioReactive
from power_supply import charger_online, battery_level
from thermal import apu_temperature
from performance import gpu_busy_percent, CpuSampler
from saved_gradients import upsert_gradient, remove_gradient
from hhd_rgb_control import HhdRgbControl
import self_updater
from report import collector as report_collector
from report import client as report_client

_REPORT_APP = "colores"
_REPORT_SERVICE_URL = os.environ.get(
    "COLORES_REPORT_URL", "https://bug-collector-khaki.vercel.app/api/report"
)

SENSOR_BAND_DEFAULTS = {
    "battery": BATTERY_BANDS,
    "temperature": TEMPERATURE_BANDS,
}
SENSOR_BAND_LIMITS = {"battery": 100, "temperature": 120}

DEFAULTS = {
    "power": True,
    "brightness": 80,
    "mode": "solid",
    "color": [255, 255, 255],
    "gradient": [[0, 196, 255], [136, 86, 255]],
    "gradient_speed": 30,
    "effect": {"id": "breathing", "speed": 50, "use_gradient": False},
    "ambilight": {"vividness": 27, "smoothing": 75, "fps": 10, "sampling": "columns"},
    "saved_gradients": [],
    "enabled_experiments": [],
    "power_led_off": False,
    "charger_only": False,
    "force_control": False,
    "hhd_rgb_restore": None,
    "battery_breathe": True,
    "temperature_breathe": True,
    "sensor_bands": dict(SENSOR_BAND_DEFAULTS),
    "remember_startup": True,
    "startup_factory": None,
}

PROFILE_KEYS = (
    "brightness",
    "mode",
    "color",
    "gradient",
    "gradient_speed",
    "effect",
    "ambilight",
    "battery_breathe",
    "temperature_breathe",
)
PROFILE_DEFAULTS = {key: DEFAULTS[key] for key in PROFILE_KEYS}
GLOBAL_KEYS = tuple(key for key in DEFAULTS if key not in PROFILE_KEYS)

CHARGER_POLL_INTERVAL = 3.0

STARTUP_PERSIST_DELAY = 1.0

FORCE_CONTROL_INTERVAL = 2.0

ACQUIRE_ATTEMPTS = 20
ACQUIRE_INTERVAL = 1.0
REASSERT_DELAY = 5.0
RESUME_POLL_INTERVAL = 1.0
RESUME_REAPPLY_DELAY = 2.0
RESUME_SUSPEND_THRESHOLD = 1.0
RESUME_RECONNECT_ATTEMPTS = 3
RESUME_RECONNECT_INTERVAL = 1.0


def _rgb(values):
    return {"r": values[0], "g": values[1], "b": values[2]}


def _saved(entry):
    return {"name": entry["name"], "stops": [_rgb(c) for c in entry["stops"]]}


def _parse_sensor_bands(sensor: str, bands) -> tuple:
    if (
        sensor not in SENSOR_BAND_LIMITS
        or not isinstance(bands, (list, tuple))
        or len(bands) != 5
    ):
        raise ValueError("invalid sensor bands")
    parsed = []
    for entry in bands:
        if isinstance(entry, dict):
            threshold = entry.get("min")
            color = entry.get("color")
        elif isinstance(entry, (list, tuple)) and len(entry) == 2:
            threshold, color = entry
        else:
            raise ValueError("invalid sensor band")
        if isinstance(color, dict):
            color = [color.get(channel) for channel in ("r", "g", "b")]
        if (
            isinstance(threshold, bool)
            or not isinstance(threshold, int)
            or threshold < 0
            or threshold > SENSOR_BAND_LIMITS[sensor]
            or not isinstance(color, (list, tuple))
            or len(color) != 3
            or any(
                isinstance(value, bool)
                or not isinstance(value, int)
                or value < 0
                or value > 255
                for value in color
            )
        ):
            raise ValueError("invalid sensor band")
        parsed.append((threshold, tuple(color)))
    thresholds = [threshold for threshold, _ in parsed]
    if thresholds[-1] != 0 or any(
        high <= low for high, low in zip(thresholds, thresholds[1:])
    ):
        raise ValueError("invalid sensor thresholds")
    return tuple(parsed)


def _normalize_sensor_bands(sensor: str, bands) -> tuple:
    try:
        return _parse_sensor_bands(sensor, bands)
    except (TypeError, ValueError):
        return SENSOR_BAND_DEFAULTS[sensor]


def _normalize_sensor_settings(stored) -> dict:
    values = stored if isinstance(stored, dict) else {}
    return {
        sensor: _normalize_sensor_bands(sensor, values.get(sensor))
        for sensor in SENSOR_BAND_DEFAULTS
    }


def _serialize_sensor_bands(bands) -> list:
    return [{"min": threshold, "color": _rgb(color)} for threshold, color in bands]


def _temperature_reading(value):
    return None if value is None else round(float(value), 1)


def _suspend_clock():
    try:
        return time.clock_gettime(time.CLOCK_BOOTTIME) - time.monotonic()
    except (AttributeError, OSError):
        return None


def _normalize_ambilight_settings(settings: dict | None) -> dict:
    stored = settings or {}
    normalized = {**DEFAULTS["ambilight"], **stored}
    if "vividness" in stored:
        vividness = stored["vividness"]
    else:
        saturation = max(100, min(250, int(stored.get("saturation", 140))))
        vividness = round((saturation - 100) / 1.5)
    normalized["vividness"] = max(0, min(100, int(vividness)))
    normalized.pop("saturation", None)
    return normalized


def _user_creds():
    try:
        entry = pwd.getpwnam(decky.DECKY_USER)
        return f"/run/user/{entry.pw_uid}", entry.pw_uid, entry.pw_gid
    except (KeyError, AttributeError):
        return "/run/user/1000", 1000, 1000


class Plugin:
    def _init(self) -> None:
        if getattr(self, "_ready", False):
            return
        self._stopping = False
        self._hhd_rgb_lock = asyncio.Lock()
        self._hhd_rgb_status = None
        self._setup_device(self._build_context())
        self._store = SettingsStore(
            os.path.join(decky.DECKY_PLUGIN_SETTINGS_DIR, "state.json")
        )
        legacy = self._store.load(DEFAULTS)
        legacy["ambilight"] = _normalize_ambilight_settings(
            legacy.get("ambilight")
        )
        legacy["effect"] = {**DEFAULTS["effect"], **legacy["effect"]}
        legacy["sensor_bands"] = _normalize_sensor_settings(
            legacy.get("sensor_bands")
        )
        self._base_settings = dict(legacy)
        self._profiles = LightingProfileStore(
            os.path.join(decky.DECKY_PLUGIN_SETTINGS_DIR, "profiles.json"),
            PROFILE_DEFAULTS,
            {key: legacy[key] for key in PROFILE_KEYS},
        )
        self._current_app_key = None
        self._sync_effective_profile()
        self._hhd_rgb = HhdRgbControl()
        self._ac_online = charger_online()
        level = battery_level()
        self._battery_level = 100 if level is None else level
        self._apu_temp = apu_temperature()
        self._apply_power_led()
        self._capture_startup_factory()
        self._ready = True

    def _capture_startup_factory(self) -> None:
        if self._settings.get("startup_factory") is not None:
            return
        controller = self._controller
        if hasattr(controller, "read_startup"):
            factory = controller.read_startup()
            if factory:
                self._settings["startup_factory"] = factory
                self._persist_settings()

    def _build_context(self) -> dict:
        ambilight_available = shutil.which("gst-launch-1.0") is not None
        return build_device(ambilight=ambilight_available)

    def _setup_device(self, ctx: dict) -> None:
        self._device = ctx["info"]
        self._capabilities = ctx["capabilities"]
        self._zones = self._capabilities.get("zones", 1) or 1
        self._controller = ctx["device"]
        self._power_led = ctx.get("power_led")
        self._cpu_sampler = CpuSampler()
        max_render_fps = self._capabilities.get("maxRenderFps", 30)
        self._engine = EffectEngine(self._render, self._zones, max_fps=max_render_fps)
        runtime_dir, uid, gid = _user_creds()
        self._ambilight = Ambilight(
            self._render,
            self._zones,
            runtime_dir,
            uid,
            gid,
            layout=self._capabilities.get("layout"),
            max_fps=max_render_fps,
        )
        self._audio = AudioReactive(self._render, self._zones, runtime_dir, uid, gid)

    def _reprobe_device(self) -> bool:
        if self._controller.available:
            return True
        ctx = self._build_context()
        if not ctx["device"].available:
            return False
        self._ambilight.stop()
        self._engine.stop()
        self._setup_device(ctx)
        return True

    async def _acquire_and_reassert(self) -> None:
        try:
            for _ in range(ACQUIRE_ATTEMPTS):
                if self._controller.available:
                    break
                await asyncio.sleep(ACQUIRE_INTERVAL)
                if self._reprobe_device():
                    self._apply()
            await asyncio.sleep(REASSERT_DELAY)
            self._reprobe_device()
            if not self._wants_render_loop():
                self._apply()
        except Exception as error:
            decky.logger.warning("Colores: acquire/reassert failed: %s", error)

    def _apply_power_led(self) -> None:
        if not (self._power_led and self._capabilities.get("powerLed")):
            return
        if self._settings.get("power_led_off", False) and not self._power_led.set(True):
            decky.logger.warning("Colores: power LED apply on load failed")

    async def get_version(self) -> str:
        return read_version()

    async def check_update(self, force: bool = False) -> dict:
        self._init()
        return self_updater.check(force)

    async def install_update(self) -> dict:
        self._init()
        return self_updater.install()

    async def restart_loader(self) -> None:
        self_updater.restart_loader()

    async def submit_report(self, categories=None, text: str = "") -> dict:
        self._init()
        home, hostname = self._redact_ids()
        try:
            bundle = await self._build_report_bundle(categories, text, home, hostname)
        except Exception as e:  # noqa: BLE001
            decky.logger.error("Colores: report bundle failed: %s", e)
            bundle = report_collector.build_bundle(
                app=_REPORT_APP, categories=categories, text=text,
                environment={}, capabilities={}, state={}, stores={}, logs=[],
                home=home, hostname=hostname,
            )
            bundle["error"] = "bundle_incomplete"
        res = await asyncio.get_running_loop().run_in_executor(
            None, lambda: report_client.submit(_REPORT_SERVICE_URL, bundle)
        )
        if res.get("ok"):
            decky.logger.info("Colores: report sent: %s", res.get("code"))
            return {"ok": True, "code": res["code"], "issue_url": res.get("issue_url")}
        path = report_client.save_local(
            getattr(decky, "DECKY_PLUGIN_LOG_DIR", "."), bundle
        )
        decky.logger.warning(
            "Colores: report send failed (%s); saved to %s", res.get("error"), path
        )
        return {"ok": False, "error": res.get("error", "unknown"), "saved_path": path}

    def _redact_ids(self):
        home = getattr(decky, "DECKY_USER_HOME", None) or os.path.expanduser("~")
        try:
            import socket

            hostname = socket.gethostname()
        except Exception:  # noqa: BLE001
            hostname = None
        return home, hostname

    async def _build_report_bundle(self, categories, text, home, hostname) -> dict:
        loop = asyncio.get_running_loop()
        try:
            state = await self.get_state()
        except Exception:  # noqa: BLE001
            state = {}
        capabilities = report_collector.capabilities_from(
            state,
            driver=type(self._controller).__name__,
            route=getattr(self._controller, "route", None),
            led_path=getattr(self._controller, "led_path", None),
            last_error=getattr(self._controller, "last_error", None),
        )

        def _assemble() -> dict:
            logs = report_collector.tail_logs(
                getattr(decky, "DECKY_PLUGIN_LOG_DIR", ""), home=home, hostname=hostname
            )
            snapshot = report_collector.sysfs_snapshot(home=home, hostname=hostname)
            kernel = report_collector.kernel_logs(
                self._run_capture,
                extra=report_collector.rgb_conflict_cmds(
                    bool(capabilities.get("conflicts_with_system_rgb"))
                ),
                home=home,
                hostname=hostname,
            )
            return report_collector.build_bundle(
                app=_REPORT_APP,
                categories=categories,
                text=text,
                environment=self._report_environment(),
                capabilities=capabilities,
                state=state,
                stores=self._report_stores(),
                logs=logs,
                kernel=kernel,
                sysfs=snapshot,
                home=home,
                hostname=hostname,
            )

        return await loop.run_in_executor(None, _assemble)

    def _run_capture(self, cmd) -> str | None:
        try:
            import subprocess

            env = dict(os.environ)
            env.pop("LD_LIBRARY_PATH", None)
            env.pop("LD_PRELOAD", None)
            r = subprocess.run(
                cmd, capture_output=True, text=True, timeout=5, env=env,
            )  # noqa: S603
            return r.stdout or ""
        except Exception:  # noqa: BLE001
            return None

    def _report_environment(self) -> dict:
        def _dmi(name):
            try:
                with open(f"/sys/class/dmi/id/{name}") as f:
                    return f.read().strip()
            except OSError:
                return None

        os_name = None
        try:
            rel = {}
            with open("/etc/os-release") as f:
                for line in f:
                    if "=" in line:
                        k, v = line.rstrip().split("=", 1)
                        rel[k] = v.strip('"')
            os_name = rel.get("PRETTY_NAME") or rel.get("NAME")
        except Exception:  # noqa: BLE001
            pass
        kernel = None
        try:
            u = os.uname()
            kernel = f"{u.sysname} {u.release}"
        except Exception:  # noqa: BLE001
            pass
        dev = getattr(self, "_device", {}) or {}
        return {
            "plugin_version": read_version(),
            "decky_version": getattr(decky, "DECKY_VERSION", None),
            "device_key": dev.get("name"),
            "product_name": dev.get("product") or _dmi("product_name"),
            "product_family": _dmi("product_family"),
            "board_name": dev.get("board") or _dmi("board_name"),
            "os": os_name,
            "kernel": kernel,
        }

    def _report_stores(self) -> dict:
        base = getattr(decky, "DECKY_PLUGIN_SETTINGS_DIR", "")
        try:
            with open(os.path.join(base, "state.json")) as f:
                settings = json.load(f)
        except Exception:  # noqa: BLE001
            settings = getattr(self, "_settings", {})
        stores = {"settings": settings}
        profiles = getattr(self, "_profiles", None)
        if profiles is not None:
            stores["profiles"] = {
                "profiles_configured": profiles.configured_count(),
                "watcher_state": "game" if self._current_app_key else "global",
                "fallback_reason": None,
            }
        return stores

    def _serialized_saved(self) -> list:
        return [_saved(g) for g in self._settings["saved_gradients"]]

    def _merged_capabilities(self) -> dict:
        caps = dict(self._capabilities)
        states = dict(caps.get("states", {}))
        settings = getattr(self, "_settings", getattr(self, "_base_settings", {}))
        enabled = set(settings.get("enabled_experiments", []))
        for feature, state in states.items():
            if state == "experimental":
                caps[feature] = feature in enabled
            elif state == "supported":
                caps[feature] = True
            else:
                caps[feature] = False
        caps["enabledExperiments"] = sorted(enabled)
        route = getattr(self._controller, "route", None)
        if route:
            caps["rgbRoute"] = route
        return caps

    def _mode_is_supported(self, mode: str) -> bool:
        requirements = {
            "gradient": "color",
            "effect": "effects",
            "ambient": "ambilight",
            "vu": "audioMode",
            "battery": "batteryMode",
            "temperature": "temperatureMode",
            "performance": "performanceMode",
            "clock": "clockMode",
        }
        if mode == "solid":
            return True
        capability = requirements.get(mode)
        return capability is not None and bool(
            self._merged_capabilities().get(capability, False)
        )

    def _sync_effective_profile(self) -> None:
        profile = self._profiles.effective(self._current_app_key)
        effective = dict(self._base_settings)
        effective.update(profile)
        effective["ambilight"] = _normalize_ambilight_settings(
            effective.get("ambilight")
        )
        effective["effect"] = {
            **DEFAULTS["effect"],
            **effective.get("effect", {}),
        }
        if not self._mode_is_supported(effective["mode"]):
            effective["mode"] = "solid"
        self._settings = effective

    def _persist_settings(self) -> None:
        if not hasattr(self, "_profiles"):
            self._store.save(self._settings)
            return
        for key in GLOBAL_KEYS:
            if key in self._settings:
                self._base_settings[key] = self._settings[key]
        stored = dict(self._base_settings)
        stored.update(self._profiles.editable("global", None))
        self._store.save(stored)

    def _edited_scope_is_effective(self, scope: str, app_key) -> bool:
        if scope == "global":
            return self._current_app_key is None or self._profiles.is_following_global(
                self._current_app_key
            )
        return (
            self._current_app_key is not None
            and str(app_key) == self._current_app_key
            and not self._profiles.is_following_global(self._current_app_key)
        )

    def _serialized_profile(self, profile: dict) -> dict:
        return {
            "brightness": profile["brightness"],
            "mode": profile["mode"],
            "color": _rgb(profile["color"]),
            "gradient": [_rgb(color) for color in profile["gradient"]],
            "gradientSpeed": profile["gradient_speed"],
            "effect": {
                "id": profile["effect"]["id"],
                "speed": profile["effect"]["speed"],
                "useGradient": profile["effect"].get("use_gradient", False),
            },
            "ambilight": profile["ambilight"],
            "batteryBreathe": profile.get("battery_breathe", True),
            "temperatureBreathe": profile.get("temperature_breathe", True),
        }

    def _profile_state(self, scope: str, app_key, profile=None) -> dict:
        key = None if app_key is None else str(app_key)
        state = self._profiles.scope_state(key)
        selected = profile or self._profiles.editable(scope, key)
        return {
            "scope": scope,
            "appKey": key,
            "profile": self._serialized_profile(selected),
            "hasGameProfile": state["has_game_profile"],
            "followsGlobal": state["follows_global"],
            "activeProfile": "default",
        }

    async def get_profile_state(self, scope: str, app_key=None) -> dict:
        self._init()
        return self._profile_state(scope, app_key)

    async def patch_profile(self, scope: str, app_key, changes: dict) -> dict:
        self._init()
        if not hasattr(self, "_profiles"):
            for key, value in changes.items():
                if key in ("effect", "ambilight") and isinstance(value, dict):
                    self._settings[key] = {**self._settings.get(key, {}), **value}
                elif key in PROFILE_KEYS:
                    self._settings[key] = value
            self._save_and_apply()
            return {}
        updated = self._profiles.patch(scope, app_key, changes)
        if self._edited_scope_is_effective(scope, app_key):
            self._sync_effective_profile()
            self._apply()
        self._persist_settings()
        if scope == "global" and self._current_app_key is None:
            self._maybe_persist_startup()
        return self._profile_state(scope, app_key, updated)

    async def set_profile_follow_global(self, app_key, follow: bool) -> dict:
        self._init()
        key = str(app_key)
        self._profiles.set_follow_global(key, follow)
        if self._current_app_key == key:
            self._sync_effective_profile()
            self._apply()
        return self._profile_state("game", key)

    async def forget_profile(self, app_key) -> dict:
        self._init()
        key = str(app_key)
        self._profiles.forget(key)
        if self._current_app_key == key:
            self._sync_effective_profile()
            self._apply()
        return self._profile_state("game", key)

    async def set_current_app(self, app_key=None) -> dict:
        self._init()
        key = None if app_key is None or not str(app_key).strip() else str(app_key)
        if key == self._current_app_key:
            return self._profile_state("game" if key else "global", key)
        self._current_app_key = key
        self._ambilight.stop()
        self._audio.stop()
        self._engine.stop()
        self._sync_effective_profile()
        self._apply()
        return self._profile_state("game" if key else "global", key)

    async def get_state(self) -> dict:
        self._init()
        s = self._settings
        return {
            "device": self._device,
            "capabilities": self._merged_capabilities(),
            "power": s["power"],
            "brightness": s["brightness"],
            "mode": s["mode"],
            "color": _rgb(s["color"]),
            "gradient": [_rgb(c) for c in s["gradient"]],
            "gradientSpeed": s.get("gradient_speed", DEFAULTS["gradient_speed"]),
            "effect": {
                "id": s["effect"]["id"],
                "speed": s["effect"]["speed"],
                "useGradient": s["effect"].get("use_gradient", False),
            },
            "ambilight": s["ambilight"],
            "savedGradients": self._serialized_saved(),
            "powerLedOff": s.get("power_led_off", False),
            "chargerOnly": s.get("charger_only", False),
            "forceControl": s.get("force_control", False),
            "batteryBreathe": s.get("battery_breathe", True),
            "batteryLevel": getattr(self, "_battery_level", 100),
            "temperatureBreathe": s.get("temperature_breathe", True),
            "temperature": _temperature_reading(getattr(self, "_apu_temp", None)),
            "sensorBands": {
                sensor: _serialize_sensor_bands(s["sensor_bands"][sensor])
                for sensor in SENSOR_BAND_DEFAULTS
            },
            "rememberStartup": s.get("remember_startup", True),
            "profileContext": self._profile_state(
                "game" if self._current_app_key else "global",
                self._current_app_key,
            ),
        }

    async def set_power(self, on: bool) -> None:
        self._init()
        self._settings["power"] = on
        self._save_and_apply()

    async def set_charger_only(self, on: bool) -> None:
        self._init()
        self._settings["charger_only"] = on
        if on:
            self._ac_online = charger_online()
        self._save_and_apply()

    async def set_force_control(self, on: bool) -> None:
        self._init()
        if self._stopping:
            decky.logger.warning("Colores: force-control change ignored during shutdown")
            return
        self._settings["force_control"] = on
        self._persist_settings()
        if on:
            claim = await self._claim_hhd_rgb()
            if claim == "stopping" or self._stopping:
                return
            self._controller.invalidate()
        else:
            await self._restore_hhd_rgb()
            if self._stopping:
                return
        self._apply()

    def _uses_hhd_rgb_takeover(self) -> bool:
        return bool(self._capabilities.get("hhdRgbTakeover"))

    def _set_hhd_status(self, status, log, message):
        if self._hhd_rgb_status != status:
            log(message)
        self._hhd_rgb_status = status

    async def _run_hhd_call(self, call):
        future = asyncio.get_running_loop().run_in_executor(None, call)
        try:
            return await asyncio.shield(future)
        except asyncio.CancelledError:
            await future
            raise
        except Exception as error:
            decky.logger.warning(
                "Colores: HHD RGB operation failed: %s", type(error).__name__
            )
            return None

    async def _claim_hhd_rgb(self) -> str:
        if not self._uses_hhd_rgb_takeover():
            return "not_applicable"
        async with self._hhd_rgb_lock:
            if self._stopping:
                return "stopping"
            current = await self._run_hhd_call(self._hhd_rgb.read_rgb)
            if current is None:
                self._set_hhd_status("unavailable", decky.logger.warning, "Colores: HHD RGB state unavailable; continuing Apex reclaim")
                return "failed"
            if current is False:
                self._set_hhd_status("disabled", decky.logger.info, "Colores: HHD RGB already disabled")
                return "unchanged"
            if self._settings.get("hhd_rgb_restore") is not True:
                self._settings["hhd_rgb_restore"] = True
                self._persist_settings()
            confirmed = await self._run_hhd_call(lambda: self._hhd_rgb.set_rgb(False))
            if confirmed is True:
                self._hhd_rgb_status = "disabled"
                decky.logger.info("Colores: HHD RGB disabled and confirmed for Apex takeover")
                return "changed"
            self._set_hhd_status("disable_failed", decky.logger.warning, "Colores: HHD RGB disable was not confirmed; restore marker retained")
            return "failed"

    async def _restore_hhd_rgb(self) -> bool:
        if not self._uses_hhd_rgb_takeover():
            return True
        async with self._hhd_rgb_lock:
            if self._settings.get("hhd_rgb_restore") is not True:
                return True
            confirmed = await self._run_hhd_call(lambda: self._hhd_rgb.set_rgb(True))
            if confirmed is True:
                self._settings["hhd_rgb_restore"] = None
                self._persist_settings()
                self._hhd_rgb_status = "restored"
                decky.logger.info("Colores: HHD RGB ownership restored and confirmed")
                return True
            decky.logger.warning("Colores: HHD RGB restore not confirmed; retry marker retained")
            return False

    async def set_battery_breathe(self, on: bool) -> None:
        await self.patch_profile("global", None, {"battery_breathe": on})

    async def set_temperature_breathe(self, on: bool) -> None:
        await self.patch_profile("global", None, {"temperature_breathe": on})

    async def set_sensor_bands(self, sensor: str, bands) -> list:
        self._init()
        parsed = _parse_sensor_bands(sensor, bands)
        candidate = {
            **self._settings,
            "sensor_bands": {**self._settings["sensor_bands"], sensor: parsed},
        }
        self._store.save(candidate)
        self._settings = candidate
        self._apply()
        return _serialize_sensor_bands(parsed)

    async def get_temperature(self):
        self._init()
        return _temperature_reading(getattr(self, "_apu_temp", None))

    async def get_performance(self):
        self._init()
        value = gpu_busy_percent()
        if value is None:
            value = getattr(self, "_perf_value", None)
        return value

    async def set_remember_startup(self, on: bool) -> None:
        self._init()
        self._settings["remember_startup"] = on
        controller = self._controller
        if on:
            self._maybe_persist_startup()
        elif hasattr(controller, "restore_startup"):
            controller.restore_startup(self._settings.get("startup_factory"))
        self._persist_settings()

    async def set_brightness(self, value: int) -> None:
        await self.patch_profile("global", None, {"brightness": value})

    async def set_mode(self, mode: str) -> None:
        await self.patch_profile("global", None, {"mode": mode})

    async def set_solid(self, r: int, g: int, b: int) -> None:
        await self.patch_profile("global", None, {"color": [r, g, b]})

    async def set_gradient(self, stops: list) -> None:
        await self.patch_profile(
            "global", None, {"gradient": [list(stop) for stop in stops]}
        )

    async def set_gradient_speed(self, speed: int) -> None:
        await self.patch_profile("global", None, {"gradient_speed": speed})

    async def set_effect(self, effect_id: str, speed: int, use_gradient: bool) -> None:
        await self.patch_profile(
            "global",
            None,
            {"effect": {"id": effect_id, "speed": speed, "use_gradient": use_gradient}},
        )

    async def save_gradient(self, name: str, stops: list) -> list:
        self._init()
        self._settings["saved_gradients"] = upsert_gradient(
            self._settings["saved_gradients"], name, stops
        )
        self._persist_settings()
        return self._serialized_saved()

    async def delete_gradient(self, name: str) -> list:
        self._init()
        self._settings["saved_gradients"] = remove_gradient(
            self._settings["saved_gradients"], name
        )
        self._persist_settings()
        return self._serialized_saved()

    async def reconnect(self) -> bool:
        self._init()
        if self._stopping:
            return False
        if self._settings.get("force_control"):
            claim = await self._claim_hhd_rgb()
            if claim == "stopping" or self._stopping:
                return False
        self._reprobe_device()
        ok = self._controller.reconnect()
        if hasattr(self, "_profiles"):
            self._sync_effective_profile()
        if self._settings["mode"] == "ambient":
            self._ambilight.stop()
        self._apply()
        return bool(ok)

    async def get_ambilight_status(self) -> str:
        self._init()
        return self._ambilight.status

    async def get_audio_status(self) -> str:
        self._init()
        return self._audio.status

    async def set_ambilight(self, vividness: int, smoothing: int, fps: int) -> None:
        self._init()
        source = (
            self._profiles.editable("global", None)["ambilight"]
            if hasattr(self, "_profiles")
            else self._settings["ambilight"]
        )
        ambilight = dict(source)
        ambilight.update(
            vividness=max(0, min(100, int(vividness))),
            smoothing=smoothing,
            fps=fps,
        )
        ambilight.pop("saturation", None)
        await self.patch_profile("global", None, {"ambilight": ambilight})

    async def set_ambilight_sampling(self, mode: str) -> None:
        await self.patch_profile("global", None, {"ambilight": {"sampling": mode}})

    async def set_power_led(self, off: bool) -> None:
        self._init()
        self._settings["power_led_off"] = off
        self._persist_settings()
        if self._power_led and self._capabilities.get("powerLed"):
            if not self._power_led.set(off):
                decky.logger.warning("Colores: power LED write failed (off=%s)", off)

    async def set_experiment(self, feature: str, on: bool) -> None:
        self._init()
        enabled = set(self._settings.get("enabled_experiments", []))
        if on:
            enabled.add(feature)
        else:
            enabled.discard(feature)
        self._settings["enabled_experiments"] = sorted(enabled)
        self._persist_settings()

    def _effective_power(self) -> bool:
        if not self._settings["power"]:
            return False
        if self._settings.get("charger_only", False):
            return bool(getattr(self, "_ac_online", True))
        return True

    def _battery_state(self) -> dict:
        return {
            "level": getattr(self, "_battery_level", 100),
            "charging": bool(getattr(self, "_ac_online", True)),
            "breathe": self._settings.get("battery_breathe", True),
            "bands": self._settings.get("sensor_bands", {}).get(
                "battery", BATTERY_BANDS
            ),
        }

    def _temperature_state(self) -> dict:
        return {
            "temp": _temperature_reading(getattr(self, "_apu_temp", None)),
            "breathe": self._settings.get("temperature_breathe", True),
            "bands": self._settings.get("sensor_bands", {}).get(
                "temperature", TEMPERATURE_BANDS
            ),
        }

    def _performance_state(self) -> dict:
        value = gpu_busy_percent()
        if value is None:
            value = self._cpu_sampler.percent()
        if value is not None:
            self._perf_value = value
        return {"value": getattr(self, "_perf_value", None)}

    def _clock_state(self) -> dict:
        lt = time.localtime()
        return {"hour": lt.tm_hour + lt.tm_min / 60.0}

    def _maybe_persist_startup(self) -> None:
        if not self._settings.get("remember_startup"):
            return
        if (
            hasattr(self, "_profiles")
            and self._current_app_key is not None
            and not self._profiles.is_following_global(self._current_app_key)
        ):
            return
        if self._settings.get("mode") not in ("solid", "gradient"):
            return
        if not hasattr(self._controller, "save_startup"):
            return
        task = getattr(self, "_startup_task", None)
        if task is not None and not task.done():
            task.cancel()
        self._startup_task = asyncio.create_task(self._persist_startup_after_delay())

    async def _persist_startup_after_delay(self) -> None:
        try:
            await asyncio.sleep(STARTUP_PERSIST_DELAY)
            self._controller.save_startup()
        except asyncio.CancelledError:
            raise
        except Exception as error:
            decky.logger.warning("Colores: startup persist failed: %s", error)

    def _render(self, zone_colors) -> None:
        self._controller.apply_zones(
            zone_colors, self._settings["brightness"], self._effective_power()
        )

    def _save_and_apply(self) -> None:
        self._persist_settings()
        self._apply()

    def _wants_render_loop(self) -> bool:
        s = self._settings
        if s["mode"] in ("ambient", "battery", "temperature", "performance", "clock", "vu"):
            return True
        if s["mode"] == "gradient":
            return not self._controller.supports_per_zone()
        if s["mode"] == "effect":
            effect = s["effect"]
            if effect.get("use_gradient", False):
                return True
            if effect["id"] == "spiral":
                return not self._controller.supports_hardware_effects()
            if effect["id"] == "wave":
                return self._controller.supports_per_zone() or bool(
                    self._capabilities.get("perControllerColor", False)
                )
            return False
        return False

    def _apply(self) -> None:
        if self._controller.supports_hardware_effects() and not self._wants_render_loop():
            self._apply_hardware()
            return
        self._apply_per_zone()

    def _apply_hardware(self) -> None:
        s = self._settings
        self._ambilight.stop()
        self._audio.stop()
        self._engine.stop()
        brightness = s["brightness"]
        power = self._effective_power()
        if not power:
            self._controller.apply_solid((0, 0, 0), 0, False)
            return
        if s["mode"] == "effect":
            effect = s["effect"]
            self._controller.apply_hardware_effect(
                effect["id"], tuple(s["color"]), effect["speed"], power
            )
        elif s["mode"] == "gradient":
            self._controller.apply_zones(
                interpolate_gradient([tuple(c) for c in s["gradient"]], self._zones), brightness, power
            )
        else:
            self._controller.apply_solid(tuple(s["color"]), brightness, power)

    def _apply_per_zone(self) -> None:
        s = self._settings
        if not self._effective_power():
            self._ambilight.stop()
            self._audio.stop()
            self._engine.set_static([(0, 0, 0)] * self._zones)
            return

        if s["mode"] == "ambient":
            self._audio.stop()
            self._engine.stop()
            amb = s["ambilight"]
            self._ambilight.start(
                {
                    "saturation": 1.0 + (amb["vividness"] / 100) * 1.5,
                    "smoothing": amb["smoothing"],
                    "fps": amb.get("fps", 10),
                    "sampling": amb.get("sampling", "columns"),
                    "global_color": not (
                        self._capabilities.get("perZone")
                        or self._capabilities.get("perControllerColor")
                    ),
                    "fallback": tuple(s["color"]),
                }
            )
            return

        if s["mode"] == "vu":
            self._ambilight.stop()
            self._engine.stop()
            self._audio.start()
            return

        self._ambilight.stop()
        self._audio.stop()

        if s["mode"] == "battery":
            self._engine.start_battery(self._battery_state)
        elif s["mode"] == "temperature":
            self._engine.start_temperature(self._temperature_state)
        elif s["mode"] == "performance":
            self._engine.start_performance(self._performance_state)
        elif s["mode"] == "clock":
            self._engine.start_clock(self._clock_state)
        elif s["mode"] == "effect":
            effect = s["effect"]
            self._engine.start_effect(
                effect["id"],
                effect["speed"],
                {
                    "color": tuple(s["color"]),
                    "stops": [tuple(c) for c in s["gradient"]],
                    "use_gradient": effect.get("use_gradient", False),
                },
            )
        elif s["mode"] == "gradient":
            stops = [tuple(c) for c in s["gradient"]]
            if self._controller.supports_per_zone():
                self._engine.set_static(interpolate_gradient(stops, self._zones))
            else:
                self._engine.start_effect(
                    "gradient_sweep", s["gradient_speed"], {"stops": stops}
                )
        else:
            self._engine.set_static([tuple(s["color"])] * self._zones)

    async def _main(self):
        self._init()
        if self._settings.get("force_control"):
            await self._claim_hhd_rgb()
            self._controller.invalidate()
        else:
            await self._restore_hhd_rgb()
        decky.logger.info(
            "Colores v%s on %s (product=%s board=%s euid=%s driver=%s route=%s color=%s zones=%s ambilight=%s available=%s ledPath=%s lastError=%s)",
            read_version(),
            self._device["name"],
            self._device.get("product"),
            self._device.get("board"),
            os.geteuid(),
            type(self._controller).__name__,
            getattr(self._controller, "route", None),
            self._capabilities["color"],
            self._capabilities["zones"],
            self._capabilities["ambilight"],
            self._controller.available,
            self._controller.led_path,
            self._controller.last_error,
        )
        self._apply()
        self._reassert_task = asyncio.create_task(self._acquire_and_reassert())
        self._charger_task = asyncio.create_task(self._charger_watch())
        self._resume_task = asyncio.create_task(self._resume_watch())
        if self._capabilities.get("conflictsWithSystemRgb"):
            self._force_control_task = asyncio.create_task(self._force_control_watch())

    async def _charger_watch(self) -> None:
        try:
            while True:
                await asyncio.sleep(CHARGER_POLL_INTERVAL)
                level = battery_level()
                if level is not None:
                    self._battery_level = level
                temp = apu_temperature()
                if temp is not None:
                    self._apu_temp = temp
                online = charger_online()
                if online != getattr(self, "_ac_online", True):
                    self._ac_online = online
                    if self._settings.get("charger_only", False):
                        self._apply()
        except asyncio.CancelledError:
            raise
        except Exception as error:
            decky.logger.warning("Colores: charger watch failed: %s", error)

    async def _resume_watch(self) -> None:
        previous = _suspend_clock()
        if previous is None:
            return
        try:
            while True:
                await asyncio.sleep(RESUME_POLL_INTERVAL)
                current = _suspend_clock()
                if current is None:
                    return
                suspended_for = current - previous
                previous = current
                if suspended_for < RESUME_SUSPEND_THRESHOLD:
                    continue
                await asyncio.sleep(RESUME_REAPPLY_DELAY)
                if await self._restore_after_resume():
                    decky.logger.info(
                        "Colores: restored lighting after %.1fs suspend", suspended_for
                    )
                else:
                    decky.logger.warning(
                        "Colores: could not restore lighting after %.1fs suspend",
                        suspended_for,
                    )
        except asyncio.CancelledError:
            raise
        except Exception as error:
            decky.logger.warning("Colores: resume watch failed: %s", error)

    async def _restore_after_resume(self) -> bool:
        for attempt in range(RESUME_RECONNECT_ATTEMPTS):
            try:
                if await self.reconnect():
                    return True
            except asyncio.CancelledError:
                raise
            except Exception as error:
                decky.logger.warning("Colores: resume reconnect failed: %s", error)
            if attempt + 1 < RESUME_RECONNECT_ATTEMPTS:
                await asyncio.sleep(RESUME_RECONNECT_INTERVAL)
        return False

    async def _force_control_watch(self) -> None:
        try:
            while True:
                await asyncio.sleep(FORCE_CONTROL_INTERVAL)
                if not self._settings.get("force_control"):
                    continue
                if self._uses_hhd_rgb_takeover():
                    claim = await self._claim_hhd_rgb()
                    if claim == "changed":
                        self._controller.invalidate()
                if not self._wants_render_loop():
                    self._apply()
        except asyncio.CancelledError:
            raise
        except Exception as error:
            decky.logger.warning("Colores: force-control watch failed: %s", error)

    async def _stop_background_tasks(self):
        async with self._hhd_rgb_lock:
            self._stopping = True
        tasks = []
        for attr in (
            "_reassert_task",
            "_charger_task",
            "_resume_task",
            "_force_control_task",
            "_startup_task",
        ):
            task = getattr(self, attr, None)
            if task:
                task.cancel()
                tasks.append(task)
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)

    async def _unload(self):
        await self._stop_background_tasks()
        if getattr(self, "_ready", False):
            await self._restore_hhd_rgb()
        if getattr(self, "_ambilight", None):
            self._ambilight.stop()
        if getattr(self, "_engine", None):
            self._engine.stop()
        decky.logger.info("Colores unloaded")

    async def _uninstall(self):
        await self._stop_background_tasks()
        if getattr(self, "_ready", False):
            await self._restore_hhd_rgb()
        decky.logger.info("Colores uninstalled")
