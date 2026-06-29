import types

import power_led
from power_led import PowerLedController

LPBL = [{"offset": 0x10, "mask": 0x40}]
LEDPM = [{"offset": 0x52, "mask": 0x20}, {"offset": 0x58, "mask": 0x01}]


def _ec(tmp_path, overrides=None):
    data = bytearray(0x60)
    for off, val in (overrides or {}).items():
        data[off] = val
    path = tmp_path / "ec_io"
    path.write_bytes(bytes(data))
    return str(path)


def _byte(path, off):
    with open(path, "rb") as handle:
        return handle.read()[off]


def test_unavailable_without_config(tmp_path):
    assert PowerLedController([], ec_io=_ec(tmp_path)).available() is False


def test_available_when_ec_writable(tmp_path):
    assert PowerLedController(LPBL, ec_io=_ec(tmp_path)).available() is True


def test_set_off_sets_bit_preserving_others(tmp_path):
    path = _ec(tmp_path, {0x10: 0x05})
    ctrl = PowerLedController(LPBL, ec_io=path)
    assert ctrl.set(True) is True
    assert _byte(path, 0x10) == 0x45  # 0x05 | 0x40, other bits kept
    assert ctrl.get() is True


def test_set_on_clears_bit_preserving_others(tmp_path):
    path = _ec(tmp_path, {0x10: 0x45})
    ctrl = PowerLedController(LPBL, ec_io=path)
    assert ctrl.set(False) is True
    assert _byte(path, 0x10) == 0x05
    assert ctrl.get() is False


def test_multi_field_device(tmp_path):
    path = _ec(tmp_path)
    ctrl = PowerLedController(LEDPM, ec_io=path)
    assert ctrl.set(True) is True
    assert _byte(path, 0x52) == 0x20 and _byte(path, 0x58) == 0x01
    assert ctrl.get() is True
    assert ctrl.set(False) is True
    assert _byte(path, 0x52) == 0x00 and _byte(path, 0x58) == 0x00
    assert ctrl.get() is False


def test_short_ec_node_does_not_raise(tmp_path):
    # EC node shorter than the configured offset: read(1) returns b"" -> must not
    # raise (IndexError), so it never bricks plugin load. Degrades to None/False.
    path = tmp_path / "ec_io"
    path.write_bytes(bytes(4))  # only 4 bytes, offset 0x10 is past EOF
    ctrl = PowerLedController(LPBL, ec_io=str(path))
    assert ctrl.get() is None
    assert ctrl.set(True) is False


def test_missing_ec_is_safe(tmp_path, monkeypatch):
    # No EC node and the module cannot be loaded -> degrade gracefully, never raise.
    monkeypatch.setattr(
        power_led.subprocess, "run",
        lambda *a, **k: types.SimpleNamespace(returncode=1, stdout=b"", stderr=b""),
    )
    ctrl = PowerLedController(LPBL, ec_io=str(tmp_path / "nope"))
    assert ctrl.available() is False
    assert ctrl.set(True) is False
    assert ctrl.get() is None
