import os
import pwd
import shutil

import decky

from version import read_version
from device import build_device
from settings_store import SettingsStore
from effects import EffectEngine, interpolate_gradient
from ambilight import Ambilight
from saved_gradients import upsert_gradient, remove_gradient

DEFAULTS = {
    "power": True,
    "brightness": 80,
    "mode": "solid",
    "color": [255, 255, 255],
    "gradient": [[0, 196, 255], [136, 86, 255]],
    "effect": {"id": "breathing", "speed": 50, "use_gradient": False},
    "ambilight": {"saturation": 140, "smoothing": 75, "fps": 10},
    "saved_gradients": [],
    "enabled_experiments": [],
}


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
        ambilight_available = shutil.which("gst-launch-1.0") is not None
        ctx = build_device(ambilight=ambilight_available)
        self._device = ctx["info"]
        self._capabilities = ctx["capabilities"]
        self._zones = self._capabilities.get("zones", 1) or 1
        self._controller = ctx["device"]
        self._store = SettingsStore(
            os.path.join(decky.DECKY_PLUGIN_SETTINGS_DIR, "state.json")
        )
        self._settings = self._store.load(DEFAULTS)
        self._settings["ambilight"] = {**DEFAULTS["ambilight"], **self._settings["ambilight"]}
        self._settings["effect"] = {**DEFAULTS["effect"], **self._settings["effect"]}
        self._engine = EffectEngine(self._render, self._zones)
        runtime_dir, uid, gid = _user_creds()
        self._ambilight = Ambilight(
            self._render,
            self._zones,
            runtime_dir,
            uid,
            gid,
            layout=self._capabilities.get("layout"),
        )
        self._ready = True

    async def get_version(self) -> str:
        return read_version()

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
            "effect": {
                "id": s["effect"]["id"],
                "speed": s["effect"]["speed"],
                "useGradient": s["effect"].get("use_gradient", False),
            },
            "ambilight": s["ambilight"],
            "savedGradients": self._serialized_saved(),
        }

    async def set_power(self, on: bool) -> None:
        self._init()
        self._settings["power"] = on
        self._save_and_apply()

    async def set_brightness(self, value: int) -> None:
        self._init()
        self._settings["brightness"] = value
        self._save_and_apply()

    async def set_mode(self, mode: str) -> None:
        self._init()
        self._settings["mode"] = mode
        self._save_and_apply()

    async def set_solid(self, r: int, g: int, b: int) -> None:
        self._init()
        self._settings["color"] = [r, g, b]
        self._save_and_apply()

    async def set_gradient(self, stops: list) -> None:
        self._init()
        self._settings["gradient"] = [list(stop) for stop in stops]
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

    async def get_ambilight_status(self) -> str:
        self._init()
        return self._ambilight.status

    async def set_ambilight(self, saturation: int, smoothing: int, fps: int) -> None:
        self._init()
        self._settings["ambilight"] = {"saturation": saturation, "smoothing": smoothing, "fps": fps}
        self._save_and_apply()

    async def set_experiment(self, feature: str, on: bool) -> None:
        self._init()
        enabled = set(self._settings.get("enabled_experiments", []))
        if on:
            enabled.add(feature)
        else:
            enabled.discard(feature)
        self._settings["enabled_experiments"] = sorted(enabled)
        self._store.save(self._settings)

    def _render(self, zone_colors) -> None:
        self._controller.apply_zones(
            zone_colors, self._settings["brightness"], self._settings["power"]
        )

    def _save_and_apply(self) -> None:
        self._store.save(self._settings)
        self._apply()

    def _apply(self) -> None:
        if self._controller.supports_hardware_effects():
            self._apply_hardware()
            return
        self._apply_per_zone()

    def _apply_hardware(self) -> None:
        s = self._settings
        self._ambilight.stop()
        self._engine.stop()
        brightness = s["brightness"]
        power = s["power"]
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
                interpolate_gradient([tuple(c) for c in s["gradient"]], 2), brightness, power
            )
        else:
            self._controller.apply_solid(tuple(s["color"]), brightness, power)

    def _apply_per_zone(self) -> None:
        s = self._settings
        if not s["power"]:
            self._ambilight.stop()
            self._engine.set_static([(0, 0, 0)] * self._zones)
            return

        if s["mode"] == "ambient":
            self._engine.stop()
            amb = s["ambilight"]
            self._ambilight.start(
                {
                    "saturation": amb["saturation"] / 100.0,
                    "smoothing": amb["smoothing"],
                    "fps": amb.get("fps", 10),
                }
            )
            return

        self._ambilight.stop()

        if s["mode"] == "effect":
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
            self._engine.set_static(
                interpolate_gradient([tuple(c) for c in s["gradient"]], self._zones)
            )
        else:
            self._engine.set_static([tuple(s["color"])] * self._zones)

    async def _main(self):
        self._init()
        decky.logger.info(
            "Colores v%s on %s (euid=%s color=%s zones=%s ambilight=%s ledPath=%s)",
            read_version(),
            self._device["name"],
            os.geteuid(),
            self._capabilities["color"],
            self._capabilities["zones"],
            self._capabilities["ambilight"],
            self._capabilities.get("ledPath"),
        )
        self._apply()

    async def _unload(self):
        if getattr(self, "_ambilight", None):
            self._ambilight.stop()
        if getattr(self, "_engine", None):
            self._engine.stop()
        decky.logger.info("Colores unloaded")

    async def _uninstall(self):
        decky.logger.info("Colores uninstalled")
