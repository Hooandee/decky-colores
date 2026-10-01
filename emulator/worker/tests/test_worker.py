import importlib.util
from pathlib import Path
import tempfile
import unittest


MODULE_PATH = Path(__file__).parents[1] / "worker.py"
SPEC = importlib.util.spec_from_file_location("colores_emulator_worker", MODULE_PATH)
worker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(worker)


class VirtualSysfsTests(unittest.TestCase):
    def test_multicolor_state(self):
        profile = {"backend": {"type": "multicolor", "targets": ["rgb:test"]}}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            worker.create_virtual_sysfs(root, "Test", profile)
            node = root / "sys/class/leds/rgb:test"
            (node / "multi_intensity").write_text("255 0 0\n")
            (node / "brightness").write_text("255\n")
            self.assertEqual(worker.read_led_state(root, profile)["colors"], [[255, 0, 0]])

    def test_channel_zones_are_grouped(self):
        profile = {"backend": {"type": "channels", "targets": ["red=r0", "red=r1", "green=g0", "green=g1", "blue=b0", "blue=b1"]}}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            worker.create_virtual_sysfs(root, "Test", profile)
            for name in ("r0", "g1"):
                (root / "sys/class/leds" / name / "brightness").write_text("255\n")
            self.assertEqual(worker.read_led_state(root, profile)["colors"], [[255, 0, 0], [0, 255, 0]])


if __name__ == "__main__":
    unittest.main()
