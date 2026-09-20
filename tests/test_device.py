import os

from py_modules.device import build_layout, detect_device, lookup_name, read_zone_format, build_capabilities, build_device
import led_device as _led_device_mod
SysfsRgbDevice = _led_device_mod.SysfsRgbDevice
NullDevice = _led_device_mod.NullDevice
MultiSysfsRgbDevice = _led_device_mod.MultiSysfsRgbDevice
HpOmenRgbDevice = _led_device_mod.HpOmenRgbDevice
ValveLedsDevice = _led_device_mod.ValveLedsDevice


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


def test_build_layout_bar_is_single_full_width_group():
    layout = build_layout(17, layout_kind="bar")
    assert len(layout) == 1
    assert layout[0]["kind"] == "bar"
    assert layout[0]["region"] == [0.0, 0.0, 1.0, 1.0]
    assert layout[0]["zones"] == list(range(17))


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


def _make_portal_leds(root, count=8):
    names = [f"rgb:l{i}" for i in range(1, 5)] + [f"rgb:r{i}" for i in range(1, 5)]
    for name in names[:count]:
        _make_led(root, name, {
            "multi_index": "blue green red",
            "multi_max_intensity": "255 255 255",
            "multi_intensity": "0 0 0",
            "brightness": "0",
            "max_brightness": "255",
        })


def test_lookup_name_matches_board():
    assert lookup_name("RC73XA", "ROG Xbox Ally X RC73XA_RC73XA") == "ROG Xbox Ally X"


def test_lookup_name_unknown_falls_back_to_product():
    assert lookup_name("X", "MysteryHandheld") == "MysteryHandheld"


def test_detect_device_reads_dmi(tmp_path):
    _make_dmi(str(tmp_path), "RC73XA", "ROG Xbox Ally X RC73XA_RC73XA")
    device = detect_device(str(tmp_path))
    assert device["name"] == "ROG Xbox Ally X"
    assert device["board"] == "RC73XA"


def test_detect_device_reads_vendor_and_model(tmp_path):
    dmi = tmp_path / "sys/class/dmi/id"
    dmi.mkdir(parents=True)
    (dmi / "board_name").write_text("MS-1T8K")
    (dmi / "product_name").write_text("Claw A8 BZ2EM")
    (dmi / "sys_vendor").write_text("Micro-Star International Co., Ltd.")

    info = detect_device(str(tmp_path))

    assert info["vendor"] == "Micro-Star International Co., Ltd."
    assert info["model"] == "Claw A8 BZ2EM"


def test_detect_device_falls_back_to_device_tree_model(tmp_path):
    model = tmp_path / "sys/firmware/devicetree/base/model"
    model.parent.mkdir(parents=True)
    model.write_bytes(b"AYN Odin 2 Portal\x00")

    info = detect_device(str(tmp_path))

    assert info["model"] == "AYN Odin 2 Portal"
    assert info["name"] == "AYN Odin 2 Portal"


def test_read_zone_format_packed_decimal(tmp_path):
    led = os.path.join(str(tmp_path), "ally:rgb:joystick_rings")
    os.makedirs(led)
    open(os.path.join(led, "multi_index"), "w").write("rgb rgb rgb rgb")
    zones, fmt = read_zone_format(led)
    assert zones == 4
    assert fmt == "packed_decimal"


def test_read_zone_format_keeps_non_ally_rgb_as_hex(tmp_path):
    led = os.path.join(str(tmp_path), "rgb:other")
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


def test_unknown_device_uses_valid_standard_multicolor_sysfs(tmp_path):
    root = str(tmp_path)
    _make_dmi(root, "X", "MysteryHandheld")
    _make_led(root, "rgb:standard", {
        "multi_index": "blue green red blue green red",
        "multi_max_intensity": "255 255 255 255 255 255",
        "multi_intensity": "0 0 0 0 0 0",
        "brightness": "0",
        "max_brightness": "255",
    })

    ctx = build_device(root)

    assert isinstance(ctx["device"], SysfsRgbDevice)
    assert ctx["capabilities"]["states"]["color"] == "supported"
    assert ctx["capabilities"]["states"]["brightness"] == "supported"
    assert ctx["capabilities"]["perZone"] is False
    assert ctx["device"].supports_per_zone() is False
    assert ctx["capabilities"]["layoutKind"] == "uniform"
    assert ctx["device"].apply_zones([(255, 0, 0)], 100, True) is True
    intensity = os.path.join(root, "sys/class/leds/rgb:standard/multi_intensity")
    assert open(intensity).read() == "0 0 255 0 0 255"
    assert ctx["device"].apply_solid((0, 0, 255), 100, True) is True
    assert open(intensity).read() == "255 0 0 255 0 0"


