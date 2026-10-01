#!/usr/bin/env python3
"""Run an extracted Colores backend against a synthetic Armada RGB device."""

from __future__ import annotations

import asyncio
import base64
import importlib.util
import json
import logging
import math
import os
from pathlib import Path
import pwd
import shutil
import sys
import tempfile
import threading
import types


_OUTPUT_LOCK = threading.Lock()


def emit(message: dict) -> None:
    with _OUTPUT_LOCK:
        sys.stdout.write(json.dumps(message, separators=(",", ":")) + "\n")
        sys.stdout.flush()


def read_catalog(plugin_root: Path) -> dict:
    path = plugin_root / "py_modules" / "armada_rgb_profiles.json"
    with path.open(encoding="utf-8") as handle:
        catalog = json.load(handle)
    if catalog.get("version") != 1 or not isinstance(catalog.get("profiles"), list):
        raise ValueError("unsupported Armada RGB catalog")
    return catalog


def find_profile(catalog: dict, model: str) -> dict:
    for profile in catalog["profiles"]:
        if model in profile.get("models", []):
            return profile
    raise ValueError(f"model is not present in plugin catalog: {model}")


def create_virtual_sysfs(root: Path, model: str, profile: dict) -> None:
    model_path = root / "sys/firmware/devicetree/base/model"
    model_path.parent.mkdir(parents=True, exist_ok=True)
    model_path.write_text(model + "\0", encoding="utf-8")
    (root / "etc").mkdir(parents=True, exist_ok=True)
    (root / "etc/os-release").write_text('ID="armada"\nNAME="Armada OS"\n', encoding="utf-8")
    leds = root / "sys/class/leds"
    leds.mkdir(parents=True, exist_ok=True)
    backend = profile["backend"]
    if backend["type"] == "multicolor":
        names = backend["targets"]
    elif backend["type"] == "channels":
        names = [target.split("=", 1)[1] for target in backend["targets"]]
    else:
        raise ValueError(f"unsupported backend: {backend['type']}")
    for name in names:
        node = leds / name
        node.mkdir(parents=True, exist_ok=True)
        (node / "max_brightness").write_text("255\n", encoding="ascii")
        (node / "brightness").write_text("0\n", encoding="ascii")
        if backend["type"] == "multicolor":
            (node / "multi_index").write_text("red green blue\n", encoding="ascii")
            (node / "multi_intensity").write_text("0 0 0\n", encoding="ascii")


def _read_int(path: Path) -> int:
    try:
        return max(0, int(path.read_text(encoding="ascii").strip()))
    except (OSError, ValueError):
        return 0


def _srgb_from_linear(value: float) -> int:
    value = max(0.0, min(1.0, value))
    encoded = value * 12.92 if value <= 0.0031308 else 1.055 * math.pow(value, 1 / 2.4) - 0.055
    return round(encoded * 255)


def read_led_state(root: Path, profile: dict) -> dict:
    leds = root / "sys/class/leds"
    backend = profile["backend"]
    raw = []
    colors = []
    if backend["type"] == "multicolor":
        for name in backend["targets"]:
            node = leds / name
            maximum = max(1, _read_int(node / "max_brightness"))
            brightness = min(maximum, _read_int(node / "brightness"))
            try:
                order = (node / "multi_index").read_text(encoding="ascii").lower().split()
                values = [int(value) for value in (node / "multi_intensity").read_text(encoding="ascii").split()]
            except (OSError, ValueError):
                order, values = ["red", "green", "blue"], [0, 0, 0]
            channels = dict(zip(order, values))
            rgb = [max(0, min(maximum, channels.get(channel, 0))) for channel in ("red", "green", "blue")]
            raw.append({"target": name, "rgb": rgb, "brightness": brightness, "max": maximum})
            colors.append([_srgb_from_linear((value / maximum) * (brightness / maximum)) for value in rgb])
    else:
        grouped = {channel: [] for channel in ("red", "green", "blue")}
        for target in backend["targets"]:
            channel, name = target.split("=", 1)
            grouped[channel].append(name)
        for names in zip(grouped["red"], grouped["green"], grouped["blue"]):
            values = []
            targets = []
            for name in names:
                node = leds / name
                maximum = max(1, _read_int(node / "max_brightness"))
                value = min(maximum, _read_int(node / "brightness"))
                values.append(_srgb_from_linear(value / maximum))
                targets.append({"target": name, "value": value, "max": maximum})
            raw.append(targets)
            colors.append(values)
    return {"colors": colors, "raw": raw, "backend": backend["type"]}


