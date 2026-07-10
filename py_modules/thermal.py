import os

HWMON_ROOT = "/sys/class/hwmon"
THERMAL_ROOT = "/sys/class/thermal"

# AMD APU package (Tctl) first, then Intel package (MSI Claw), then the AMD GPU edge.
_HWMON_PREFERENCE = ("k10temp", "coretemp", "amdgpu")


def _read(path):
    try:
        with open(path, "r") as handle:
            return handle.read().strip()
    except OSError:
        return None


def _millidegrees(raw):
    if raw is None or not raw.lstrip("-").isdigit():
        return None
    return int(raw) / 1000.0


def _hwmon_chips(root):
    try:
        entries = os.listdir(root)
    except OSError:
        return {}
    chips = {}
    for name in sorted(entries):
        chip = os.path.join(root, name)
        label = _read(os.path.join(chip, "name"))
        if label:
            chips.setdefault(label, chip)
    return chips


def _thermal_zone_temp(root):
    try:
        entries = os.listdir(root)
    except OSError:
        return None
    best = None
    for name in entries:
        if not name.startswith("thermal_zone"):
            continue
        zone = os.path.join(root, name)
        value = _millidegrees(_read(os.path.join(zone, "temp")))
        if value is None:
            continue
        if "x86_pkg_temp" in (_read(os.path.join(zone, "type")) or ""):
            return value
        best = value if best is None else max(best, value)
    return best


def apu_temperature(hwmon_root=HWMON_ROOT, thermal_root=THERMAL_ROOT):
    chips = _hwmon_chips(hwmon_root)
    for pref in _HWMON_PREFERENCE:
        if pref in chips:
            value = _millidegrees(_read(os.path.join(chips[pref], "temp1_input")))
            if value is not None:
                return value
    zone = _thermal_zone_temp(thermal_root)
    if zone is not None:
        return zone
    for chip in chips.values():
        value = _millidegrees(_read(os.path.join(chip, "temp1_input")))
        if value is not None:
            return value
    return None


def temperature_available(hwmon_root=HWMON_ROOT, thermal_root=THERMAL_ROOT):
    return apu_temperature(hwmon_root, thermal_root) is not None
