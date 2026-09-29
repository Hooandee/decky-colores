from __future__ import annotations

import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).parents[1]


class WidgetContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.qml = (ROOT / "package" / "contents" / "ui" / "main.qml").read_text(encoding="utf-8")
        cls.metadata = json.loads((ROOT / "package" / "metadata.json").read_text(encoding="utf-8"))

    def test_identity_is_armada_os(self) -> None:
        plugin = self.metadata["KPlugin"]
        self.assertEqual(plugin["Id"], "com.armada.armadaos")
        self.assertEqual(plugin["Name"], "Armada OS")
        self.assertNotIn("Odin", json.dumps(self.metadata))

    def test_led_icon_behavior_is_preserved(self) -> None:
        self.assertIn('return "dialog-warning"', self.qml)
        self.assertIn('return "preferences-desktop-color"', self.qml)
        self.assertIn('return "process-stop"', self.qml)
        self.assertIn("source: root.ledStateIcon", self.qml)

    def test_leds_precede_separator_and_profiles(self) -> None:
        led = self.qml.index('text: i18n("RGB lights")')
        separator = self.qml.index("Kirigami.Separator")
        eco = self.qml.index('text: "Eco"', separator)
        self.assertLess(led, separator)
        self.assertLess(separator, eco)
        self.assertNotIn('text: "Perfil de rendimiento"', self.qml)

    def test_visible_ui_uses_native_plasma_i18n(self) -> None:
        self.assertIn('i18n("RGB lights")', self.qml)
        self.assertIn('i18n("Active: %1", root.profileLabel)', self.qml)
        self.assertIn('i18n("Decky or Colores is unavailable")', self.qml)
        self.assertNotIn("Consultando", self.qml)
        self.assertNotIn("No se pudo", self.qml)

    def test_metadata_is_english(self) -> None:
        self.assertEqual(
            self.metadata["KPlugin"]["Description"],
            "Controls RGB lighting and performance profiles on Armada OS",
        )

    def test_every_supported_catalog_covers_the_qml_messages(self) -> None:
        messages = set(re.findall(r'i18n\("([^"\\]*(?:\\.[^"\\]*)*)"', self.qml))
        self.assertTrue(messages)
        for locale in ("es", "de", "it", "pt_BR"):
            catalog = (ROOT / "translate" / f"{locale}.po").read_text(encoding="utf-8")
            translated = set(re.findall(r'^msgid "(.+)"$', catalog, re.MULTILINE))
            self.assertEqual(messages, translated, locale)

    def test_both_backends_are_connected(self) -> None:
        self.assertIn('Qt.resolvedUrl("../code/armada-ledctl.py")', self.qml)
        self.assertIn('readonly property string profileHelper: "/usr/bin/armada-power"', self.qml)
        self.assertIn("com.steampowered.SteamOSManager1", self.qml)

    def test_widget_never_requests_privileges(self) -> None:
        self.assertNotIn("sudo", self.qml)
        self.assertNotIn("/var/usrlocal", self.qml)
        self.assertIn("root.ledState.controller_available", self.qml)


if __name__ == "__main__":
    unittest.main()
