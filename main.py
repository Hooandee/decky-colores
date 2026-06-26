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
    async def get_version(self) -> str:
        return read_version()

    async def get_state(self) -> dict:
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
        self._settings["color"] = [r, g, b]
        self._persist_and_apply()

    async def set_brightness(self, value: int) -> None:
        self._settings["brightness"] = value
        self._persist_and_apply()

    async def set_power(self, on: bool) -> None:
        self._settings["power"] = on
        self._persist_and_apply()

    def _apply(self) -> None:
        self._controller.apply(
            self._settings["color"],
            self._settings["brightness"],
            self._settings["power"],
        )

    def _persist_and_apply(self) -> None:
        self._store.save(self._settings)
        self._apply()

    async def _main(self):
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
        decky.logger.info(
            "Colores v%s on %s (color=%s zones=%s)",
            read_version(),
            self._device["name"],
            self._capabilities["color"],
            self._capabilities["zones"],
        )
        self._apply()

    async def _unload(self):
        decky.logger.info("Colores unloaded")

    async def _uninstall(self):
        decky.logger.info("Colores uninstalled")
