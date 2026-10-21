FORMAT = "colores-styles"
VERSION = 1
NAME_MAX = 40
MAX_STYLES = 50

_TO_NEUTRAL = {"solid": "color", "vu": "audio", "ambient": "ambilight"}
_TO_DECKY = {neutral: decky for decky, neutral in _TO_NEUTRAL.items()}
MODES = ("color", "gradient", "effect", "ambilight", "audio", "battery", "temperature", "performance", "clock")


def _rgb(value):
    if isinstance(value, (list, tuple)) and len(value) == 3:
        value = {"r": value[0], "g": value[1], "b": value[2]}
    if not isinstance(value, dict):
        raise ValueError("invalid color")
    color = {}
    for channel in ("r", "g", "b"):
        part = value.get(channel)
        if isinstance(part, bool) or not isinstance(part, int) or not 0 <= part <= 255:
            raise ValueError("invalid color channel")
        color[channel] = part
    return color


def _int(value, low, high):
    if isinstance(value, bool) or not isinstance(value, int) or not low <= value <= high:
        raise ValueError("invalid number")
    return value


def _bool(value):
    if not isinstance(value, bool):
        raise ValueError("invalid flag")
    return value


def _colors(value, minimum):
    if not isinstance(value, list) or not minimum <= len(value) <= 32:
        raise ValueError("invalid color list")
    return [_rgb(color) for color in value]


def parse_style(raw):
    if not isinstance(raw, dict):
        raise ValueError("invalid style")
    name = raw.get("name")
    if not isinstance(name, str) or not name.strip() or len(name.strip()) > NAME_MAX:
        raise ValueError("invalid style name")
    style = {"name": name.strip()}
    if "mode" in raw:
        if raw["mode"] not in MODES:
            raise ValueError("invalid style mode")
        style["mode"] = raw["mode"]
    if "colors" in raw:
        style["colors"] = _colors(raw["colors"], 1)
    if "gradient" in raw:
        style["gradient"] = _colors(raw["gradient"], 2)
    if "gradientSpeed" in raw:
        style["gradientSpeed"] = _int(raw["gradientSpeed"], 0, 100)
    if "brightness" in raw:
        style["brightness"] = _int(raw["brightness"], 0, 100)
    if "effect" in raw:
        effect = raw["effect"]
        if not isinstance(effect, dict) or not isinstance(effect.get("id"), str) or not effect["id"]:
            raise ValueError("invalid style effect")
        style["effect"] = {
            "id": effect["id"],
            "speed": _int(effect.get("speed", 50), 0, 100),
            "useGradient": _bool(effect.get("useGradient", False)),
        }
    if "ambilight" in raw:
        ambilight = raw["ambilight"]
        if not isinstance(ambilight, dict):
            raise ValueError("invalid style ambilight")
        style["ambilight"] = {
            key: (_bool(ambilight[key]) if key == "audio" else _int(ambilight[key], 0, 100))
            for key in ("vividness", "smoothing", "audio")
            if key in ambilight
        }
    for key in ("batteryBreathe", "temperatureBreathe"):
        if key in raw:
            style[key] = _bool(raw[key])
    return style


def from_profile(name, profile):
    effect = profile["effect"]
    ambilight = profile["ambilight"]
    return parse_style(
        {
            "name": name,
            "mode": _TO_NEUTRAL.get(profile["mode"], profile["mode"]),
            "colors": [_rgb(profile["color"])],
            "gradient": [_rgb(color) for color in profile["gradient"]],
            "gradientSpeed": profile["gradient_speed"],
            "brightness": profile["brightness"],
            "effect": {"id": effect["id"], "speed": effect["speed"], "useGradient": bool(effect.get("use_gradient", False))},
            "ambilight": {
                "vividness": ambilight["vividness"],
                "smoothing": ambilight["smoothing"],
                "audio": bool(ambilight.get("audio", False)),
            },
            "batteryBreathe": bool(profile.get("battery_breathe", True)),
            "temperatureBreathe": bool(profile.get("temperature_breathe", True)),
        }
    )


def to_profile_changes(style):
    changes = {}
    if "mode" in style:
        changes["mode"] = _TO_DECKY.get(style["mode"], style["mode"])
    if "colors" in style:
        first = style["colors"][0]
        changes["color"] = [first["r"], first["g"], first["b"]]
    if "gradient" in style:
        changes["gradient"] = [[color["r"], color["g"], color["b"]] for color in style["gradient"]]
    if "gradientSpeed" in style:
        changes["gradient_speed"] = style["gradientSpeed"]
    if "brightness" in style:
        changes["brightness"] = style["brightness"]
    if "effect" in style:
        effect = style["effect"]
        changes["effect"] = {"id": effect["id"], "speed": effect["speed"], "use_gradient": effect["useGradient"]}
    if "ambilight" in style:
        changes["ambilight"] = dict(style["ambilight"])
    if "batteryBreathe" in style:
        changes["battery_breathe"] = style["batteryBreathe"]
    if "temperatureBreathe" in style:
        changes["temperature_breathe"] = style["temperatureBreathe"]
    return changes


def _same(a, b):
    return a.casefold() == b.casefold()


def merge_styles(current, incoming):
    merged = list(current)
    added = replaced = 0
    for style in incoming:
        index = next((i for i, existing in enumerate(merged) if _same(existing["name"], style["name"])), None)
        if index is None:
            if len(merged) >= MAX_STYLES:
                continue
            merged.append(style)
            added += 1
        else:
            merged[index] = style
            replaced += 1
    return merged, added, replaced


def remove_style(current, name):
    return [style for style in current if not _same(style["name"], name)]


def export_document(styles):
    return {"format": FORMAT, "version": VERSION, "styles": list(styles)}


def import_document(document):
    if not isinstance(document, dict) or document.get("format") != FORMAT:
        raise ValueError("not a Colores styles file")
    version = document.get("version")
    if isinstance(version, bool) or not isinstance(version, int) or not 1 <= version <= VERSION:
        raise ValueError("unsupported styles version")
    entries = document.get("styles")
    if not isinstance(entries, list):
        raise ValueError("invalid styles list")
    imported = []
    skipped = 0
    for entry in entries:
        try:
            imported.append(parse_style(entry))
        except ValueError:
            skipped += 1
    return imported, skipped
