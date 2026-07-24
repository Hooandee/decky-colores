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
from effects import EffectEngine, interpolate_gradient
from ambilight import Ambilight
from audio import AudioReactive
from power_supply import charger_online, battery_level
from thermal import apu_temperature
from performance import gpu_busy_percent, CpuSampler
from saved_gradients import upsert_gradient, remove_gradient
import self_updater
from report import collector as report_collector
from report import client as report_client

_REPORT_APP = "colores"
_REPORT_SERVICE_URL = os.environ.get(
    "COLORES_REPORT_URL", "https://bug-collector-khaki.vercel.app/api/report"
)

DEFAULTS = {
    "power": True,
    "brightness": 80,
    "mode": "solid",
    "color": [255, 255, 255],
    "gradient": [[0, 196, 255], [136, 86, 255]],
    "gradient_speed": 30,
    "effect": {"id": "breathing", "speed": 50, "use_gradient": False},
    "ambilight": {"saturation": 140, "smoothing": 75, "fps": 10, "sampling": "columns"},
    "saved_gradients": [],
    "enabled_experiments": [],
    "power_led_off": False,
    "charger_only": False,
    "force_control": False,
    "battery_breathe": True,
    "temperature_breathe": True,
    "remember_startup": True,
    "startup_factory": None,
}

# How often the background watcher samples the AC adapter to react to plug/unplug
# while the menu is closed. A couple of sysfs reads every few seconds is negligible.
CHARGER_POLL_INTERVAL = 3.0

# Seconds of quiet before committing a startup color to EC flash. The live color
# applies immediately (volatile register); only this persisted copy touches flash,
# so we debounce it — dragging the color wheel must not write flash on every frame.
STARTUP_PERSIST_DELAY = 1.0

# When "force control" is on, how often we re-assert our LED state to win it back
# from another RGB tool (e.g. HHD) that keeps reapplying its own colors. A blind
# re-write, not an ownership check. Only runs on devices that actually conflict.
FORCE_CONTROL_INTERVAL = 2.0

# Cold-boot LED acquisition: the Ally's RGB node hangs off a USB HID device that can
# enumerate late, so /sys/class/leds/...:rgb may not exist yet when the plugin loads.
# Poll for it for a bounded window, then re-assert once more to beat any default the
# firmware/another plugin writes shortly after load. Module-level so tests can shrink them.
ACQUIRE_ATTEMPTS = 20
ACQUIRE_INTERVAL = 1.0
REASSERT_DELAY = 5.0


def _rgb(values):
    return {"r": values[0], "g": values[1], "b": values[2]}


