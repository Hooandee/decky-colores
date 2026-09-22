import os

from device_profiles import profile_for_discovered_adapter, profile_for_hid_signatures, resolve_profile_match
from led_device import (
    ApexRgbDevice,
    HpOmenRgbDevice,
    MultiSysfsRgbDevice,
    NullDevice,
    PORTAL_LED_NAMES,
    SysfsRgbDevice,
    ValveLedsDevice,
    discover_portal_leds,
    discover_valve_leds,
)
from hid_adapters import HID_AVAILABLE, HID_DRIVERS, build_hid_device, discover_hid_drivers
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
    ("product", "ONEXPLAYER APEX", "OneXPlayer OneXFly Apex"),
    ("product", "ONEXPLAYER F1Pro", "OneXPlayer OneXFly F1 Pro"),
]


def _read(path):
    try:
        with open(path) as handle:
            return handle.read().strip().strip("\x00")
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
    vendor = _read(os.path.join(dmi, "sys_vendor"))
    model = product or _read(os.path.join(sysfs_root, "sys/firmware/devicetree/base/model"))
    if not model:
        model = _read(os.path.join(sysfs_root, "proc/device-tree/model"))
    return {
        "name": lookup_name(board, product) if product or board else model or "Unknown device",
        "board": board,
        "product": product,
        "vendor": vendor,
        "model": model,
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
    if layout_kind == "uniform":
        return [{"name": "Lights", "region": [0.0, 0.0, 1.0, 1.0], "zones": list(range(zones)), "kind": "uniform"}]
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
_PACKED_COLOR_NAME = "rgb"
_ALLY_RGB_NODE = "ally:rgb:joystick_rings"

FEATURES = ("color", "brightness", "effects", "ambilight")


def read_zone_format(led_path):
    multi_index = _read(os.path.join(led_path, "multi_index"))
    tokens = multi_index.split()
    if tokens and all(token.lower() in _CHANNEL_NAMES for token in tokens):
        return max(1, len(tokens) // 3), "decimal"
    if os.path.basename(led_path) == _ALLY_RGB_NODE and tokens and all(
        token.lower() == _PACKED_COLOR_NAME for token in tokens
    ):
        return len(tokens), "packed_decimal"
    return max(1, len(tokens)), "hex"


def _all_experimental(profile):
    return sorted(set(profile.get("experimental", [])) | set(FEATURES))


def _feature_state(profile, feature, present):
    if not present:
        return "unsupported"
    if feature in profile.get("experimental", []):
        return "experimental"
    return "supported"


def build_capabilities(profile, has_led, zones, max_brightness, ambilight, power_led=None, battery=False, temperature=False):
    present = {
        "color": has_led,
        "brightness": has_led and profile.get("brightness", True),
        "effects": has_led,
        "ambilight": bool(ambilight),
    }
    states = {f: _feature_state(profile, f, present[f]) for f in FEATURES}
    active = {f: states[f] != "unsupported" for f in FEATURES}
    power_led_available = bool(power_led and power_led.available())
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
        "powerLed": power_led_available,
        "powerLedSeparateStates": bool(
            power_led_available and power_led.supports_independent_states()
        ),
        "sleepChargingIndicator": False,
        "hasBattery": bool(battery),
        "batteryMode": bool(battery) and active["color"],
        "temperatureMode": bool(temperature) and active["color"],
        "performanceMode": active["color"],
        "clockMode": active["color"],
        "audioMode": active["color"],
        "conflictsWithSystemRgb": bool(profile.get("conflicts_with_system_rgb", False)),
        "hhdRgbTakeover": bool(profile.get("hhd_rgb_takeover", False)),
        "persistentStartup": bool(profile.get("persistent_startup", False)),
        "maxRenderFps": int(profile.get("max_render_fps", 30)),
        "layoutKind": profile.get("layout_kind", "rings"),
        "layout": build_layout(zones, profile.get("swap_sticks", False), profile.get("layout_kind", "rings")),
    }


def _find_rgb_led(leds_dir, required_name=None, allow_packed=False):
    if not os.path.isdir(leds_dir):
        return None

    if required_name:
        path = os.path.join(leds_dir, required_name)
        return path if os.path.exists(os.path.join(path, "multi_intensity")) else None

    try:
        entries = os.listdir(leds_dir)
    except OSError:
        return None

    candidates = sorted(entries, key=lambda c: ("rgb" not in c.lower(), c.lower()))
    for name in candidates:
        if name in PORTAL_LED_NAMES:
            continue
        path = os.path.join(leds_dir, name)
        if os.path.exists(os.path.join(path, "multi_intensity")) and _valid_rgb_schema(
            path, allow_packed=allow_packed
        ):
            return path
    return None


def _valid_rgb_schema(led_path, allow_packed=False):
    tokens = _read(os.path.join(led_path, "multi_index")).lower().split()
    if tokens and all(token == _PACKED_COLOR_NAME for token in tokens):
        return allow_packed or os.path.basename(led_path) == _ALLY_RGB_NODE
    if not tokens or len(tokens) % 3:
        return False
    groups = [tokens[index : index + 3] for index in range(0, len(tokens), 3)]
    return all(set(group) == _CHANNEL_NAMES and group == groups[0] for group in groups)


def _rgb_channel_order(led_path, default="rgb"):
    tokens = _read(os.path.join(led_path, "multi_index")).lower().split()
    if len(tokens) >= 3 and set(tokens[:3]) == _CHANNEL_NAMES:
        return "".join(token[0] for token in tokens[:3])
    return default


_IMPLEMENTED_DRIVERS = {
    "sysfs",
    "hid_msi",
    "hid_legion_go",
    "hid_legion_go_s",
    "hid_asus_ally",
    "valve_leds",
    "multi_sysfs",
    "hp_omen_platform",
}


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
    capabilities = build_capabilities(
        profile, True, len(nodes), max_brightness, ambilight, power_led, battery, temperature
    )
    return {"device": device, "capabilities": capabilities}


def _with_sleep_charging(profile, context):
    driver = profile.get("sleep_charging")
    controller = context["device"]
    supports = getattr(controller, "supports_sleep_charging_indicator", None)
    if not driver:
        context["capabilities"]["sleepChargingIndicator"] = False
        return context
    if not (callable(supports) and supports()):
        controller = build_hid_device(driver) if HID_AVAILABLE else None
        supports = getattr(controller, "supports_sleep_charging_indicator", None)
    available = bool(
        controller
        and callable(supports)
        and supports()
        and controller.available
    )
    context["capabilities"]["sleepChargingIndicator"] = available
    context["sleep_charging_controller"] = controller
    return context


def build_device(sysfs_root="/", ambilight=False):
    info = detect_device(sysfs_root)
    profile, matched = resolve_profile_match(info["board"], info["product"])
    hid_drivers = discover_hid_drivers() if HID_AVAILABLE else set()
    hid_profile = profile_for_hid_signatures(info, hid_drivers)
    if not matched and hid_profile is not None:
        profile = hid_profile
    elif hid_profile is not None and profile.get("fallback", {}).get("driver") == "hid_oxp_v2":
        profile["fallback"]["driver"] = hid_profile["driver"]
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
                return _with_sleep_charging(profile, {
                    "info": info,
                    "capabilities": hid_ctx["capabilities"],
                    "device": hid_ctx["device"],
                    "power_led": power_led,
                })
        profile["experimental"] = _all_experimental(profile)

    if profile["driver"] == "valve_leds":
        valve_ctx = _build_valve_context(profile, sysfs_root, ambilight, power_led, battery, temperature)
        if valve_ctx is not None:
            return _with_sleep_charging(profile, {
                "info": info,
                "capabilities": valve_ctx["capabilities"],
                "device": valve_ctx["device"],
                "power_led": power_led,
            })
        profile["experimental"] = _all_experimental(profile)

    leds_dir = os.path.join(sysfs_root, "sys/class/leds")
    portal_nodes = discover_portal_leds(leds_dir) if not matched else []
    if portal_nodes:
        portal_name = info.get("model") or "Multizone RGB device"
        profile = profile_for_discovered_adapter("portal_sysfs", portal_name)
        info["name"] = profile["name"]
        device = MultiSysfsRgbDevice(portal_nodes, color_order=profile["color_order"])
        capabilities = build_capabilities(
            profile, device.available, profile["zones"], 255, ambilight,
            power_led, battery, temperature,
        )
        capabilities["perZone"] = device.supports_per_zone()
        return _with_sleep_charging(profile, {
            "info": info,
            "capabilities": capabilities,
            "device": device,
            "power_led": power_led,
        })

    identity = " ".join(
        str(info.get(field) or "") for field in ("vendor", "product", "board", "model")
    ).lower()
    omen_path = os.path.join(sysfs_root, "sys/devices/platform/hp-rgb-lighting")
    omen_device = HpOmenRgbDevice(omen_path) if not matched and "omen" in identity else None
    if omen_device is not None and omen_device.available:
        profile = profile_for_discovered_adapter(
            "hp_omen_platform", info.get("product") or info.get("model") or "HP OMEN"
        )
        info["name"] = profile["name"]
        capabilities = build_capabilities(
            profile, True, profile["zones"], 1, ambilight,
            power_led, battery, temperature,
        )
        capabilities["perZone"] = omen_device.supports_per_zone()
        return _with_sleep_charging(profile, {
            "info": info,
            "capabilities": capabilities,
            "device": omen_device,
            "power_led": power_led,
        })

    led_path = (
        _find_rgb_led(
            leds_dir,
            profile.get("led_name"),
            allow_packed=matched,
        )
        if profile.get("allow_sysfs_fallback", True)
        else None
    )

    if led_path:
        zones, index_format = read_zone_format(led_path)
        if not matched:
            profile["color_order"] = _rgb_channel_order(led_path, profile["color_order"])
            profile["experimental"] = []
            profile["layout_kind"] = "uniform"
            profile["per_zone"] = False
        if profile.get("zones"):
            zones = profile["zones"]
        max_brightness = _max_brightness(_read(os.path.join(led_path, "max_brightness")))
        device = SysfsRgbDevice(
            led_path, zones, max_brightness, profile["color_order"], index_format,
            color_correction=profile.get("color_correction", [1.0, 1.0, 1.0]),
            latch=profile.get("latch"),
            per_zone=profile.get("per_zone", True),
        )
        has_led = device.available
        if has_led and profile.get("prefer_hid") and HID_AVAILABLE:
            fallback = profile.get("fallback") or {}
            hid_device = build_hid_device(fallback.get("driver"))
            if hid_device is not None:
                correction = fallback.get("color_correction")
                if correction and hasattr(hid_device, "set_color_correction"):
                    hid_device.set_color_correction(correction)
                device = ApexRgbDevice(hid_device, device)
        if not has_led:
            led_path = None

    if not led_path:
        fallback = profile.get("fallback")
        if fallback and HID_AVAILABLE and fallback.get("driver") in HID_DRIVERS:
            fb_profile = dict(fallback)
            fb_profile["name"] = profile["name"]
            hid_ctx = _build_hid_context(fb_profile, ambilight, power_led, battery, temperature)
            if hid_ctx is not None:
                return _with_sleep_charging(profile, {
                    "info": info,
                    "capabilities": hid_ctx["capabilities"],
                    "device": hid_ctx["device"],
                    "power_led": power_led,
                })
        zones, max_brightness, device, has_led = 0, 255, NullDevice(), False

    if profile["driver"] not in _IMPLEMENTED_DRIVERS:
        profile["experimental"] = _all_experimental(profile)

    capabilities = build_capabilities(profile, has_led, zones, max_brightness, ambilight, power_led, battery, temperature)
    capabilities["perZone"] = bool(
        has_led and zones > 1 and profile.get("per_zone", device.supports_per_zone())
    )
    if isinstance(device, ApexRgbDevice):
        capabilities["reconnectable"] = True
    return _with_sleep_charging(profile, {
        "info": info,
        "capabilities": capabilities,
        "device": device,
        "power_led": power_led,
    })
