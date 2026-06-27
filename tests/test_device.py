import os

from py_modules.device import build_layout, detect_device, detect_capabilities, lookup_name


def test_build_layout_splits_into_two_sticks():
    layout = build_layout(4)
    assert [g["name"] for g in layout] == ["Left stick", "Right stick"]
    assert layout[0]["zones"] == [0, 1]
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
