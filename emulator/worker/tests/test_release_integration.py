import base64
import json
from pathlib import Path
import select
import subprocess
import sys
import tempfile
import time
import unittest
import zipfile


REPOSITORY = Path(__file__).parents[3]
WORKER = Path(__file__).parents[1] / "worker.py"
RELEASE = REPOSITORY / "Colores.zip"


@unittest.skipUnless(RELEASE.is_file(), "Colores.zip is not available")
class ReleaseIntegrationTests(unittest.TestCase):
    def _read_until(self, process, predicate, timeout=12):
        deadline = time.monotonic() + timeout
        messages = []
        while time.monotonic() < deadline:
            ready, _, _ = select.select([process.stdout], [], [], 0.25)
            if not ready:
                continue
            line = process.stdout.readline()
            if not line:
                break
            message = json.loads(line)
            messages.append(message)
            if predicate(message):
                return message, messages
        stderr = process.stderr.read(4000) if process.poll() is not None else ""
        self.fail(f"worker event timed out; messages={messages!r}; stderr={stderr}")

    def _launch_worker(self, plugin_root, settings_dir, model, restore_config=None):
        process = subprocess.Popen(
            [sys.executable, str(WORKER)],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )
        init = {
            "type": "init",
            "pluginRoot": str(plugin_root),
            "settingsDir": str(settings_dir),
            "model": model,
            "restoreConfig": restore_config,
        }
        process.stdin.write(json.dumps(init) + "\n")
        process.stdin.flush()
        ready, _ = self._read_until(process, lambda message: message.get("type") in {"ready", "fatal"})
        self.assertEqual(ready.get("type"), "ready", ready)
        return process, ready["state"]

    def _stop_worker(self, process):
        if process.poll() is None:
            process.stdin.write(json.dumps({"type": "unload"}) + "\n")
            process.stdin.flush()
            try:
                process.wait(timeout=4)
            except subprocess.TimeoutExpired:
                process.terminate()
                process.wait(timeout=3)
        process.stdin.close()
        process.stdout.close()
        process.stderr.close()

    @staticmethod
    def _transferable_config(state):
        return {
            "profile": state["profileContext"]["profile"],
            "power": state["power"],
            "chargerOnly": state["chargerOnly"],
            "forceControl": state["forceControl"],
            "powerLedOff": state["powerLedOff"],
            "powerLedAwakeOff": state["powerLedAwakeOff"],
            "powerLedSuspendOff": state["powerLedSuspendOff"],
            "sleepChargingIndicator": state["sleepChargingIndicator"],
            "savedGradients": state["savedGradients"],
            "sensorBands": state["sensorBands"],
            "rememberStartup": state["rememberStartup"],
        }

    def test_real_release_loads_and_writes_virtual_leds(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with zipfile.ZipFile(RELEASE) as archive:
                archive.extractall(root)
            plugin_root = root / "Colores"
            process = subprocess.Popen(
                [sys.executable, str(WORKER)],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1,
            )
            try:
                init = {"type": "init", "pluginRoot": str(plugin_root), "settingsDir": str(root / "settings"), "model": "AYN Odin 3"}
                process.stdin.write(json.dumps(init) + "\n")
                process.stdin.flush()
                ready, _ = self._read_until(process, lambda message: message.get("type") in {"ready", "fatal"})
                self.assertEqual(ready.get("type"), "ready", ready)
                self.assertEqual(ready["state"]["mode"], "solid")
                process.stdin.write(json.dumps({"id": 7, "type": "rpc", "name": "set_solid", "args": [255, 0, 0]}) + "\n")
                process.stdin.flush()
                response, _ = self._read_until(process, lambda message: message.get("id") == 7)
                self.assertTrue(response["ok"])
                state, _ = self._read_until(
                    process,
                    lambda message: message.get("type") == "led_state"
                    and any(any(channel for channel in color) for color in message.get("colors", [])),
                )
                self.assertEqual(len(state["colors"]), 8)
            finally:
                if process.poll() is None:
                    process.stdin.write(json.dumps({"type": "unload"}) + "\n")
                    process.stdin.flush()
                    try:
                        process.wait(timeout=4)
                    except subprocess.TimeoutExpired:
                        process.terminate()
                        process.wait(timeout=3)
                process.stdin.close()
                process.stdout.close()
                process.stderr.close()

    def test_experimental_flags_remain_saved_per_model_across_switches(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with zipfile.ZipFile(RELEASE) as archive:
                archive.extractall(root)
            plugin_root = root / "Colores"
            odin2_settings = root / "settings-odin2"
            odin3_settings = root / "settings-odin3"
            odin2, odin2_state = self._launch_worker(plugin_root, odin2_settings, "AYN Odin 2")
            try:
                odin2.stdin.write(json.dumps({
                    "id": 1, "type": "rpc", "name": "set_experiment", "args": ["ambilight", True]
                }) + "\n")
                odin2.stdin.flush()
                enabled, _ = self._read_until(odin2, lambda message: message.get("id") == 1)
                self.assertTrue(enabled["ok"])
                odin2.stdin.write(json.dumps({"id": 2, "type": "rpc", "name": "get_state", "args": []}) + "\n")
                odin2.stdin.flush()
                state_response, _ = self._read_until(odin2, lambda message: message.get("id") == 2)
                self.assertIn("ambilight", state_response["result"]["capabilities"]["enabledExperiments"])
                transfer_to_odin3 = self._transferable_config(state_response["result"])
            finally:
                self._stop_worker(odin2)

            odin3, odin3_state = self._launch_worker(
                plugin_root, odin3_settings, "AYN Odin 3", transfer_to_odin3
            )
            transfer_back_to_odin2 = self._transferable_config(odin3_state)
            self._stop_worker(odin3)

            odin2, returned_state = self._launch_worker(
                plugin_root, odin2_settings, "AYN Odin 2", transfer_back_to_odin2
            )
            try:
                self.assertIn("ambilight", returned_state["capabilities"]["enabledExperiments"])
                self.assertTrue(returned_state["capabilities"]["ambilight"])
            finally:
                self._stop_worker(odin2)

    def test_active_ambilight_configuration_is_restored_for_new_model(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with zipfile.ZipFile(RELEASE) as archive:
                archive.extractall(root)
            plugin_root = root / "Colores"
            settings_dir = root / "settings"
            settings_dir.mkdir()
            (settings_dir / "state.json").write_text(json.dumps({
                "enabled_experiments": ["ambilight", "brightness", "color", "effects"]
            }))
            process = subprocess.Popen(
                [sys.executable, str(WORKER)],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1,
            )
            try:
                init = {
                    "type": "init",
                    "pluginRoot": str(plugin_root),
                    "settingsDir": str(settings_dir),
                    "model": "AYN Odin 2",
                    "restoreConfig": {
                        "profile": {
                            "brightness": 43,
                            "mode": "ambient",
                            "color": {"r": 12, "g": 34, "b": 56},
                            "gradient": [{"r": 1, "g": 2, "b": 3}, {"r": 4, "g": 5, "b": 6}],
                            "gradientSpeed": 27,
                            "effect": {"id": "wave", "speed": 61, "useGradient": True},
                            "ambilight": {"vividness": 81, "smoothing": 23, "fps": 17, "sampling": "columns"},
                            "batteryBreathe": False,
                            "temperatureBreathe": False,
                        },
                        "power": True,
                        "chargerOnly": True,
                        "forceControl": False,
                        "rememberStartup": False,
                        "sensorBands": {
                            "battery": [
                                {"min": 100, "color": {"r": 0, "g": 120, "b": 255}},
                                {"min": 75, "color": {"r": 0, "g": 200, "b": 60}},
                                {"min": 50, "color": {"r": 255, "g": 200, "b": 0}},
                                {"min": 25, "color": {"r": 255, "g": 110, "b": 0}},
                                {"min": 0, "color": {"r": 255, "g": 30, "b": 20}},
                            ]
                        },
                        "savedGradients": [
                            {"name": "Transferred", "stops": [{"r": 255, "g": 0, "b": 0}, {"r": 0, "g": 0, "b": 255}]}
                        ],
                    },
                }
                process.stdin.write(json.dumps(init) + "\n")
                process.stdin.flush()
                ready, _ = self._read_until(process, lambda message: message.get("type") in {"ready", "fatal"})
                self.assertEqual(ready.get("type"), "ready", ready)
                state = ready["state"]
                self.assertEqual(state["mode"], "ambient", state)
                self.assertEqual(state["brightness"], 43)
                self.assertEqual(state["ambilight"], {"vividness": 81, "smoothing": 23, "fps": 17, "sampling": "columns"})
                self.assertTrue(state["chargerOnly"])
                self.assertFalse(state["rememberStartup"])
                self.assertEqual(state["sensorBands"]["battery"][2]["min"], 50)
                self.assertIn({"name": "Transferred", "stops": [{"r": 255, "g": 0, "b": 0}, {"r": 0, "g": 0, "b": 255}]}, state["savedGradients"])

                frame = base64.b64encode(bytes([0, 255, 0]) * (64 * 36)).decode("ascii")
                process.stdin.write(json.dumps({"type": "video_frame", "data": frame, "width": 64, "height": 36}) + "\n")
                process.stdin.flush()
                led_state, _ = self._read_until(
                    process,
                    lambda message: message.get("type") == "led_state"
                    and any(any(channel for channel in color) for color in message.get("colors", [])),
                )
                self.assertEqual(len(led_state["colors"]), 4)
                profiles = json.loads((root / "settings" / "profiles.json").read_text())
                saved_profile = profiles["global"]["profiles"]["default"]
                self.assertEqual(saved_profile["mode"], "ambient")
                self.assertEqual(saved_profile["brightness"], 43)
                self.assertEqual(saved_profile["ambilight"]["vividness"], 81)
            finally:
                if process.poll() is None:
                    process.stdin.write(json.dumps({"type": "unload"}) + "\n")
                    process.stdin.flush()
                    try:
                        process.wait(timeout=4)
                    except subprocess.TimeoutExpired:
                        process.terminate()
                        process.wait(timeout=3)
                process.stdin.close()
                process.stdout.close()
                process.stderr.close()

    def test_active_vu_configuration_is_restored_and_processes_audio(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with zipfile.ZipFile(RELEASE) as archive:
                archive.extractall(root)
            settings_dir = root / "settings"
            settings_dir.mkdir()
            (settings_dir / "state.json").write_text(json.dumps({
                "enabled_experiments": ["brightness"]
            }))
            process = subprocess.Popen(
                [sys.executable, str(WORKER)],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1,
            )
            try:
                init = {
                    "type": "init",
                    "pluginRoot": str(root / "Colores"),
                    "settingsDir": str(settings_dir),
                    "model": "AYN Odin 2",
                    "restoreConfig": {
                        "profile": {
                            "brightness": 62,
                            "mode": "vu",
                            "color": {"r": 30, "g": 80, "b": 240},
                            "gradient": [{"r": 255, "g": 0, "b": 0}, {"r": 0, "g": 0, "b": 255}],
                            "gradientSpeed": 30,
                            "effect": {"id": "breathing", "speed": 50, "useGradient": False},
                            "ambilight": {"vividness": 27, "smoothing": 75, "fps": 10, "sampling": "columns"},
                            "batteryBreathe": True,
                            "temperatureBreathe": True,
                        },
                        "power": True,
                    },
                }
                process.stdin.write(json.dumps(init) + "\n")
                process.stdin.flush()
                ready, _ = self._read_until(process, lambda message: message.get("type") in {"ready", "fatal"})
                self.assertEqual(ready.get("type"), "ready", ready)
                self.assertEqual(ready["state"]["mode"], "vu")
                self.assertEqual(ready["state"]["brightness"], 62)

                audio = base64.b64encode(b"\xff\x7f" * 1024).decode("ascii")
                process.stdin.write(json.dumps({"type": "audio_pcm", "pcm": audio}) + "\n")
                process.stdin.flush()
                led_state, _ = self._read_until(
                    process,
                    lambda message: message.get("type") == "led_state"
                    and any(any(channel for channel in color) for color in message.get("colors", [])),
                )
                self.assertEqual(len(led_state["colors"]), 4)
                profiles = json.loads((root / "settings" / "profiles.json").read_text())
                self.assertEqual(profiles["global"]["profiles"]["default"]["mode"], "vu")
            finally:
                if process.poll() is None:
                    process.stdin.write(json.dumps({"type": "unload"}) + "\n")
                    process.stdin.flush()
                    try:
                        process.wait(timeout=4)
                    except subprocess.TimeoutExpired:
                        process.terminate()
                        process.wait(timeout=3)
                process.stdin.close()
                process.stdout.close()
                process.stderr.close()


if __name__ == "__main__":
    unittest.main()
