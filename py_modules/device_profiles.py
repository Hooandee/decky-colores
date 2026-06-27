ASUS_SYSFS = {
    "driver": "sysfs",
    "color_order": "rgb",
    "zones": 4,
    "supported_effects": ["breathing", "rainbow", "wave", "cycle"],
    "experimental": [],
}

MSI_HID = {
    "driver": "hid_msi",
    "color_order": "bgr",
    "zones": 9,
    "supported_effects": ["breathing", "rainbow", "wave", "cycle"],
    "swap_sticks": True,
    "experimental": ["ambilight"],
}

LEGION_TABLET_HID = {
    "driver": "hid_legion_tablet",
    "color_order": "rgb",
    "zones": 2,
    "supported_effects": ["breathing", "rainbow", "wave"],
    "experimental": ["ambilight"],
}

LEGION_GO_S_HID = {
    "driver": "hid_legion_go_s",
    "color_order": "rgb",
    "zones": 2,
    "supported_effects": ["breathing", "rainbow", "wave"],
    "experimental": ["ambilight"],
}

GENERIC = {
    "driver": "sysfs",
    "color_order": "rgb",
    "zones": 0,
    "supported_effects": ["breathing", "rainbow", "wave", "cycle"],
    "experimental": ["color", "brightness", "effects", "ambilight"],
}


def _copy_profile(base):
    merged = dict(base)
    merged["experimental"] = list(base.get("experimental", []))
    return merged


def _profile(base, name):
    merged = _copy_profile(base)
    merged["name"] = name
    return merged


PROFILES = [
    ("board", "RC71L", _profile(ASUS_SYSFS, "ROG Ally")),
    ("board", "RC72LA", _profile(ASUS_SYSFS, "ROG Ally X")),
    ("board", "RC73YA", _profile(ASUS_SYSFS, "ROG Xbox Ally")),
    ("board", "RC73XA", _profile(ASUS_SYSFS, "ROG Xbox Ally X")),
    ("product", "83E1", _profile(LEGION_TABLET_HID, "Legion Go")),
    ("product", "83N0", _profile(LEGION_TABLET_HID, "Legion Go 2")),
    ("product", "83N1", _profile(LEGION_TABLET_HID, "Legion Go 2")),
    ("product", "83L3", _profile(LEGION_GO_S_HID, "Legion Go S")),
    ("product", "83Q2", _profile(LEGION_GO_S_HID, "Legion Go S")),
    ("product", "83N6", _profile(LEGION_GO_S_HID, "Legion Go S")),
    ("product", "83Q3", _profile(LEGION_GO_S_HID, "Legion Go S")),
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
