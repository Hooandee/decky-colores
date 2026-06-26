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
    }


def _max_brightness(raw):
    return int(raw) if raw.isdigit() and int(raw) > 0 else 255


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
