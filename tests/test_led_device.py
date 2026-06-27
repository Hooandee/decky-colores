import os

from py_modules.led_device import SysfsRgbDevice


def _make_led(tmp_path, multi_index="rgb rgb rgb rgb"):
    led = os.path.join(str(tmp_path), "led")
    os.makedirs(led)
    open(os.path.join(led, "multi_index"), "w").write(multi_index)
    open(os.path.join(led, "multi_intensity"), "w").write("0 0 0 0")
    open(os.path.join(led, "brightness"), "w").write("0")
    return led


def _read(path):
    with open(path) as handle:
        return handle.read().strip()


def test_unavailable_when_no_path():
    device = SysfsRgbDevice(None)
    assert device.available is False
    assert device.apply_zones([(255, 0, 0)], 100, True) is False


def test_hex_rgb_packs_zones_and_brightness(tmp_path):
    led = _make_led(tmp_path)
    device = SysfsRgbDevice(led, zones=4, max_brightness=255)
    assert device.apply_zones([(255, 0, 0)], 100, True) is True
    assert _read(os.path.join(led, "multi_intensity")) == "0xff0000 0xff0000 0xff0000 0xff0000"
    assert _read(os.path.join(led, "brightness")) == "255"


def test_hex_bgr_swaps_red_and_blue(tmp_path):
    led = _make_led(tmp_path)
    device = SysfsRgbDevice(led, zones=4, max_brightness=255, color_order="bgr")
    device.apply_zones([(255, 0, 0)], 100, True)
    assert _read(os.path.join(led, "multi_intensity")) == "0x0000ff 0x0000ff 0x0000ff 0x0000ff"


def test_brightness_scales(tmp_path):
    led = _make_led(tmp_path)
    device = SysfsRgbDevice(led, zones=4, max_brightness=255)
    device.apply_zones([(0, 255, 0)], 50, True)
    assert _read(os.path.join(led, "brightness")) == "128"


def test_power_off_sets_brightness_zero(tmp_path):
    led = _make_led(tmp_path)
    device = SysfsRgbDevice(led, zones=4, max_brightness=255)
    device.apply_zones([(0, 0, 255)], 100, False)
    assert _read(os.path.join(led, "brightness")) == "0"


def test_clamps_out_of_range(tmp_path):
    led = _make_led(tmp_path)
    device = SysfsRgbDevice(led, zones=2, max_brightness=255)
    device.apply_zones([(300, -5, 128)], 250, True)
    assert _read(os.path.join(led, "multi_intensity")) == "0xff0080 0xff0080"
    assert _read(os.path.join(led, "brightness")) == "255"


def test_supports_per_zone():
    assert SysfsRgbDevice("/x").supports_per_zone() is True
