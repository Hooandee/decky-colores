ASUS_SYSFS = {
    "driver": "sysfs",
    "color_order": "rgb",
    "zones": 4,
    "supported_effects": ["breathing", "rainbow", "wave", "cycle", "spiral"],
    "color_correction": [1.0, 0.85, 1.0],
    "experimental": [],
}

ASUS_ALLY_HID = {
    "driver": "hid_asus_ally",
    "color_order": "rgb",
    "zones": 4,
    # No "spiral": that renders as the Legion-only firmware effect ("Espiral GO").
    "supported_effects": ["breathing", "rainbow", "wave", "cycle"],
    "color_correction": [1.0, 0.85, 1.0],
    "conflicts_with_system_rgb": True,
    "experimental": [],
}

MSI_HID = {
    "driver": "hid_msi",
    "color_order": "bgr",
    "zones": 9,
    "supported_effects": ["breathing", "rainbow", "wave", "cycle", "spiral"],
    "swap_sticks": True,
    "experimental": [],
}

# Power-button ring LED control via the Embedded Controller (see power_led.py).
# Each entry is an EC byte/bit to SET to turn the LED off (cleared = on); polarity is
# uniform across Legion devices. Offsets reverse-engineered from each device's DSDT
# (GZFD.WMAF -> SLT2) and verified on hardware. A list because the original Legion Go
# uses two separate fields (awake + suspend) while Go S / Go 2 use a single shared bit.
POWER_LED_LPBL = [{"offset": 0x10, "mask": 0x40}]  # Go S, Go 2 (LPBL, awake+suspend)
POWER_LED_LEDPM = [  # original Legion Go (LEDP awake + LEDM suspend)
    {"offset": 0x52, "mask": 0x20},
    {"offset": 0x58, "mask": 0x01},
]

LEGION_TABLET_HID = {
    "driver": "hid_legion_tablet",
    "color_order": "rgb",
    "zones": 2,
    "supported_effects": ["breathing", "rainbow", "spiral"],
    "per_controller": True,
    "gradient_crossfade": True,
    "experimental": [],
}

LEGION_GO_S_HID = {
    "driver": "hid_legion_go_s",
    "color_order": "rgb",
    "zones": 2,
    "supported_effects": ["breathing", "rainbow", "spiral"],
    "gradient_crossfade": True,
    "experimental": [],
}

VALVE_LEDS = {
    "driver": "valve_leds",
    "color_order": "rgb",
    "zones": 17,
    "layout_kind": "bar",
    "reverse_zones": True,
    "supported_effects": ["breathing", "rainbow", "wave", "cycle", "comet", "sparkle", "ripple", "aurora"],
    "conflicts_with_system_rgb": True,
    "persistent_startup": True,
    "experimental": [],
}

OXP_HID = {
    "driver": "hid_oxp_v2",
    "color_order": "rgb",
    "zones": 1,
    "max_render_fps": 20,
    "supported_effects": ["breathing", "rainbow", "wave", "cycle"],
    "conflicts_with_system_rgb": True,
    "experimental": [],
}

OXP_SYSFS = {
    "driver": "sysfs",
    "color_order": "rgb",
    "latch": [["enabled", "true"], ["effect", "monocolor"]],
    "max_render_fps": 10,
    "supported_effects": ["breathing", "rainbow", "wave", "cycle"],
    "experimental": [],
}

GENERIC = {
    "driver": "sysfs",
    "color_order": "rgb",
    "zones": 0,
    "supported_effects": ["breathing", "rainbow", "wave", "cycle"],
    "experimental": ["color", "brightness", "effects", "ambilight"],
}

# The sysfs RGB node isn't guaranteed on every kernel/Bazzite build for the Ally line.
# When it's missing, build_device drops to the Aura HID driver instead of "no LEDs".
ASUS_SYSFS["fallback"] = ASUS_ALLY_HID
OXP_SYSFS["fallback"] = OXP_HID


def _copy_bits(bits):
    return [dict(bit) for bit in bits]


def _copy_profile(base):
    merged = dict(base)
    merged["experimental"] = list(base.get("experimental", []))
    if base.get("power_led"):
        merged["power_led"] = _copy_bits(base["power_led"])
    if base.get("fallback"):
        merged["fallback"] = _copy_profile(base["fallback"])
    return merged


def _profile(base, name, power_led=None):
    merged = _copy_profile(base)
    merged["name"] = name
    if power_led is not None:
        merged["power_led"] = _copy_bits(power_led)
    return merged


PROFILES = [
    ("board", "RC71L", _profile(ASUS_ALLY_HID, "ROG Ally")),
    ("board", "RC72LA", _profile(ASUS_SYSFS, "ROG Ally X")),
    ("board", "RC73YA", _profile(ASUS_SYSFS, "ROG Xbox Ally")),
    ("board", "RC73XA", _profile(ASUS_SYSFS, "ROG Xbox Ally X")),
    ("product", "83E1", _profile(LEGION_TABLET_HID, "Legion Go", POWER_LED_LEDPM)),
    ("product", "83N0", _profile(LEGION_TABLET_HID, "Legion Go 2", POWER_LED_LPBL)),
    ("product", "83N1", _profile(LEGION_TABLET_HID, "Legion Go 2", POWER_LED_LPBL)),
    ("product", "83L3", _profile(LEGION_GO_S_HID, "Legion Go S", POWER_LED_LPBL)),
    ("product", "83Q2", _profile(LEGION_GO_S_HID, "Legion Go S", POWER_LED_LPBL)),
    ("product", "83N6", _profile(LEGION_GO_S_HID, "Legion Go S", POWER_LED_LPBL)),
    ("product", "83Q3", _profile(LEGION_GO_S_HID, "Legion Go S", POWER_LED_LPBL)),
    ("product_contains", "Claw 8 AI+", _profile(MSI_HID, "MSI Claw 8 AI+")),
    ("product_contains", "Claw A1M", _profile(MSI_HID, "MSI Claw")),
    ("board", "Fremont", _profile(VALVE_LEDS, "Steam Machine")),
    ("product", "F7F", _profile(VALVE_LEDS, "Steam Machine")),
    ("product", "ONEXPLAYER APEX", _profile(OXP_SYSFS, "OneXPlayer OneXFly Apex")),
    ("product", "ONEXPLAYER F1Pro", _profile(OXP_SYSFS, "OneXPlayer OneXFly F1 Pro")),
    ("product_contains", "ONEXPLAYER", _profile(OXP_SYSFS, "")),
]


def resolve_profile(board, product):
    for field, value, profile in PROFILES:
        if field == "board" and value == board:
            return _copy_profile(profile)
        if field == "product" and value == product:
            return _copy_profile(profile)
        if field == "product_contains" and value in (product or ""):
            resolved = _copy_profile(profile)
            if not resolved.get("name"):
                resolved["name"] = product or board or "Unknown device"
            return resolved
    fallback = _copy_profile(GENERIC)
    fallback["name"] = product or board or "Unknown device"
    return fallback
