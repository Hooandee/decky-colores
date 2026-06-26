import os

import decky

from version import read_version
from device import detect_device, detect_capabilities
from settings_store import SettingsStore
from led_controller import LedController
from effects import EffectEngine, interpolate_gradient

DEFAULTS = {
    "power": True,
    "brightness": 80,
    "mode": "solid",
    "color": [255, 255, 255],
    "gradient": [[0, 196, 255], [136, 86, 255]],
    "effect": {"id": "breathing", "speed": 50},
}


def _rgb(values):
    return {"r": values[0], "g": values[1], "b": values[2]}


class Plugin:
    def _init(self) -> None:
        if getattr(self, "_ready", False):
            return
        self._device = detect_device()
        self._capabilities = detect_capabilities()
        self._capabilities["effects"] = self._capabilities["color"]
        self._zones = self._capabilities.get("zones", 1) or 1
        self._store = SettingsStore(
            os.path.join(decky.DECKY_PLUGIN_SETTINGS_DIR, "state.json")
        )
        self._settings = self._store.load(DEFAULTS)
        self._controller = LedController(
            self._capabilities.get("ledPath"),
            self._zones,
            self._capabilities.get("maxBrightness", 255),
        )
        self._engine = EffectEngine(self._render, self._zones)
        self._running_sig = None
        self._ready = True

    async def get_version(self) -> str:
        return read_version()

    async def get_state(self) -> dict:
        self._init()
        s = self._settings
        return {
            "device": self._device,
            "capabilities": self._capabilities,
            "power": s["power"],
            "brightness": s["brightness"],
            "mode": s["mode"],
            "color": _rgb(s["color"]),
            "gradient": [_rgb(c) for c in s["gradient"]],
            "effect": s["effect"],
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

    async def set_effect(self, effect_id: str, speed: int) -> None:
        self._init()
        self._settings["effect"] = {"id": effect_id, "speed": speed}
        self._save_and_apply()

    def _render(self, zone_colors) -> None:
        self._controller.apply_zones(
            zone_colors, self._settings["brightness"], self._settings["power"]
        )

    def _save_and_apply(self) -> None:
        self._store.save(self._settings)
        self._apply()

    def _apply(self) -> None:
        s = self._settings
        if not s["power"]:
            self._engine.set_static([(0, 0, 0)] * self._zones)
            self._running_sig = None
            return

        mode = s["mode"]
        if mode == "effect":
            effect = s["effect"]
            color = tuple(s["color"])
            stops = [tuple(c) for c in s["gradient"]]
            sig = ("effect", effect["id"], effect["speed"], color, tuple(stops))
            if sig != self._running_sig:
                self._engine.start_effect(
                    effect["id"], effect["speed"], {"color": color, "stops": stops}
                )
                self._running_sig = sig
            return

        if mode == "gradient":
            stops = [tuple(c) for c in s["gradient"]]
            self._engine.set_static(interpolate_gradient(stops, self._zones))
        else:
            self._engine.set_static([tuple(s["color"])] * self._zones)
        self._running_sig = None

    async def _main(self):
        self._init()
        decky.logger.info(
            "Colores v%s on %s (euid=%s color=%s zones=%s ledPath=%s)",
            read_version(),
            self._device["name"],
            os.geteuid(),
            self._capabilities["color"],
            self._capabilities["zones"],
            self._capabilities.get("ledPath"),
        )
        self._apply()

    async def _unload(self):
        if getattr(self, "_engine", None):
            self._engine.stop()
        decky.logger.info("Colores unloaded")

    async def _uninstall(self):
        decky.logger.info("Colores uninstalled")
