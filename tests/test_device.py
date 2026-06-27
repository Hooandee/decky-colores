import os

from py_modules.device import build_layout, detect_device, detect_capabilities, lookup_name, read_zone_format, build_capabilities, build_device
import led_device as _led_device_mod
SysfsRgbDevice = _led_device_mod.SysfsRgbDevice
NullDevice = _led_device_mod.NullDevice


def test_build_layout_splits_into_two_sticks():
    layout = build_layout(4)
    assert [g["name"] for g in layout] == ["Left stick", "Right stick"]
    assert layout[0]["zones"] == [0, 1]
    assert layout[1]["zones"] == [2, 3]


def test_build_layout_swap_sticks_reverses_anchor_groups():
    layout = build_layout(4, swap_sticks=True)
    assert layout[0]["name"] == "Right stick"
    assert layout[0]["zones"] == [0, 1]
    assert layout[1]["name"] == "Left stick"
    assert layout[1]["zones"] == [2, 3]


def test_build_layout_single_zone():
    layout = build_layout(1)
    assert len(layout) == 1
    assert layout[0]["zones"] == [0]


def test_build_layout_none_when_no_zones():
    assert build_layout(0) == []


def _make_dmi(root, board, product):
    dmi = os.path.join(root, "sys/class/dmi/id")
    os.makedirs(dmi)
    (open(os.path.join(dmi, "board_name"), "w")).write(board)
    (open(os.path.join(dmi, "product_name"), "w")).write(product)


def _make_led(root, name, files):
    led = os.path.join(root, "sys/class/leds", name)
    os.makedirs(led)
    for filename, content in files.items():
        with open(os.path.join(led, filename), "w") as handle:
            handle.write(content)


def test_lookup_name_matches_board():
    assert lookup_name("RC73XA", "ROG Xbox Ally X RC73XA_RC73XA") == "ROG Xbox Ally X"


def test_lookup_name_unknown_falls_back_to_product():
    assert lookup_name("X", "MysteryHandheld") == "MysteryHandheld"


def test_detect_device_reads_dmi(tmp_path):
    _make_dmi(str(tmp_path), "RC73XA", "ROG Xbox Ally X RC73XA_RC73XA")
    device = detect_device(str(tmp_path))
    assert device["name"] == "ROG Xbox Ally X"
    assert device["board"] == "RC73XA"


def test_detect_capabilities_ally_rgb(tmp_path):
    _make_led(
        str(tmp_path),
        "ally:rgb:joystick_rings",
        {
            "multi_intensity": "0 0 0 0",
            "multi_index": "rgb rgb rgb rgb",
            "max_brightness": "255",
            "brightness": "0",
        },
    )
    caps = detect_capabilities(str(tmp_path))
    assert caps["color"] is True
    assert caps["zones"] == 4
    assert caps["maxBrightness"] == 255
    assert caps["ledPath"].endswith("ally:rgb:joystick_rings")


def test_detect_capabilities_no_rgb_led(tmp_path):
    _make_led(str(tmp_path), "input1::capslock", {"max_brightness": "1", "brightness": "0"})
    caps = detect_capabilities(str(tmp_path))
    assert caps["color"] is False
    assert caps["ledPath"] is None


def test_detect_capabilities_zero_max_brightness_falls_back(tmp_path):
    _make_led(
        str(tmp_path),
        "ally:rgb:joystick_rings",
        {"multi_intensity": "0 0 0 0", "multi_index": "rgb rgb rgb rgb", "max_brightness": "0"},
    )
    caps = detect_capabilities(str(tmp_path))
    assert caps["maxBrightness"] == 255


def test_detect_capabilities_no_leds_dir(tmp_path):
    caps = detect_capabilities(str(tmp_path))
    assert caps["color"] is False
    assert caps["zones"] == 0


def test_read_zone_format_hex(tmp_path):
    led = os.path.join(str(tmp_path), "led")
    os.makedirs(led)
    open(os.path.join(led, "multi_index"), "w").write("rgb rgb rgb rgb")
    zones, fmt = read_zone_format(led)
    assert zones == 4
    assert fmt == "hex"


