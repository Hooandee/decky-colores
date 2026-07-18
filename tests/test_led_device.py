import os

from py_modules.led_device import SysfsRgbDevice, ValveLedsDevice, discover_valve_leds, IndicatorLed


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


def test_color_correction_scales_channel(tmp_path):
    led = _make_led(tmp_path)
    device = SysfsRgbDevice(led, zones=4, max_brightness=255, color_correction=(1.0, 0.5, 1.0))
    device.apply_zones([(255, 255, 255)], 100, True)
    assert _read(os.path.join(led, "multi_intensity")) == "0xff80ff 0xff80ff 0xff80ff 0xff80ff"


def test_color_correction_default_unchanged(tmp_path):
    led = _make_led(tmp_path)
    device = SysfsRgbDevice(led, zones=4, max_brightness=255)
    device.apply_zones([(255, 0, 0)], 100, True)
    assert _read(os.path.join(led, "multi_intensity")) == "0xff0000 0xff0000 0xff0000 0xff0000"


def test_supports_per_zone():
    assert SysfsRgbDevice("/x").supports_per_zone() is True


def test_decimal_format_writes_space_separated_channels(tmp_path):
    led = _make_led(tmp_path, multi_index="red green blue red green blue red green blue red green blue")
    device = SysfsRgbDevice(led, zones=4, max_brightness=255, index_format="decimal")
    device.apply_zones([(255, 0, 0)], 100, True)
    assert _read(os.path.join(led, "multi_intensity")) == "255 0 0 255 0 0 255 0 0 255 0 0"


def test_decimal_format_respects_color_order(tmp_path):
    led = _make_led(tmp_path, multi_index="red green blue red green blue")
    device = SysfsRgbDevice(led, zones=2, max_brightness=255, index_format="decimal", color_order="bgr")
    device.apply_zones([(255, 0, 0)], 100, True)
    assert _read(os.path.join(led, "multi_intensity")) == "0 0 255 0 0 255"


def _make_valve_bar(tmp_path, count=17):
    leds = os.path.join(str(tmp_path), "leds")
    os.makedirs(leds)
    for i in range(count):
        node = os.path.join(leds, f"valve-leds[{i}]")
        os.makedirs(node)
        open(os.path.join(node, "multi_index"), "w").write("red green blue")
        open(os.path.join(node, "multi_intensity"), "w").write("0 0 0")
        open(os.path.join(node, "brightness"), "w").write("0")
        open(os.path.join(node, "max_brightness"), "w").write("255")
        open(os.path.join(node, "effect"), "w").write("normal")
        open(os.path.join(node, "enabled"), "w").write("0")
        open(os.path.join(node, "brightness_scale"), "w").write("0x00")
    return leds


def test_discover_valve_leds_orders_numerically(tmp_path):
    leds = _make_valve_bar(tmp_path, count=17)
    nodes = discover_valve_leds(leds)
    assert len(nodes) == 17
    # Numeric order, so [2] precedes [10] (lexical order would not).
    assert nodes[2].endswith("valve-leds[2]")
    assert nodes[10].endswith("valve-leds[10]")


def test_discover_valve_leds_ignores_other_nodes(tmp_path):
    leds = _make_valve_bar(tmp_path, count=3)
    os.makedirs(os.path.join(leds, "status:white"))
    os.makedirs(os.path.join(leds, "input1::capslock"))
    assert len(discover_valve_leds(leds)) == 3


def test_valve_sets_manual_and_writes_per_node(tmp_path):
    leds = _make_valve_bar(tmp_path, count=3)
    nodes = discover_valve_leds(leds)
    device = ValveLedsDevice(nodes, max_brightness=255)
    assert device.apply_zones([(255, 0, 0), (0, 255, 0), (0, 0, 255)], 100, True) is True
    assert _read(os.path.join(nodes[0], "effect")) == "manual"
    assert _read(os.path.join(nodes[0], "enabled")) == "1"
    assert _read(os.path.join(nodes[0], "multi_intensity")) == "255 0 0"
    assert _read(os.path.join(nodes[1], "multi_intensity")) == "0 255 0"
    assert _read(os.path.join(nodes[2], "multi_intensity")) == "0 0 255"


def test_valve_manual_forces_per_node_brightness_full(tmp_path):
    # Per-node brightness is the multicolor intensity scaler; the strip-global
    # brightness_scale is the master we drive, so per-node stays at max.
    leds = _make_valve_bar(tmp_path, count=3)
    nodes = discover_valve_leds(leds)
    ValveLedsDevice(nodes, max_brightness=255).apply_zones([(255, 255, 255)], 50, True)
    assert _read(os.path.join(nodes[0], "brightness")) == "255"
    assert _read(os.path.join(nodes[2], "brightness")) == "255"


def test_valve_brightness_drives_global_scale(tmp_path):
    # brightness_scale (on the control node) is the real hardware master; a bar
    # with brightness_scale=0 is dark regardless of per-node values (root cause of
    # the first "writes succeed but nothing lights" bug).
    leds = _make_valve_bar(tmp_path, count=3)
    nodes = discover_valve_leds(leds)
    ValveLedsDevice(nodes, max_brightness=255).apply_zones([(255, 255, 255)], 50, True)
    assert _read(os.path.join(nodes[0], "brightness_scale")) == "128"


def test_valve_power_off_zeroes_global_scale(tmp_path):
    leds = _make_valve_bar(tmp_path, count=2)
    nodes = discover_valve_leds(leds)
    ValveLedsDevice(nodes).apply_zones([(0, 0, 255)], 100, False)
    assert _read(os.path.join(nodes[0], "brightness_scale")) == "0"