def test_generic_sysfs_selection_is_deterministic(tmp_path):
    root = str(tmp_path)
    _make_dmi(root, "X", "MysteryHandheld")
    files = {
        "multi_index": "red green blue",
        "multi_intensity": "0 0 0",
        "brightness": "0",
        "max_brightness": "255",
    }
    _make_led(root, "rgb:zeta", files)
    _make_led(root, "rgb:alpha", files)

    ctx = build_device(root)

    assert ctx["device"].led_path.endswith("rgb:alpha")


def test_unknown_device_rejects_malformed_multicolor_schema(tmp_path):
    root = str(tmp_path)
    _make_dmi(root, "X", "MysteryHandheld")
    _make_led(root, "rgb:broken", {
        "multi_index": "red red blue",
        "multi_intensity": "0 0 0",
        "brightness": "0",
        "max_brightness": "255",
    })

    ctx = build_device(root)

    assert isinstance(ctx["device"], NullDevice)
    assert ctx["capabilities"]["states"]["color"] == "unsupported"


def test_unknown_device_rejects_nonstandard_packed_rgb_node(tmp_path):
    root = str(tmp_path)
    _make_dmi(root, "X", "MysteryHandheld")
    _make_led(root, "rgb:packed", {
        "multi_index": "rgb rgb rgb rgb",
        "multi_intensity": "0 0 0 0",
        "brightness": "0",
        "max_brightness": "255",
    })

    ctx = build_device(root)

    assert isinstance(ctx["device"], NullDevice)
    assert ctx["capabilities"]["states"]["color"] == "unsupported"


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
    caps = build_capabilities(profile, has_led=True, zones=2, max_brightness=255, ambilight=False)
    assert caps["states"]["color"] == "experimental"
    assert caps["states"]["effects"] == "experimental"
    assert caps["states"]["brightness"] == "supported"


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
    assert ctx["device"].apply_zones([(255, 0, 0)], 100, True) is True
    intensity = os.path.join(str(tmp_path), "sys/class/leds/ally:rgb:joystick_rings/multi_intensity")
    assert open(intensity).read() == "16711680 16711680 16711680 16711680"


def test_complete_portal_topology_builds_uniform_multi_node_device(tmp_path):
    root = str(tmp_path)
    model = tmp_path / "sys/firmware/devicetree/base/model"
    model.parent.mkdir(parents=True)
    model.write_bytes(b"AYN Odin 2 Portal\x00")
    _make_portal_leds(root)

    ctx = build_device(root)

    assert isinstance(ctx["device"], MultiSysfsRgbDevice)
    assert ctx["info"]["name"] == "AYN Odin 2 Portal"
    assert ctx["capabilities"]["zones"] == 1
    assert ctx["capabilities"]["perZone"] is False
    assert ctx["capabilities"]["color"] is True


def test_partial_portal_topology_does_not_claim_generic_led_support(tmp_path):
    root = str(tmp_path)
    _make_portal_leds(root, count=7)

    ctx = build_device(root)

    assert isinstance(ctx["device"], NullDevice)
    assert ctx["capabilities"]["color"] is False


def _make_omen_platform(root, zones=8):
    platform = os.path.join(root, "sys/devices/platform/hp-rgb-lighting")
    os.makedirs(platform)
    for index in range(zones):
        with open(os.path.join(platform, f"zone{index}"), "w") as handle:
            handle.write("000000")
    with open(os.path.join(platform, "brightness"), "w") as handle:
        handle.write("0")


def test_hp_omen_platform_route_exposes_only_demonstrated_capabilities(tmp_path):
    root = str(tmp_path)
    _make_dmi(root, "8D24", "OMEN Gaming Laptop 16-ap0xxx")
    _make_omen_platform(root)

    ctx = build_device(root)

    assert isinstance(ctx["device"], HpOmenRgbDevice)
    assert ctx["capabilities"]["color"] is True
    assert ctx["capabilities"]["brightness"] is False
    assert ctx["capabilities"]["states"]["brightness"] == "unsupported"
    assert ctx["capabilities"]["perZone"] is False
    assert ctx["capabilities"]["zones"] == 1
    assert ctx["capabilities"]["maxRenderFps"] == 2


def test_hp_omen_platform_route_rejects_partial_interface(tmp_path):
    root = str(tmp_path)
    _make_dmi(root, "8D24", "OMEN Gaming Laptop 16-ap0xxx")
    _make_omen_platform(root, zones=7)

    ctx = build_device(root)

    assert isinstance(ctx["device"], NullDevice)
    assert ctx["capabilities"]["color"] is False


