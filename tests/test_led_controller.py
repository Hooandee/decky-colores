import os

from py_modules.led_controller import LedController


def _make_led(tmp_path):
    led = os.path.join(str(tmp_path), "led")
    os.makedirs(led)
    open(os.path.join(led, "multi_intensity"), "w").write("0 0 0 0")
    open(os.path.join(led, "brightness"), "w").write("0")
    return led


def _read(path):
    with open(path) as handle:
        return handle.read().strip()


def test_unavailable_when_no_path():
    controller = LedController(None)
    assert controller.available is False
    assert controller.apply((255, 0, 0), 100, True) is False


def test_apply_writes_packed_zones_and_brightness(tmp_path):
    led = _make_led(tmp_path)
    controller = LedController(led, zones=4, max_brightness=255)
    assert controller.apply((255, 0, 0), 100, True) is True
    assert _read(os.path.join(led, "multi_intensity")) == "0xff0000 0xff0000 0xff0000 0xff0000"
    assert _read(os.path.join(led, "brightness")) == "255"


def test_apply_scales_brightness(tmp_path):
    led = _make_led(tmp_path)
    controller = LedController(led, zones=4, max_brightness=255)
    controller.apply((0, 255, 0), 50, True)
    assert _read(os.path.join(led, "brightness")) == "128"


def test_power_off_sets_brightness_zero(tmp_path):
    led = _make_led(tmp_path)
    controller = LedController(led, zones=4, max_brightness=255)
    controller.apply((0, 0, 255), 100, False)
    assert _read(os.path.join(led, "brightness")) == "0"


def test_apply_clamps_out_of_range_values(tmp_path):
    led = _make_led(tmp_path)
    controller = LedController(led, zones=2, max_brightness=255)
    controller.apply((300, -5, 128), 250, True)
    assert _read(os.path.join(led, "multi_intensity")) == "0xff0080 0xff0080"
    assert _read(os.path.join(led, "brightness")) == "255"