class DummySuspendMonitor:
    def __init__(self, *_args, **_kwargs):
        pass

    def start(self):
        pass

    def diagnostics(self):
        return {"running": False, "connected": False, "emulated": True}

    async def stop_and_wait(self):
        pass


class DummyHhdRgbControl:
    def read_rgb(self):
        return False

    def set_rgb(self, enabled):
        return bool(enabled)


class Runtime:
    def __init__(self, plugin_root: Path, settings_dir: Path, model: str, restore_config: dict | None = None):
        self.plugin_root = plugin_root
        self.settings_dir = settings_dir
        self.model = model
        self.restore_config = restore_config if isinstance(restore_config, dict) else None
        self.catalog = read_catalog(plugin_root)
        self.profile = find_profile(self.catalog, model)
        self.virtual_root = Path(tempfile.mkdtemp(prefix="colores-virtual-sysfs-"))
        create_virtual_sysfs(self.virtual_root, model, self.profile)
        self.plugin = None
        self.main_module = None
        self._emit_handle = None

    def _install_decky_module(self) -> None:
        module = types.ModuleType("decky")
        user = pwd.getpwuid(os.getuid()).pw_name
        module.DECKY_USER = user
        module.DECKY_USER_HOME = str(Path.home())
        module.DECKY_PLUGIN_DIR = str(self.plugin_root)
        module.DECKY_PLUGIN_SETTINGS_DIR = str(self.settings_dir)
        module.DECKY_PLUGIN_LOG_DIR = str(self.settings_dir / "logs")
        module.DECKY_VERSION = "emulator"
        module.logger = logging.getLogger("colores.emulator.plugin")
        sys.modules["decky"] = module

    async def start(self) -> None:
        self.settings_dir.mkdir(parents=True, exist_ok=True)
        (self.settings_dir / "logs").mkdir(parents=True, exist_ok=True)
        self._install_decky_module()
        sys.path.insert(0, str(self.plugin_root / "py_modules"))
        sys.path.insert(0, str(self.plugin_root))
        spec = importlib.util.spec_from_file_location("colores_loaded_main", self.plugin_root / "main.py")
        if not spec or not spec.loader:
            raise RuntimeError("cannot load main.py")
        module = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = module
        spec.loader.exec_module(module)
        self.main_module = module

        import ambilight as ambilight_module
        import audio as audio_module
        import device

        original_build = device.build_device
        module.build_device = lambda ambilight=False: original_build(
            sysfs_root=str(self.virtual_root), ambilight=ambilight
        )
        module.capture_available = lambda: True
        module.SuspendMonitor = DummySuspendMonitor
        module.HhdRgbControl = DummyHhdRgbControl
        module.battery_level = lambda: 72
        module.charger_online = lambda: True
        module.apu_temperature = lambda: 54.0

        class InjectedAmbilight(ambilight_module.Ambilight):
            def __init__(self, *args, **kwargs):
                super().__init__(*args, **kwargs)
                self._emulator_running = False
                self._capture_width = 64
                self._capture_height = 36
                self._last_frame_at = 0.0

            @property
            def running(self):
                return self._emulator_running

            def start(self, options):
                self._options = options or {}
                self._emulator_running = True
                self.status = "no_source"

            def stop(self):
                self._emulator_running = False
                self.status = "idle"

            async def stop_and_wait(self):
                self.stop()

            def push(self, frame: bytes, width: int, height: int):
                if not self.running or width != self._capture_width or height != self._capture_height:
                    return
                now = asyncio.get_running_loop().time()
                interval = self._capture_interval()
                if now - self._last_frame_at < interval:
                    return
                self._last_frame_at = now
                self.status = "running"
                self._update_targets(frame)
                self._tick()

        class InjectedAudio(audio_module.AudioReactive):
            def __init__(self, *args, **kwargs):
                super().__init__(*args, **kwargs)
                self._emulator_running = False

            @property
            def running(self):
                return self._emulator_running

            def start(self, options=None):
                self._emulator_running = True
                self.status = "no_source"

            def stop(self):
                self._emulator_running = False
                self.status = "idle"
                self._level = 0.0

            def push(self, data: bytes):
                if not self.running:
                    return
                self.status = "running"
                level = self._ease(audio_module._level_from_pcm(data))
                self._apply(audio_module.frame_vu(level, self._zones))

        module.Ambilight = InjectedAmbilight
        module.AudioReactive = InjectedAudio
        self.plugin = module.Plugin()
        await self.plugin._main()
        controller = self.plugin._controller
        original_apply_zones = controller.apply_zones

        def observed_apply_zones(*args, **kwargs):
            result = original_apply_zones(*args, **kwargs)
            self.schedule_led_emit()
            return result

        controller.apply_zones = observed_apply_zones
        if self.restore_config is not None:
            await self.restore_configuration(self.restore_config)
        self.emit_led_state()
        state = await self.plugin.get_state()
        emit({"type": "ready", "state": state, "backendVersion": await self.plugin.get_version()})

    async def _restore_call(self, name: str, *args) -> None:
        method = getattr(self.plugin, name, None)
        if not callable(method):
            return
        try:
            await method(*args)
        except Exception as error:
            logging.warning("could not restore %s: %s", name, error)

    async def restore_configuration(self, config: dict) -> None:
        current = await self.plugin.get_state()
        capabilities = current.get("capabilities", {})

        for name, value in (
            ("set_charger_only", config.get("chargerOnly")),
            ("set_force_control", config.get("forceControl")),
            ("set_remember_startup", config.get("rememberStartup")),
        ):
            if isinstance(value, bool):
                await self._restore_call(name, value)

        sensor_bands = config.get("sensorBands")
        if not isinstance(sensor_bands, dict):
            sensor_bands = {}
        for sensor, bands in sensor_bands.items():
            if sensor in ("battery", "temperature") and isinstance(bands, list):
                await self._restore_call("set_sensor_bands", sensor, bands)

        if capabilities.get("powerLed"):
            power_led_off = config.get("powerLedOff")
            if isinstance(power_led_off, bool):
                await self._restore_call("set_power_led", power_led_off)
            if capabilities.get("powerLedSeparateStates"):
                for name, state_name in (("powerLedAwakeOff", "awake"), ("powerLedSuspendOff", "suspend")):
                    value = config.get(name)
                    if isinstance(value, bool):
                        await self._restore_call("set_power_led_state", state_name, value)
        if isinstance(config.get("sleepChargingIndicator"), bool) and capabilities.get("sleepChargingIndicator"):
            await self._restore_call("set_sleep_charging_indicator", config["sleepChargingIndicator"])

        desired_gradients = config.get("savedGradients")
        if not isinstance(desired_gradients, list):
            desired_gradients = []
        current_gradients = current.get("savedGradients")
        if not isinstance(current_gradients, list):
            current_gradients = []
        desired_names = {entry.get("name") for entry in desired_gradients if isinstance(entry, dict)}
        for entry in current_gradients:
            if entry.get("name") not in desired_names:
                await self._restore_call("delete_gradient", entry.get("name"))
        for entry in desired_gradients:
            if isinstance(entry, dict) and isinstance(entry.get("name"), str):
                stops = [self._rgb_array(stop) for stop in entry.get("stops", [])]
                await self._restore_call("save_gradient", entry["name"], stops)

        source_profile = config.get("profile") or {}
        profile = {}
        mapping = (
            ("brightness", "brightness"),
            ("mode", "mode"),
            ("gradientSpeed", "gradient_speed"),
            ("ambilight", "ambilight"),
            ("batteryBreathe", "battery_breathe"),
            ("temperatureBreathe", "temperature_breathe"),
        )
        for source, target in mapping:
            if source in source_profile:
                profile[target] = source_profile[source]
        if "color" in source_profile:
            profile["color"] = self._rgb_array(source_profile["color"])
        if "gradient" in source_profile:
            profile["gradient"] = [self._rgb_array(stop) for stop in source_profile["gradient"]]
        if isinstance(source_profile.get("effect"), dict):
            effect = source_profile["effect"]
            profile["effect"] = {
                "id": effect.get("id"),
                "speed": effect.get("speed"),
                "use_gradient": effect.get("useGradient", effect.get("use_gradient", False)),
            }

        mode = profile.get("mode")
        required_capability = {
            "gradient": "color", "effect": "effects", "ambient": "ambilight",
            "vu": "audioMode", "battery": "batteryMode", "temperature": "temperatureMode",
            "performance": "performanceMode", "clock": "clockMode",
        }.get(mode)
        if required_capability and not capabilities.get(required_capability):
            profile.pop("mode", None)
        if not capabilities.get("brightness"):
            profile.pop("brightness", None)
        if not capabilities.get("color"):
            for key in ("color", "gradient", "gradient_speed", "effect"):
                profile.pop(key, None)
        if not capabilities.get("ambilight"):
            profile.pop("ambilight", None)
        if profile:
            await self._restore_call("patch_profile", "global", None, profile)

        if isinstance(config.get("power"), bool):
            await self._restore_call("set_power", config["power"])

    @staticmethod
    def _rgb_array(color):
        if isinstance(color, dict):
            return [color.get("r", 0), color.get("g", 0), color.get("b", 0)]
        if isinstance(color, (list, tuple)):
            return list(color)
        return [0, 0, 0]

    def schedule_led_emit(self) -> None:
        try:
            loop = asyncio.get_running_loop()
        except RuntimeError:
            return
        if self._emit_handle is not None:
            self._emit_handle.cancel()
        self._emit_handle = loop.call_later(0.01, self.emit_led_state)

    def emit_led_state(self) -> None:
        self._emit_handle = None
        emit({"type": "led_state", **read_led_state(self.virtual_root, self.profile)})

    async def rpc(self, name: str, args: list):
        if name == "check_update":
            version = await self.plugin.get_version()
            return {"current": version, "latest": version, "has_update": False, "notes": "", "download_url": "", "error": "Updates are disabled in the emulator"}
        if name == "install_update":
            return {"ok": False, "needs_restart": False, "message": "Updates are disabled in the emulator"}
        if name == "restart_loader":
            return None
        if name == "submit_report":
            return {"ok": False, "error": "Reports are disabled in the emulator"}
        method = getattr(self.plugin, name, None)
        if method is None or name.startswith("_"):
            raise AttributeError(f"unknown plugin call: {name}")
        return await method(*args)

    def video_frame(self, encoded: str, width: int, height: int) -> None:
        self.plugin._ambilight.push(base64.b64decode(encoded), width, height)

    def audio_pcm(self, encoded: str) -> None:
        self.plugin._audio.push(base64.b64decode(encoded))
        self.schedule_led_emit()

    async def stop(self) -> None:
        try:
            if self.plugin is not None:
                await self.plugin._unload()
        finally:
            shutil.rmtree(self.virtual_root, ignore_errors=True)


