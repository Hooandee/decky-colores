import os

from device_profiles import resolve_profile
from led_device import SysfsRgbDevice, NullDevice, ValveLedsDevice, discover_valve_leds, IndicatorLed
from hid_adapters import HID_AVAILABLE, HID_DRIVERS, build_hid_device
from power_led import PowerLedController
from power_supply import battery_present
from thermal import temperature_available

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
    ("product", "83N6", "Legion Go S"),
    ("product", "83Q3", "Legion Go S"),
    ("product", "83N0", "Legion Go 2"),
    ("product", "83N1", "Legion Go 2"),
    ("board", "Fremont", "Steam Machine"),
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


def build_layout(zones, swap_sticks=False, layout_kind="rings"):
    if zones <= 0:
        return []
    if layout_kind == "bar":
        return [{"name": "Bar", "region": [0.0, 0.0, 1.0, 1.0], "zones": list(range(zones)), "kind": "bar"}]
    if zones == 1:
        return [{"name": "Lights", "region": [0.0, 0.0, 1.0, 1.0], "zones": [0]}]
    groups = list(reversed(_STICK_ANCHORS)) if swap_sticks else _STICK_ANCHORS
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


def _all_experimental(profile):
    return sorted(set(profile.get("experimental", [])) | set(FEATURES))


def _feature_state(profile, feature, present):
    if feature in profile.get("experimental", []):
        return "experimental"
    return "supported" if present else "unsupported"