def test_hp_platform_interface_is_not_claimed_on_foreign_identity(tmp_path):
    root = str(tmp_path)
    _make_dmi(root, "X", "MysteryHandheld")
    _make_omen_platform(root)

    ctx = build_device(root)

    assert isinstance(ctx["device"], NullDevice)


def test_build_device_ally_uses_canonical_node_when_input_rgb_competes(tmp_path):
    root = str(tmp_path)
    leds_dir = os.path.join(root, "sys/class/leds")
    input_name = "input29:rgb:indicator"
    ally_name = "ally:rgb:joystick_rings"
    _make_dmi(root, "RC73YA", "ROG Xbox Ally RC73YA")
    _make_led(root, input_name,
              {"multi_intensity": "0 0 0", "multi_index": "red green blue",
               "max_brightness": "255", "brightness": "0"})
    _make_led(root, ally_name,
              {"multi_intensity": "0 0 0 0", "multi_index": "rgb rgb rgb rgb",
               "max_brightness": "255", "brightness": "0"})
    ctx = build_device(root)

    assert isinstance(ctx["device"], SysfsRgbDevice)
    assert ctx["device"].apply_zones([(255, 0, 0)], 100, True) is True
    assert open(os.path.join(leds_dir, ally_name, "multi_intensity")).read() == (
        "16711680 16711680 16711680 16711680"
    )
    assert open(os.path.join(leds_dir, input_name, "multi_intensity")).read() == "0 0 0"


def test_build_device_ally_uses_hid_fallback_when_only_input_rgb_exists(tmp_path, monkeypatch):
    import py_modules.device as device_module

    fallback_device = object()
    root = str(tmp_path)
    _make_dmi(root, "RC73XA", "ROG Xbox Ally X RC73XA")
    _make_led(root, "input20:rgb:indicator",
              {"multi_intensity": "0 0 0", "multi_index": "red green blue",
               "max_brightness": "255", "brightness": "0"})
    monkeypatch.setattr(device_module, "HID_AVAILABLE", True)
    monkeypatch.setattr(
        device_module,
        "_build_hid_context",
        lambda *args: {"device": fallback_device, "capabilities": {"color": True}},
    )

    ctx = build_device(root)

    assert ctx["device"] is fallback_device
    input_intensity = os.path.join(
        root, "sys/class/leds/input20:rgb:indicator/multi_intensity"
    )
    assert open(input_intensity).read() == "0 0 0"


def test_build_device_ally_uses_hid_fallback_for_incompatible_packed_maxima(tmp_path, monkeypatch):
    import py_modules.device as device_module

    fallback_device = object()
    _make_dmi(str(tmp_path), "RC72LA", "ROG Ally X RC72LA")
    _make_led(str(tmp_path), "ally:rgb:joystick_rings",
              {"multi_intensity": "0 0 0 0", "multi_index": "rgb rgb rgb rgb",
               "multi_max_intensity": "255 255 255 255",
               "max_brightness": "255", "brightness": "0"})
    monkeypatch.setattr(device_module, "HID_AVAILABLE", True)
    monkeypatch.setattr(
        device_module,
        "_build_hid_context",
        lambda *args: {"device": fallback_device, "capabilities": {"color": True}},
    )

    ctx = build_device(str(tmp_path))
    assert ctx["device"] is fallback_device


def test_build_device_original_ally_does_not_fall_back_to_sysfs(tmp_path, monkeypatch):
    import py_modules.device as device_module

    _make_dmi(str(tmp_path), "RC71L", "ROG Ally RC71L_RC71L")
    _make_led(str(tmp_path), "ally:rgb:joystick_rings",
              {"multi_intensity": "0 0 0 0", "multi_index": "rgb rgb rgb rgb",
               "max_brightness": "255", "brightness": "128"})
    monkeypatch.setattr(device_module, "HID_AVAILABLE", False)

    ctx = build_device(str(tmp_path))
    assert isinstance(ctx["device"], NullDevice)
    intensity = os.path.join(str(tmp_path), "sys/class/leds/ally:rgb:joystick_rings/multi_intensity")
    assert open(intensity).read() == "0 0 0 0"


def test_build_device_legion_without_node_is_null_and_unsupported(tmp_path):
    _make_dmi(str(tmp_path), "83N0", "83N0")
    os.makedirs(os.path.join(str(tmp_path), "sys/class/leds"))
    ctx = build_device(str(tmp_path))
    assert ctx["info"]["name"] == "Legion Go 2"
    assert isinstance(ctx["device"], NullDevice)
    assert ctx["capabilities"]["states"]["color"] == "unsupported"