async def read_message() -> dict | None:
    line = await asyncio.get_running_loop().run_in_executor(None, sys.stdin.readline)
    if not line:
        return None
    return json.loads(line)


async def run() -> None:
    logging.basicConfig(level=logging.INFO, stream=sys.stderr)
    first = await read_message()
    if not first or first.get("type") != "init":
        raise RuntimeError("first worker message must be init")
    runtime = Runtime(
        Path(first["pluginRoot"]),
        Path(first["settingsDir"]),
        first["model"],
        first.get("restoreConfig"),
    )
    try:
        await runtime.start()
    except Exception as error:
        logging.exception("plugin startup failed")
        emit({"type": "fatal", "error": str(error)})
        await runtime.stop()
        return
    while True:
        message = await read_message()
        if message is None or message.get("type") == "unload":
            break
        request_id = message.get("id")
        try:
            kind = message.get("type")
            if kind == "rpc":
                result = await runtime.rpc(message["name"], message.get("args", []))
            elif kind == "video_frame":
                runtime.video_frame(message["data"], int(message["width"]), int(message["height"]))
                result = None
            elif kind == "audio_pcm":
                runtime.audio_pcm(message["pcm"])
                result = None
            else:
                raise ValueError(f"unknown message type: {kind}")
            if request_id:
                emit({"id": request_id, "ok": True, "result": result})
        except Exception as error:
            logging.exception("worker request failed")
            if request_id:
                emit({"id": request_id, "ok": False, "error": str(error)})
    await runtime.stop()


if __name__ == "__main__":
    try:
        asyncio.run(run())
    except KeyboardInterrupt:
        pass
