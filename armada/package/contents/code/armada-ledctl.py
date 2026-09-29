#!/usr/bin/python3
"""Unprivileged bridge between the Armada OS plasmoid and Decky Colores."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import secrets
import socket
import struct
import sys
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, BinaryIO


LED_NAMES = (
    "left-side",
    "left-joystick",
    "right-side",
    "right-joystick",
)
DECKY_HOST = "127.0.0.1"
DECKY_PORT = 1337
DECKY_TIMEOUT = 3
MAX_FRAME_SIZE = 1024 * 1024
WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"


class LedControlError(RuntimeError):
    """An expected hardware or controller error."""


class DeckyError(RuntimeError):
    """Decky or Colores could not service a request."""


def _sysfs_root() -> Path:
    return Path(os.environ.get("ARMADA_LED_SYSFS_ROOT", "/"))


def _led_path(name: str, attribute: str) -> Path:
    return _sysfs_root() / "sys" / "class" / "leds" / name / attribute


def _read_int(path: Path) -> int:
    try:
        value = int(path.read_text(encoding="ascii").strip())
    except FileNotFoundError as error:
        raise LedControlError(f"No se encontró {path}") from error
    except PermissionError as error:
        raise LedControlError(f"Sin permiso para leer {path}") from error
    except (OSError, ValueError) as error:
        raise LedControlError(f"Valor inválido en {path}") from error
    if value < 0:
        raise LedControlError(f"Valor negativo en {path}")
    return value


def _read_hardware() -> dict[str, dict[str, int]]:
    readings: dict[str, dict[str, int]] = {}
    for name in LED_NAMES:
        brightness = _read_int(_led_path(name, "brightness"))
        maximum = _read_int(_led_path(name, "max_brightness"))
        if maximum < 1 or brightness > maximum:
            raise LedControlError(f"Rango de brillo inválido para {name}")
        readings[name] = {"brightness": brightness, "max_brightness": maximum}
    return readings


def _power_state(readings: dict[str, dict[str, int]]) -> str:
    enabled = [entry["brightness"] > 0 for entry in readings.values()]
    if all(enabled):
        return "on"
    if any(enabled):
        return "partial"
    return "off"


def _read_exact(stream: BinaryIO, length: int) -> bytes:
    chunks: list[bytes] = []
    remaining = length
    while remaining:
        chunk = stream.read(remaining)
        if not chunk:
            raise DeckyError("Decky cerró la conexión")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def _send_frame(connection: socket.socket, opcode: int, payload: bytes) -> None:
    if len(payload) > MAX_FRAME_SIZE:
        raise DeckyError("La solicitud a Decky es demasiado grande")
    mask = os.urandom(4)
    length = len(payload)
    if length < 126:
        header = bytes((0x80 | opcode, 0x80 | length))
    elif length <= 0xFFFF:
        header = bytes((0x80 | opcode, 0x80 | 126)) + struct.pack("!H", length)
    else:
        header = bytes((0x80 | opcode, 0x80 | 127)) + struct.pack("!Q", length)
    masked = bytes(value ^ mask[index % 4] for index, value in enumerate(payload))
    connection.sendall(header + mask + masked)


def _receive_frame(stream: BinaryIO) -> tuple[int, bytes]:
    first, second = _read_exact(stream, 2)
    if not first & 0x80:
        raise DeckyError("Decky envió una respuesta fragmentada")
    opcode = first & 0x0F
    masked = bool(second & 0x80)
    length = second & 0x7F
    if length == 126:
        length = struct.unpack("!H", _read_exact(stream, 2))[0]
    elif length == 127:
        length = struct.unpack("!Q", _read_exact(stream, 8))[0]
    if length > MAX_FRAME_SIZE:
        raise DeckyError("La respuesta de Decky es demasiado grande")
    mask = _read_exact(stream, 4) if masked else b""
    payload = _read_exact(stream, length)
    if masked:
        payload = bytes(value ^ mask[index % 4] for index, value in enumerate(payload))
    return opcode, payload


def _authenticate_websocket(connection: socket.socket, token: str) -> BinaryIO:
    key = base64.b64encode(os.urandom(16)).decode("ascii")
    path = "/ws?auth=" + urllib.parse.quote(token, safe="")
    request = (
        f"GET {path} HTTP/1.1\r\n"
        f"Host: {DECKY_HOST}:{DECKY_PORT}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n\r\n"
    )
    connection.sendall(request.encode("ascii"))
    stream = connection.makefile("rb")
    status_line = stream.readline(4097).decode("iso-8859-1").strip()
    if not status_line.startswith("HTTP/1.1 101"):
        raise DeckyError("Decky rechazó la conexión")
    headers: dict[str, str] = {}
    while True:
        line = stream.readline(4097)
        if not line or line in (b"\r\n", b"\n"):
            break
        name, separator, value = line.decode("iso-8859-1").partition(":")
        if not separator:
            raise DeckyError("Decky envió cabeceras inválidas")
        headers[name.strip().lower()] = value.strip()
    expected = base64.b64encode(
        hashlib.sha1((key + WEBSOCKET_GUID).encode("ascii")).digest()
    ).decode("ascii")
    if headers.get("sec-websocket-accept") != expected:
        raise DeckyError("Decky no validó la conexión")
    return stream


def _decky_token() -> str:
    try:
        with urllib.request.urlopen(
            f"http://{DECKY_HOST}:{DECKY_PORT}/auth/token", timeout=DECKY_TIMEOUT
        ) as response:
            token = response.read(4097).decode("ascii").strip()
    except (OSError, UnicodeError) as error:
        raise DeckyError("Decky no está disponible") from error
    if not token:
        raise DeckyError("Decky no devolvió un token válido")
    return token


def _decky_call(method: str, *arguments: Any) -> Any:
    request_id = secrets.randbelow(2**31 - 1) + 1
    payload = json.dumps(
        {
            "type": 0,
            "route": "loader/call_plugin_method",
            "args": ["Colores", method, *arguments],
            "id": request_id,
        },
        separators=(",", ":"),
    ).encode("utf-8")
    try:
        token = _decky_token()
        with socket.create_connection(
            (DECKY_HOST, DECKY_PORT), timeout=DECKY_TIMEOUT
        ) as connection:
            connection.settimeout(DECKY_TIMEOUT)
            stream = _authenticate_websocket(connection, token)
            request_sent = False
            try:
                _send_frame(connection, 0x1, payload)
                request_sent = True
                while True:
                    opcode, response_payload = _receive_frame(stream)
                    if opcode == 0x8:
                        raise DeckyError("Decky cerró la conexión")
                    if opcode == 0x9:
                        _send_frame(connection, 0xA, response_payload)
                        continue
                    if opcode != 0x1:
                        continue
                    message = json.loads(response_payload.decode("utf-8"))
                    if message.get("id") != request_id:
                        continue
                    if message.get("type") == 1:
                        return message.get("result")
                    if message.get("type") == -1:
                        raise DeckyError("Colores rechazó la solicitud")
            finally:
                if request_sent:
                    cancellation = json.dumps(
                        {"type": 3, "id": request_id}, separators=(",", ":")
                    ).encode("utf-8")
                    try:
                        _send_frame(connection, 0x1, cancellation)
                    except (DeckyError, OSError):
                        pass
                stream.close()
    except DeckyError:
        raise
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as error:
        raise DeckyError("No se pudo comunicar con Colores") from error


def _controller_error() -> str | None:
    try:
        _decky_call("get_version")
    except DeckyError:
        return "controller-unavailable"
    return None


def status() -> dict[str, Any]:
    readings = _read_hardware()
    controller_error = _controller_error()
    return {
        "available": True,
        "controller_available": controller_error is None,
        "power": _power_state(readings),
        "zones": readings,
        "error": controller_error,
    }


def set_power(power: str) -> dict[str, Any]:
    _read_hardware()
    _decky_call("set_power", power == "on")
    return status()


def _error_response(error: Exception) -> dict[str, Any]:
    try:
        readings = _read_hardware()
    except LedControlError:
        readings = {}
    return {
        "available": bool(readings),
        "controller_available": False,
        "power": _power_state(readings) if readings else "unknown",
        "zones": readings,
        "error": (
            "hardware-unavailable"
            if isinstance(error, LedControlError)
            else "controller-unavailable"
        ),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Control RGB mediante Decky Colores")
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("status", help="Muestra el estado en JSON")
    set_parser = subparsers.add_parser("set", help="Enciende o apaga las luces")
    set_parser.add_argument("power", choices=("on", "off"))
    arguments = parser.parse_args(argv)

    try:
        result = status() if arguments.command == "status" else set_power(arguments.power)
        exit_code = 0
    except (DeckyError, LedControlError) as error:
        result = _error_response(error)
        exit_code = 1
    print(json.dumps(result, separators=(",", ":"), sort_keys=True))
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
