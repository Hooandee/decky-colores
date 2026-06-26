import os

import decky

from version import read_version
from device import detect_device, detect_capabilities
from settings_store import SettingsStore
from led_controller import LedController

DEFAULTS = {
    "power": True,
    "brightness": 80,
    "color": [255, 255, 255],
}


class Plugin:
    def _init(self) -> None:
        if getattr(self, "_ready", False):
            return
        self._device = detect_device()
        self._capabilities = detect_capabilities()
        self._store = SettingsStore(
            os.path.join(decky.DECKY_PLUGIN_SETTINGS_DIR, "state.json")
        )
        self._settings = self._store.load(DEFAULTS)
        self._controller = LedController(
            self._capabilities.get("ledPath"),
            self._capabilities.get("zones", 1),
            self._capabilities.get("maxBrightness", 255),
        )
        self._ready = True

    async def get_version(self) -> str:
        return read_version()

    async def get_state(self) -> dict:
        self._init()
        return {
            "device": self._device,
            "capabilities": self._capabilities,
            "power": self._settings["power"],
            "brightness": self._settings["brightness"],
            "color": {
                "r": self._settings["color"][0],
                "g": self._settings["color"][1],
                "b": self._settings["color"][2],
            },
        }

    async def set_color(self, r: int, g: int, b: int) -> None:
        self._init()
        self._settings["color"] = [r, g, b]
        self._persist_and_apply()

    async def set_brightness(self, value: int) -> None:
        self._init()
        self._settings["brightness"] = value
        self._persist_and_apply()

    async def set_power(self, on: bool) -> None:
        self._init()
        self._settings["power"] = on
        self._persist_and_apply()

    def _apply(self) -> None:
        ok = self._controller.apply(
            self._settings["color"],
            self._settings["brightness"],
            self._settings["power"],
        )
        if ok:
            decky.logger.info(
                "applied color=%s brightness=%s power=%s",
                self._settings["color"],
                self._settings["brightness"],
                self._settings["power"],
            )
        else:
            decky.logger.warning(
                "apply failed (euid=%s ledPath=%s): %s",
                os.geteuid(),
                self._capabilities.get("ledPath"),
                self._controller.last_error,
            )

    def _persist_and_apply(self) -> None:
        self._store.save(self._settings)
        self._apply()

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
        decky.logger.info("Colores unloaded")

    async def _uninstall(self):
        decky.logger.info("Colores uninstalled")
