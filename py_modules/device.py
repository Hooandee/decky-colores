import os

DEVICE_REGISTRY = [
    ("board", "Jupiter", "Steam Deck"),
    ("board", "Galileo", "Steam Deck OLED"),
    ("board", "RC71L", "ROG Ally"),
    ("board", "RC72LA", "ROG Ally X"),
    ("board", "RC73YA", "ROG Xbox Ally"),
    ("board", "RC73XA", "ROG Xbox Ally X"),
    ("product", "83E1", "Legion Go"),
    ("product", "83L3", "Legion Go S"),
    ("product", "83Q2", "Legion Go S"),
    ("product", "83N0", "Legion Go 2"),
    ("product", "83N1", "Legion Go 2"),
]


def _read(path):
    try:
        with open(path) as handle:
            return handle.read().strip()
    except OSError:
        return ""


def lookup_name(board, product):
    for field, value, name in DEVICE_REGISTRY:
        candidate = board if field == "board" else product
        if value == candidate:
            return name
    return product or board or "Unknown device"


def detect_device(sysfs_root="/"):
    dmi = os.path.join(sysfs_root, "sys/class/dmi/id")
    board = _read(os.path.join(dmi, "board_name"))
    product = _read(os.path.join(dmi, "product_name"))
    return {
        "name": lookup_name(board, product),
        "board": board,
        "product": product,
    }


def detect_capabilities(sysfs_root="/"):
    leds_dir = os.path.join(sysfs_root, "sys/class/leds")
    led_path = _find_rgb_led(leds_dir)

    if led_path is None:
        return {
            "color": False,
            "brightness": False,
            "zones": 0,
            "maxBrightness": 0,
            "ledPath": None,
            "layout": [],
        }

    zones = 1
    multi_index = _read(os.path.join(led_path, "multi_index"))
    if multi_index:
        zones = len(multi_index.split())

    max_brightness = _read(os.path.join(led_path, "max_brightness"))
    has_color = os.path.exists(os.path.join(led_path, "multi_intensity"))

    return {
        "color": has_color,
        "brightness": True,
        "zones": zones,
        "maxBrightness": _max_brightness(max_brightness),
        "ledPath": led_path,
        "layout": build_layout(zones),
    }


def _max_brightness(raw):
    return int(raw) if raw.isdigit() and int(raw) > 0 else 255


# Per-stick screen anchors (normalized x0, y0, x1, y1) used for Ambilight sampling.
# Layout describes how flat LED zones group into physical joysticks; each group's
# zones are sampled across its screen region (one sub-region per LED for richness).
_STICK_ANCHORS = [
    ("Left stick", [0.0, 0.0, 0.30, 0.35]),
    ("Right stick", [0.70, 0.33, 1.0, 0.67]),
]


def build_layout(zones):
    if zones <= 0:
        return []
    if zones == 1:
        return [{"name": "Lights", "region": [0.0, 0.0, 1.0, 1.0], "zones": [0]}]
    groups = _STICK_ANCHORS
    base, extra = divmod(zones, len(groups))
    layout = []
    index = 0
    for i, (name, region) in enumerate(groups):
        count = base + (1 if i < extra else 0)
        if count == 0:
            continue
        layout.append({"name": name, "region": list(region), "zones": list(range(index, index + count))})
        index += count
    return layout


_CHANNEL_NAMES = {"red", "green", "blue"}

FEATURES = ("color", "brightness", "effects", "ambilight")


def read_zone_format(led_path):
    multi_index = _read(os.path.join(led_path, "multi_index"))
    tokens = multi_index.split()
    if tokens and all(token.lower() in _CHANNEL_NAMES for token in tokens):
        return max(1, len(tokens) // 3), "decimal"
    return max(1, len(tokens)), "hex"


def _feature_state(profile, feature, present):
    if feature in profile.get("experimental", []):
        return "experimental"
    return "supported" if present else "unsupported"


def build_capabilities(profile, has_led, zones, max_brightness, ambilight):
    present = {
        "color": has_led,
        "brightness": has_led,
        "effects": has_led,
        "ambilight": bool(ambilight),
    }
    states = {f: _feature_state(profile, f, present[f]) for f in FEATURES}
    active = {f: states[f] in ("supported", "experimental") for f in FEATURES}
    return {
        "color": active["color"],
        "brightness": active["brightness"],
        "effects": active["effects"],
        "ambilight": active["ambilight"],
        "zones": zones,
        "maxBrightness": max_brightness,
        "perZone": has_led and zones > 1,
        "supportedEffects": list(profile.get("supported_effects", [])),
        "states": states,
        "experimental": list(profile.get("experimental", [])),
        "layout": build_layout(zones),
    }


def _find_rgb_led(leds_dir):
    if not os.path.isdir(leds_dir):
        return None

    try:
        entries = os.listdir(leds_dir)
    except OSError:
        return None

    candidates = sorted(entries, key=lambda c: "rgb" not in c.lower())
    for name in candidates:
        path = os.path.join(leds_dir, name)
        if os.path.exists(os.path.join(path, "multi_intensity")):
            return path
    return None