def test_read_zone_format_decimal(tmp_path):
    led = os.path.join(str(tmp_path), "led")
    os.makedirs(led)
    open(os.path.join(led, "multi_index"), "w").write("red green blue red green blue")
    zones, fmt = read_zone_format(led)
    assert zones == 2
    assert fmt == "decimal"


def test_build_capabilities_supported_when_present():
    profile = {"name": "ROG Ally X", "driver": "sysfs", "color_order": "rgb",
               "supported_effects": ["breathing"], "experimental": []}
    caps = build_capabilities(profile, has_led=True, zones=4, max_brightness=255, ambilight=True)
    assert caps["color"] is True
    assert caps["zones"] == 4
    assert caps["perZone"] is True
    assert caps["states"]["color"] == "supported"
    assert caps["supportedEffects"] == ["breathing"]


def test_build_capabilities_experimental_features():
    profile = {"name": "Legion Go 2", "driver": "hid_legion_tablet", "color_order": "rgb",
               "supported_effects": ["breathing"], "experimental": ["color", "effects"]}
    caps = build_capabilities(profile, has_led=False, zones=0, max_brightness=255, ambilight=False)
    assert caps["states"]["color"] == "experimental"
    assert caps["states"]["effects"] == "experimental"
    assert caps["states"]["brightness"] == "unsupported"


def test_build_capabilities_gradient_crossfade_flag():
    legion = {"name": "Legion Go S", "driver": "hid_legion_go_s", "color_order": "rgb",
              "supported_effects": [], "experimental": [], "gradient_crossfade": True}
    ally = {"name": "ROG Ally X", "driver": "sysfs", "color_order": "rgb",
            "supported_effects": [], "experimental": []}
    legion_caps = build_capabilities(legion, has_led=True, zones=2, max_brightness=255, ambilight=False)
    ally_caps = build_capabilities(ally, has_led=True, zones=4, max_brightness=255, ambilight=False)
    assert legion_caps["gradientCrossfade"] is True
    assert ally_caps["gradientCrossfade"] is False


def test_build_capabilities_unsupported_when_absent():
    profile = {"name": "X", "driver": "sysfs", "color_order": "rgb",
               "supported_effects": [], "experimental": []}
    caps = build_capabilities(profile, has_led=False, zones=0, max_brightness=255, ambilight=False)
    assert caps["states"]["color"] == "unsupported"
    assert caps["color"] is False


def test_build_device_ally_returns_sysfs_writer(tmp_path):
    _make_dmi(str(tmp_path), "RC72LA", "ROG Ally X RC72LA")
    _make_led(str(tmp_path), "ally:rgb:joystick_rings",
              {"multi_intensity": "0 0 0 0", "multi_index": "rgb rgb rgb rgb",
               "max_brightness": "255", "brightness": "0"})
    ctx = build_device(str(tmp_path))
    assert ctx["info"]["name"] == "ROG Ally X"
    assert isinstance(ctx["device"], SysfsRgbDevice)
    assert ctx["capabilities"]["states"]["color"] == "supported"
    assert ctx["capabilities"]["zones"] == 4


def test_build_device_legion_without_node_is_null_and_experimental(tmp_path):
    _make_dmi(str(tmp_path), "83N0", "83N0")
    os.makedirs(os.path.join(str(tmp_path), "sys/class/leds"))
    ctx = build_device(str(tmp_path))
    assert ctx["info"]["name"] == "Legion Go 2"
    assert isinstance(ctx["device"], NullDevice)
    assert ctx["capabilities"]["states"]["color"] == "experimental"


def test_build_device_msi_uses_bgr_on_sysfs_fallback(tmp_path):
    _make_dmi(str(tmp_path), "", "Claw 8 AI+ A2VM")
    _make_led(str(tmp_path), "rgb:claw",
              {"multi_intensity": "0 0 0 0", "multi_index": "rgb rgb rgb rgb",
               "max_brightness": "255", "brightness": "0"})
    ctx = build_device(str(tmp_path))
    assert ctx["info"]["name"] == "MSI Claw 8 AI+"
    assert isinstance(ctx["device"], SysfsRgbDevice)
    assert ctx["device"]._color_order == "bgr"