def build_capabilities(profile, has_led, zones, max_brightness, ambilight, power_led=None, battery=False, temperature=False):
    present = {
        "color": has_led,
        "brightness": has_led,
        "effects": has_led,
        "ambilight": bool(ambilight),
    }
    states = {f: _feature_state(profile, f, present[f]) for f in FEATURES}
    active = {f: states[f] != "unsupported" for f in FEATURES}
    return {
        "color": active["color"],
        "brightness": active["brightness"],
        "effects": active["effects"],
        "ambilight": active["ambilight"],
        "zones": zones,
        "maxBrightness": max_brightness,
        "perZone": has_led and zones > 1,
        "hardwareEffects": False,
        "reconnectable": False,
        "perControllerColor": bool(profile.get("per_controller", False)),
        "gradientCrossfade": bool(profile.get("gradient_crossfade", False)),
        "supportedEffects": list(profile.get("supported_effects", [])),
        "states": states,
        "experimental": list(profile.get("experimental", [])),
        "powerLed": bool(power_led and power_led.available()),
        "hasBattery": bool(battery),
        "batteryMode": bool(battery) and active["color"],
        "temperatureMode": bool(temperature) and active["color"],
        "performanceMode": active["color"],
        "clockMode": active["color"],
        "audioMode": active["color"],
        "conflictsWithSystemRgb": bool(profile.get("conflicts_with_system_rgb", False)),
        "indicatorLed": bool(profile.get("indicator_led", False)),
        "persistentStartup": bool(profile.get("persistent_startup", False)),
        "layoutKind": profile.get("layout_kind", "rings"),
        "layout": build_layout(zones, profile.get("swap_sticks", False), profile.get("layout_kind", "rings")),
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


def find_indicator_led(leds_dir):
    try:
        entries = os.listdir(leds_dir)
    except OSError:
        return None
    for name in sorted(entries):
        path = os.path.join(leds_dir, name)
        if (
            "status" in name
            and os.path.exists(os.path.join(path, "brightness"))
            and not os.path.exists(os.path.join(path, "multi_intensity"))
        ):
            return path
    return None


_IMPLEMENTED_DRIVERS = {"sysfs", "hid_msi", "hid_legion_tablet", "hid_legion_go_s", "hid_asus_ally", "valve_leds"}


def _build_hid_context(profile, ambilight, power_led=None, battery=False, temperature=False):
    device = build_hid_device(profile["driver"])
    if device is None or not device.available:
        return None
    correction = profile.get("color_correction")
    if correction and hasattr(device, "set_color_correction"):
        device.set_color_correction(correction)
    zones = profile.get("zones") or 1
    capabilities = build_capabilities(profile, True, zones, 100, ambilight, power_led, battery, temperature)
    capabilities["perZone"] = device.supports_per_zone()
    capabilities["hardwareEffects"] = device.supports_hardware_effects()
    capabilities["reconnectable"] = True
    return {"device": device, "capabilities": capabilities}


def _build_valve_context(profile, sysfs_root, ambilight, power_led=None, battery=False, temperature=False):
    leds_dir = os.path.join(sysfs_root, "sys/class/leds")
    nodes = discover_valve_leds(leds_dir)
    if not nodes:
        return None
    max_brightness = _max_brightness(_read(os.path.join(nodes[0], "max_brightness")))
    device = ValveLedsDevice(
        nodes, max_brightness, profile.get("color_correction", [1.0, 1.0, 1.0]),
        reverse=profile.get("reverse_zones", False),
    )
    indicator = None
    if profile.get("indicator_led"):
        path = find_indicator_led(leds_dir)
        if path:
            ind_max = _max_brightness(_read(os.path.join(path, "max_brightness")))
            indicator = IndicatorLed(path, ind_max)
    capabilities = build_capabilities(
        profile, True, len(nodes), max_brightness, ambilight, power_led, battery, temperature
    )
    capabilities["indicatorLed"] = bool(indicator and indicator.available())
    return {"device": device, "capabilities": capabilities, "indicator": indicator}


def build_device(sysfs_root="/", ambilight=False):
    info = detect_device(sysfs_root)
    profile = resolve_profile(info["board"], info["product"])
    info["name"] = profile["name"]
    power_led = PowerLedController(profile.get("power_led"))
    battery = battery_present(os.path.join(sysfs_root, "sys/class/power_supply"))
    temperature = temperature_available(
        os.path.join(sysfs_root, "sys/class/hwmon"),
        os.path.join(sysfs_root, "sys/class/thermal"),
    )

    if profile["driver"] in HID_DRIVERS:
        if HID_AVAILABLE:
            hid_ctx = _build_hid_context(profile, ambilight, power_led, battery, temperature)
            if hid_ctx is not None:
                return {
                    "info": info,
                    "capabilities": hid_ctx["capabilities"],
                    "device": hid_ctx["device"],
                    "power_led": power_led,
                }
        profile["experimental"] = _all_experimental(profile)

    if profile["driver"] == "valve_leds":
        valve_ctx = _build_valve_context(profile, sysfs_root, ambilight, power_led, battery, temperature)
        if valve_ctx is not None:
            return {
                "info": info,
                "capabilities": valve_ctx["capabilities"],
                "device": valve_ctx["device"],
                "power_led": power_led,
                "indicator": valve_ctx.get("indicator"),
            }
        profile["experimental"] = _all_experimental(profile)

    leds_dir = os.path.join(sysfs_root, "sys/class/leds")
    led_path = _find_rgb_led(leds_dir)

    if led_path:
        zones, index_format = read_zone_format(led_path)
        if profile.get("zones"):
            zones = profile["zones"]
        max_brightness = _max_brightness(_read(os.path.join(led_path, "max_brightness")))
        device = SysfsRgbDevice(
            led_path, zones, max_brightness, profile["color_order"], index_format,
            color_correction=profile.get("color_correction", [1.0, 1.0, 1.0]),
        )
        has_led = True
    else:
        fallback = profile.get("fallback")
        if fallback and HID_AVAILABLE and fallback.get("driver") in HID_DRIVERS:
            fb_profile = dict(fallback)
            fb_profile["name"] = profile["name"]
            hid_ctx = _build_hid_context(fb_profile, ambilight, power_led, battery, temperature)
            if hid_ctx is not None:
                return {
                    "info": info,
                    "capabilities": hid_ctx["capabilities"],
                    "device": hid_ctx["device"],
                    "power_led": power_led,
                }
        zones, max_brightness, device, has_led = 0, 255, NullDevice(), False

    if profile["driver"] not in _IMPLEMENTED_DRIVERS:
        profile["experimental"] = _all_experimental(profile)

    capabilities = build_capabilities(profile, has_led, zones, max_brightness, ambilight, power_led, battery, temperature)
    return {
        "info": info,
        "capabilities": capabilities,
        "device": device,
        "power_led": power_led,
    }