def test_build_device_msi_uses_bgr_on_sysfs_fallback(tmp_path):
    _make_dmi(str(tmp_path), "", "Claw 8 AI+ A2VM")
    _make_led(str(tmp_path), "rgb:claw",
              {"multi_intensity": "0 0 0 0", "multi_index": "rgb rgb rgb rgb",
               "max_brightness": "255", "brightness": "0"})
    ctx = build_device(str(tmp_path))
    assert ctx["info"]["name"] == "MSI Claw 8 AI+"
    assert isinstance(ctx["device"], SysfsRgbDevice)
    assert ctx["device"]._color_order == "bgr"


def test_capabilities_expose_conflicts_with_system_rgb():
    from py_modules.device_profiles import resolve_profile

    profile = resolve_profile("RC71L", "ROG Ally RC71L_RC71L")
    caps = build_capabilities(profile, True, 4, 100, False)
    assert caps["conflictsWithSystemRgb"] is True


def _make_valve_bar(root, count=17):
    for i in range(count):
        _make_led(root, f"valve-leds[{i}]", {
            "multi_index": "red green blue", "multi_intensity": "0 0 0",
            "brightness": "255", "max_brightness": "255", "effect": "normal", "enabled": "0",
        })


def test_build_device_steam_machine_is_valve_bar(tmp_path):
    _make_dmi(str(tmp_path), "Fremont", "Fremont")
    _make_valve_bar(str(tmp_path), count=17)
    ctx = build_device(str(tmp_path))
    assert ctx["info"]["name"] == "Steam Machine"
    assert isinstance(ctx["device"], ValveLedsDevice)
    caps = ctx["capabilities"]
    assert caps["zones"] == 17
    assert caps["layoutKind"] == "bar"
    assert caps["perZone"] is True
    assert caps["conflictsWithSystemRgb"] is True
    assert caps["hasBattery"] is False  # desktop console: charger-only gate must be hidden
    assert caps["persistentStartup"] is True
    assert caps["performanceMode"] is True
    assert caps["states"]["color"] == "supported"
    assert len(caps["layout"]) == 1 and caps["layout"][0]["kind"] == "bar"


def test_build_device_steam_machine_without_driver_is_unsupported(tmp_path):
    # Kernel build without leds-valve: no valve-leds nodes present.
    _make_dmi(str(tmp_path), "Fremont", "Fremont")
    os.makedirs(os.path.join(str(tmp_path), "sys/class/leds"))
    ctx = build_device(str(tmp_path))
    assert ctx["info"]["name"] == "Steam Machine"
    assert isinstance(ctx["device"], NullDevice)
    assert ctx["capabilities"]["states"]["color"] == "unsupported"


def test_capabilities_conflicts_defaults_false():
    from py_modules.device_profiles import resolve_profile

    profile = resolve_profile("RC72LA", "ROG Ally X")
    caps = build_capabilities(profile, True, 4, 255, False)
    assert caps["conflictsWithSystemRgb"] is False


_OXP_LED_FILES = {
    "multi_index": "red green blue",
    "multi_max_intensity": "100 100 100",
    "multi_intensity": "0 0 0",
    "brightness": "0",
    "max_brightness": "100",
    "enabled": "false",
    "effect": "rainbow",
}


def test_build_device_oxp_uses_latch_device(tmp_path):
    root = str(tmp_path)
    _make_dmi(root, "ONEXPLAYER APEX", "ONEXPLAYER APEX")
    _make_led(root, "oxp:rgb:joystick_rings", _OXP_LED_FILES)
    ctx = build_device(root)
    assert ctx["info"]["name"] == "OneXPlayer OneXFly Apex"
    assert type(ctx["device"]).__name__ == "SysfsRgbDevice"
    caps = ctx["capabilities"]
    assert caps["zones"] == 1
    assert caps["maxBrightness"] == 100
    assert caps["maxRenderFps"] == 10
    assert caps["states"]["color"] == "supported"
    assert caps["conflictsWithSystemRgb"] is True
    assert caps["hhdRgbTakeover"] is True
    assert ctx["device"].apply_zones([(255, 0, 0)], 100, True) is True
    led = os.path.join(root, "sys/class/leds/oxp:rgb:joystick_rings")
    assert open(os.path.join(led, "enabled")).read() == "true"
    assert open(os.path.join(led, "effect")).read() == "monocolor"
    assert open(os.path.join(led, "multi_intensity")).read() == "100 0 0"


def test_build_device_oxp_without_node_degrades(tmp_path):
    root = str(tmp_path)
    _make_dmi(root, "ONEXPLAYER APEX", "ONEXPLAYER APEX")
    ctx = build_device(root)
    assert ctx["capabilities"]["color"] is False
    assert type(ctx["device"]).__name__ == "NullDevice"