def _saved(entry):
    return {"name": entry["name"], "stops": [_rgb(c) for c in entry["stops"]]}


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
        self._setup_device(self._build_context())
        self._store = SettingsStore(
            os.path.join(decky.DECKY_PLUGIN_SETTINGS_DIR, "state.json")
        )
        self._settings = self._store.load(DEFAULTS)
        self._settings["ambilight"] = {**DEFAULTS["ambilight"], **self._settings["ambilight"]}
        self._settings["effect"] = {**DEFAULTS["effect"], **self._settings["effect"]}
        self._ac_online = charger_online()
        level = battery_level()
        self._battery_level = 100 if level is None else level
        self._apu_temp = apu_temperature()
        self._apply_power_led()
        self._capture_startup_factory()
        self._ready = True

    def _capture_startup_factory(self) -> None:
        # Snapshot the untouched boot color ONCE, before we ever persist a custom one,
        # so turning "remember at startup" off can hand the bar back to SteamOS.
        if self._settings.get("startup_factory") is not None:
            return
        controller = self._controller
        if hasattr(controller, "read_startup"):
            factory = controller.read_startup()
            if factory:
                self._settings["startup_factory"] = factory
                self._store.save(self._settings)

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
        # Recover a controller that wasn't present at load (late USB HID enumeration on
        # cold boot). No-op once we already have a working LED — this guard is what keeps
        # a healthy machine completely untouched (never rebuilds the engine mid-effect).
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
            # H1: wait for the LED node to appear, applying as soon as it does.
            for _ in range(ACQUIRE_ATTEMPTS):
                if self._controller.available:
                    break
                await asyncio.sleep(ACQUIRE_INTERVAL)
                if self._reprobe_device():
                    self._apply()
            # H2: a static write can be overwritten by a late firmware/other-plugin default.
            # Re-assert once. Skip it when a render loop is active (effect/ambilight): that
            # loop already rewrites ~30fps and wins on its own, so reapplying would only
            # restart the effect at frame 0 — needless disruption of something that works.
            await asyncio.sleep(REASSERT_DELAY)
            self._reprobe_device()
            if not self._wants_render_loop():
                self._apply()
        except Exception as error:  # never let the background task break plugin load
            decky.logger.warning("Colores: acquire/reassert failed: %s", error)

    def _apply_power_led(self) -> None:
        # Reassert ONLY the "off" state on load. When the feature is off we leave the
        # EC untouched and never load ec_sys, so a Legion that never enables the toggle
        # is never tainted (matches PowerLedController's lazy-load contract). Turning
        # the LED back on is handled by the explicit set_power_led path.
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
        # Fire-and-forget: restarts Decky to load the just-installed files.
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
        return {"settings": settings}

    def _serialized_saved(self) -> list:
        return [_saved(g) for g in self._settings["saved_gradients"]]

    def _merged_capabilities(self) -> dict:
        caps = dict(self._capabilities)
        states = dict(caps.get("states", {}))
        enabled = set(self._settings.get("enabled_experiments", []))
        for feature, state in states.items():
            if state == "experimental":
                caps[feature] = feature in enabled
            elif state == "supported":
                caps[feature] = True
            else:
                caps[feature] = False
        caps["enabledExperiments"] = sorted(enabled)
        return caps

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
            "temperature": getattr(self, "_apu_temp", None),
            "rememberStartup": s.get("remember_startup", True),
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
        self._settings["force_control"] = on
        self._store.save(self._settings)
        if on:
            self._controller.invalidate()  # force a full re-init so it reclaims at once
        self._apply()

    async def set_battery_breathe(self, on: bool) -> None:
        self._init()
        self._settings["battery_breathe"] = on
        # The battery loop reads this live via _battery_state, so persisting is
        # enough; no restart needed. Re-apply is a cheap no-op unless idle.
        self._save_and_apply()

    async def set_temperature_breathe(self, on: bool) -> None:
        self._init()
        self._settings["temperature_breathe"] = on
        self._save_and_apply()

    async def get_temperature(self):
        self._init()
        return getattr(self, "_apu_temp", None)

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
        self._store.save(self._settings)

    async def set_brightness(self, value: int) -> None:
        self._init()
        self._settings["brightness"] = value
        self._save_and_apply()

    async def set_mode(self, mode: str) -> None:
        self._init()
        self._settings["mode"] = mode
        self._save_and_apply()
        self._maybe_persist_startup()

    async def set_solid(self, r: int, g: int, b: int) -> None:
        self._init()
        self._settings["color"] = [r, g, b]
        self._save_and_apply()
        self._maybe_persist_startup()

    async def set_gradient(self, stops: list) -> None:
        self._init()
        self._settings["gradient"] = [list(stop) for stop in stops]
        self._save_and_apply()
        self._maybe_persist_startup()

    async def set_gradient_speed(self, speed: int) -> None:
        self._init()
        self._settings["gradient_speed"] = speed
        self._save_and_apply()

    async def set_effect(self, effect_id: str, speed: int, use_gradient: bool) -> None:
        self._init()
        self._settings["effect"] = {"id": effect_id, "speed": speed, "use_gradient": use_gradient}
        self._save_and_apply()

    async def save_gradient(self, name: str, stops: list) -> list:
        self._init()
        self._settings["saved_gradients"] = upsert_gradient(
            self._settings["saved_gradients"], name, stops
        )
        self._store.save(self._settings)
        return self._serialized_saved()

    async def delete_gradient(self, name: str) -> list:
        self._init()
        self._settings["saved_gradients"] = remove_gradient(
            self._settings["saved_gradients"], name
        )
        self._store.save(self._settings)
        return self._serialized_saved()

    async def reconnect(self) -> bool:
        self._init()
        self._reprobe_device()
        ok = self._controller.reconnect()
        self._apply()
        return bool(ok)

    async def get_ambilight_status(self) -> str:
        self._init()
        return self._ambilight.status

    async def get_audio_status(self) -> str:
        self._init()
        return self._audio.status

    async def set_ambilight(self, saturation: int, smoothing: int, fps: int) -> None:
        self._init()
        self._settings["ambilight"] = {
            **self._settings.get("ambilight", {}),
            "saturation": saturation,
            "smoothing": smoothing,
            "fps": fps,
        }
        self._save_and_apply()

    async def set_ambilight_sampling(self, mode: str) -> None:
        self._init()
        self._settings["ambilight"] = {**self._settings.get("ambilight", {}), "sampling": mode}
        self._save_and_apply()

    async def set_power_led(self, off: bool) -> None:
        self._init()
        self._settings["power_led_off"] = off
        self._store.save(self._settings)
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
        self._store.save(self._settings)

    def _effective_power(self) -> bool:
        # The manual power switch is the master. "Charger only" is a modifier on top:
        # when on and running on battery, the LEDs are gated off WITHOUT touching the
        # user's mode/effect/color — flipping back on at the wall restores it exactly.
        if not self._settings["power"]:
            return False
        if self._settings.get("charger_only", False):
            return bool(getattr(self, "_ac_online", True))
        return True

    def _battery_state(self) -> dict:
        # Live state for the battery render loop. "charging" uses the cached adapter
        # state (plugged) as the proxy; the loop only breathes when plugged AND below
        # 100%. Level is refreshed on the coarse charger poll — battery moves slowly.
        return {
            "level": getattr(self, "_battery_level", 100),
            "charging": bool(getattr(self, "_ac_online", True)),
            "breathe": self._settings.get("battery_breathe", True),
        }

    def _temperature_state(self) -> dict:
        return {
            "temp": getattr(self, "_apu_temp", None),
            "breathe": self._settings.get("temperature_breathe", True),
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
        # Persist ONLY on static-color modes, and DEBOUNCED: dragging the color wheel
        # fires set_solid every ~60ms, but the flash write waits for the interaction to
        # settle (STARTUP_PERSIST_DELAY), so one drag commits to flash once, not ~15x/s.
        if not self._settings.get("remember_startup"):
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
        self._store.save(self._settings)
        self._apply()

    def _wants_render_loop(self) -> bool:
        # Modes driven by our per-frame render loop (software effect engine or
        # ambilight capture) instead of firmware. Ambient capture always runs in
        # software. Gradient mode runs in software on devices that cannot render a
        # spatial gradient (single-color zones, e.g. Legion rings) — there we
        # animate an elegant crossfade through the palette instead.
        # For effects: the custom-gradient overlay must be painted per-frame, and
        # so must wave on devices that can show more than one color at once
        # (per-zone or per-controller). Single-color devices render wave with
        # their native hardware effect instead of collapsing it to a flat color.
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
                # Legion Go renders spiral with its own firmware effect; only
                # software-painting devices (e.g. the Ally) run the per-frame loop.
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
                    "saturation": amb["saturation"] / 100.0,
                    "smoothing": amb["smoothing"],
                    "fps": amb.get("fps", 10),
                    "sampling": amb.get("sampling", "columns"),
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
                # single-color zones can't show a spatial gradient: animate an
                # elegant crossfade through the whole palette instead
                self._engine.start_effect(
                    "gradient_sweep", s["gradient_speed"], {"stops": stops}
                )
        else:
            self._engine.set_static([tuple(s["color"])] * self._zones)

    async def _main(self):
        self._init()
        decky.logger.info(
            "Colores v%s on %s (euid=%s color=%s zones=%s ambilight=%s available=%s ledPath=%s lastError=%s)",
            read_version(),
            self._device["name"],
            os.geteuid(),
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
        if self._capabilities.get("conflictsWithSystemRgb"):
            self._force_control_task = asyncio.create_task(self._force_control_watch())

    async def _charger_watch(self) -> None:
        # React to plug/unplug live, even with the menu closed. Reapply ONLY on the
        # edge (state actually changed), never every tick — so a running effect is
        # not restarted at frame 0. The "charger only" gate is read inside
        # _effective_power, so this stays a cheap no-op when the feature is off.
        try:
            while True:
                await asyncio.sleep(CHARGER_POLL_INTERVAL)
                # Refresh the cached battery level; the battery render loop reads it
                # live, so no reapply is needed for it to ease to a new band.
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
        except Exception as error:  # a sysfs hiccup must never kill the plugin
            decky.logger.warning("Colores: charger watch failed: %s", error)

    async def _force_control_watch(self) -> None:
        # Another RGB tool (e.g. HHD) keeps reapplying its own colors on its events, so
        # a one-shot reclaim doesn't hold. Re-assert our state on a coarse tick to win it
        # back within a couple seconds, even with the menu closed. This is a blind
        # re-write (no read-back, no ownership check). It is a GENTLE re-assert: it does
        # NOT invalidate()/re-init, so apply_zones takes its fast path (per-zone color
        # only, no Aura init handshake or APPLY latch) — re-writing the same color that
        # way is visually seamless, where a full re-init every tick makes the LEDs blink.
        # The strong, latched reclaim (invalidate) runs on real reclaim moments instead:
        # toggling the switch on, panel-open (reconnect), and resume. Skipped while a
        # render loop (effect/ambilight) is active: that already rewrites ~30fps, and
        # re-applying would restart it at frame 0. Only created on conflicting devices.
        try:
            while True:
                await asyncio.sleep(FORCE_CONTROL_INTERVAL)
                if self._settings.get("force_control") and not self._wants_render_loop():
                    self._apply()
        except asyncio.CancelledError:
            raise
        except Exception as error:  # never let the background task break plugin load
            decky.logger.warning("Colores: force-control watch failed: %s", error)

    async def _unload(self):
        for attr in ("_reassert_task", "_charger_task", "_force_control_task", "_startup_task"):
            task = getattr(self, attr, None)
            if task:
                task.cancel()
        if getattr(self, "_ambilight", None):
            self._ambilight.stop()
        if getattr(self, "_engine", None):
            self._engine.stop()
        decky.logger.info("Colores unloaded")

    async def _uninstall(self):
        decky.logger.info("Colores uninstalled")
