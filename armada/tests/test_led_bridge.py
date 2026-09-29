from __future__ import annotations

import importlib.util
import io
import json
import os
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock


MODULE_PATH = (
    Path(__file__).parents[1]
    / "package"
    / "contents"
    / "code"
    / "armada-ledctl.py"
)
SPEC = importlib.util.spec_from_file_location("armada_led_bridge", MODULE_PATH)
assert SPEC and SPEC.loader
ledctl = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ledctl)


class LedBridgeTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.environment = mock.patch.dict(
            os.environ, {"ARMADA_LED_SYSFS_ROOT": str(self.root)}, clear=False
        )
        self.environment.start()
        for name in ledctl.LED_NAMES:
            directory = self.root / "sys" / "class" / "leds" / name
            directory.mkdir(parents=True)
            (directory / "brightness").write_text("204", encoding="ascii")
            (directory / "max_brightness").write_text("255", encoding="ascii")

    def tearDown(self) -> None:
        self.environment.stop()
        self.temporary.cleanup()

    def test_status_reports_hardware_and_controller(self) -> None:
        with mock.patch.object(ledctl, "_controller_error", return_value=None):
            result = ledctl.status()
        self.assertTrue(result["available"])
        self.assertTrue(result["controller_available"])
        self.assertEqual(result["power"], "on")
        self.assertEqual(set(result["zones"]), set(ledctl.LED_NAMES))

    def test_status_reports_partial(self) -> None:
        path = self.root / "sys" / "class" / "leds" / "left-side" / "brightness"
        path.write_text("0", encoding="ascii")
        with mock.patch.object(ledctl, "_controller_error", return_value=None):
            self.assertEqual(ledctl.status()["power"], "partial")

    def test_status_disables_control_when_colores_is_missing(self) -> None:
        with mock.patch.object(
            ledctl, "_controller_error", return_value="controller-unavailable"
        ):
            result = ledctl.status()
        self.assertTrue(result["available"])
        self.assertFalse(result["controller_available"])
        self.assertEqual(result["error"], "controller-unavailable")

    def test_missing_zone_is_an_error(self) -> None:
        missing = self.root / "sys" / "class" / "leds" / "right-side" / "brightness"
        missing.unlink()
        with self.assertRaises(ledctl.LedControlError):
            ledctl.status()

    def test_set_power_calls_colores_without_writing_sysfs(self) -> None:
        before = {
            name: (
                self.root / "sys" / "class" / "leds" / name / "brightness"
            ).read_text(encoding="ascii")
            for name in ledctl.LED_NAMES
        }
        with (
            mock.patch.object(ledctl, "_decky_call") as decky_call,
            mock.patch.object(ledctl, "_controller_error", return_value=None),
        ):
            result = ledctl.set_power("off")
        decky_call.assert_called_once_with("set_power", False)
        after = {
            name: (
                self.root / "sys" / "class" / "leds" / name / "brightness"
            ).read_text(encoding="ascii")
            for name in ledctl.LED_NAMES
        }
        self.assertEqual(after, before)
        self.assertTrue(result["controller_available"])

    def test_cli_returns_json_error_when_decky_fails(self) -> None:
        output = io.StringIO()
        with (
            mock.patch.object(
                ledctl, "_decky_call", side_effect=ledctl.DeckyError("sin Colores")
            ),
            redirect_stdout(output),
        ):
            exit_code = ledctl.main(["set", "on"])
        payload = json.loads(output.getvalue())
        self.assertEqual(exit_code, 1)
        self.assertTrue(payload["available"])
        self.assertFalse(payload["controller_available"])
        self.assertEqual(payload["error"], "controller-unavailable")

    def test_receive_unmasked_text_frame(self) -> None:
        payload = b'{"type":1,"id":1}'
        stream = io.BytesIO(bytes((0x81, len(payload))) + payload)
        opcode, received = ledctl._receive_frame(stream)
        self.assertEqual(opcode, 0x1)
        self.assertEqual(received, payload)

    def test_receive_rejects_oversized_frame(self) -> None:
        header = bytes((0x81, 127)) + (ledctl.MAX_FRAME_SIZE + 1).to_bytes(8, "big")
        with self.assertRaises(ledctl.DeckyError):
            ledctl._receive_frame(io.BytesIO(header))

    def test_decky_call_uses_unique_id_and_cancels_subscription(self) -> None:
        response = json.dumps(
            {"type": 1, "id": 42, "result": {"power": True}}
        ).encode("utf-8")
        response_frame = bytes((0x81, len(response))) + response

        class FakeConnection:
            def __init__(self) -> None:
                self.frames: list[bytes] = []

            def __enter__(self):
                return self

            def __exit__(self, *_args) -> None:
                return None

            def settimeout(self, _timeout: int) -> None:
                return None

            def sendall(self, frame: bytes) -> None:
                self.frames.append(frame)

        def decode_client_frame(frame: bytes) -> dict:
            length = frame[1] & 0x7F
            self.assertLess(length, 126)
            mask = frame[2:6]
            payload = bytes(
                value ^ mask[index % 4]
                for index, value in enumerate(frame[6 : 6 + length])
            )
            return json.loads(payload)

        connection = FakeConnection()
        with (
            mock.patch.object(ledctl, "_decky_token", return_value="token"),
            mock.patch.object(ledctl.secrets, "randbelow", return_value=41),
            mock.patch.object(
                ledctl.socket, "create_connection", return_value=connection
            ),
            mock.patch.object(
                ledctl,
                "_authenticate_websocket",
                return_value=io.BytesIO(response_frame),
            ),
        ):
            result = ledctl._decky_call("get_state")

        self.assertEqual(result, {"power": True})
        self.assertEqual(len(connection.frames), 2)
        request, cancellation = map(decode_client_frame, connection.frames)
        self.assertEqual(request["id"], 42)
        self.assertEqual(request["args"], ["Colores", "get_state"])
        self.assertEqual(cancellation, {"type": 3, "id": 42})


if __name__ == "__main__":
    unittest.main()