def test_valve_fills_short_zone_list(tmp_path):
    leds = _make_valve_bar(tmp_path, count=3)
    nodes = discover_valve_leds(leds)
    ValveLedsDevice(nodes).apply_zones([(10, 20, 30)], 100, True)
    assert _read(os.path.join(nodes[2], "multi_intensity")) == "10 20 30"


def test_valve_solid_paints_all_zones(tmp_path):
    leds = _make_valve_bar(tmp_path, count=4)
    nodes = discover_valve_leds(leds)
    ValveLedsDevice(nodes).apply_solid((90, 0, 255), 100, True)
    for node in nodes:
        assert _read(os.path.join(node, "multi_intensity")) == "90 0 255"


def test_valve_invalidate_re_sends_manual(tmp_path):
    leds = _make_valve_bar(tmp_path, count=2)
    nodes = discover_valve_leds(leds)
    device = ValveLedsDevice(nodes)
    device.apply_zones([(1, 2, 3)], 100, True)
    open(os.path.join(nodes[0], "effect"), "w").write("normal")  # Steam reasserts status
    device.invalidate()
    device.apply_zones([(4, 5, 6)], 100, True)
    assert _read(os.path.join(nodes[0], "effect")) == "manual"


def test_valve_color_correction_applies(tmp_path):
    leds = _make_valve_bar(tmp_path, count=1)
    nodes = discover_valve_leds(leds)
    ValveLedsDevice(nodes, color_correction=(1.0, 0.5, 1.0)).apply_zones([(255, 255, 255)], 100, True)
    assert _read(os.path.join(nodes[0], "multi_intensity")) == "255 128 255"


def test_valve_reverse_maps_logical_left_to_last_node(tmp_path):
    # LED index 0 is the RIGHT end on hardware; with reverse=True logical color[0]
    # (spatial left) must land on the highest-index node (physical left).
    leds = _make_valve_bar(tmp_path, count=3)
    nodes = discover_valve_leds(leds)
    ValveLedsDevice(nodes, reverse=True).apply_zones([(255, 0, 0), (0, 255, 0), (0, 0, 255)], 100, True)
    assert _read(os.path.join(nodes[2], "multi_intensity")) == "255 0 0"  # logical left -> node[2]
    assert _read(os.path.join(nodes[0], "multi_intensity")) == "0 0 255"  # logical right -> node[0]


def test_valve_supports_per_zone_not_hardware():
    device = ValveLedsDevice(["/x"])
    assert device.supports_per_zone() is True
    assert device.supports_hardware_effects() is False


def test_valve_unavailable_without_nodes():
    device = ValveLedsDevice([])
    assert device.available is False
    assert device.apply_zones([(1, 2, 3)], 100, True) is False


def test_valve_save_startup_writes_last_applied(tmp_path):
    leds = _make_valve_bar(tmp_path, count=3)
    nodes = discover_valve_leds(leds)
    device = ValveLedsDevice(nodes, max_brightness=255)
    device.apply_zones([(255, 0, 0), (0, 255, 0), (0, 0, 255)], 100, True)
    assert device.save_startup() is True
    assert _read(os.path.join(nodes[0], "multi_intensity_startup")) == "255 0 0"
    assert _read(os.path.join(nodes[2], "multi_intensity_startup")) == "0 0 255"
    assert _read(os.path.join(nodes[0], "brightness_startup")) == "255"


def test_valve_save_startup_noop_before_any_apply(tmp_path):
    leds = _make_valve_bar(tmp_path, count=2)
    device = ValveLedsDevice(discover_valve_leds(leds))
    assert device.save_startup() is False


def test_valve_read_and_restore_startup_round_trip(tmp_path):
    leds = _make_valve_bar(tmp_path, count=3)
    nodes = discover_valve_leds(leds)
    for i, node in enumerate(nodes):
        open(os.path.join(node, "multi_intensity_startup"), "w").write(f"{i} 90 255")
        open(os.path.join(node, "brightness_startup"), "w").write("56")
    device = ValveLedsDevice(nodes)
    factory = device.read_startup()
    assert factory["intensities"][2] == "2 90 255"
    assert factory["level"] == "56"
    # A custom color persisted, then restore hands the factory value back.
    device.apply_zones([(10, 20, 30)], 100, True)
    device.save_startup()
    assert _read(os.path.join(nodes[0], "multi_intensity_startup")) == "10 20 30"
    assert device.restore_startup(factory) is True
    assert _read(os.path.join(nodes[0], "multi_intensity_startup")) == "0 90 255"
    assert _read(os.path.join(nodes[0], "brightness_startup")) == "56"


def _make_indicator(tmp_path, max_brightness="100"):
    node = os.path.join(str(tmp_path), "status")
    os.makedirs(node)
    open(os.path.join(node, "brightness"), "w").write("0")
    open(os.path.join(node, "max_brightness"), "w").write(max_brightness)
    return node


def test_indicator_apply_scales_and_off(tmp_path):
    node = _make_indicator(tmp_path)
    ind = IndicatorLed(node, 100)
    assert ind.available() is True
    ind.apply(True, 50)
    assert _read(os.path.join(node, "brightness")) == "50"
    ind.apply(False, 50)
    assert _read(os.path.join(node, "brightness")) == "0"


def test_indicator_unavailable_without_path():
    ind = IndicatorLed(None)
    assert ind.available() is False
    assert ind.apply(True, 100) is False
