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

GENERIC = {
    "driver": "sysfs",
    "color_order": "rgb",
    "zones": 0,
    "supported_effects": ["breathing", "rainbow", "wave", "cycle"],
    "experimental": ["color", "brightness", "effects", "ambilight"],
}

VALVE_STEAM_MACHINE = {
    "driver": "valve_steammachine",
    "color_order": "rgb",
    "zones": 17,
    "supported_effects": ["breathing", "rainbow", "wave", "cycle"],
    "color_correction": [1.0, 1.0, 1.0],
    "conflicts_with_system_rgb": True,
    "experimental": [],
}

# The sysfs RGB node isn't guaranteed on every kernel/Bazzite build for the Ally line.
# When it's missing, build_device drops to the Aura HID driver instead of "no LEDs".
ASUS_SYSFS["fallback"] = ASUS_ALLY_HID


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
    ("board", "Fremont", _profile(VALVE_STEAM_MACHINE, "Steam Machine")),
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
]


def resolve_profile(board, product):
    for field, value, profile in PROFILES:
        if field == "board" and value == board:
            return _copy_profile(profile)
        if field == "product" and value == product:
            return _copy_profile(profile)
        if field == "product_contains" and value in (product or ""):
            return _copy_profile(profile)
    fallback = _copy_profile(GENERIC)
    fallback["name"] = product or board or "Unknown device"
    return fallback
