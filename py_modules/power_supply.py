import os

POWER_SUPPLY_ROOT = "/sys/class/power_supply"
_ADAPTER_TYPES = ("Mains", "USB")
_BATTERY_TYPE = "Battery"


def _read(path):
    try:
        with open(path, "r") as handle:
            return handle.read().strip()
    except OSError:
        return None


def charger_online(root=POWER_SUPPLY_ROOT):
    # Universal across handhelds: the AC/USB adapter exposes `online` (1 = plugged)
    # in the standard Linux power_supply class, independent of the LED driver. If
    # nothing can be read (no adapter node, permission error), assume plugged so the
    # gate never strands a device with its LEDs dark by mistake.
    try:
        entries = os.listdir(root)
    except OSError:
        return True
    saw_adapter = False
    for name in entries:
        supply = os.path.join(root, name)
        if _read(os.path.join(supply, "type")) not in _ADAPTER_TYPES:
            continue
        online = _read(os.path.join(supply, "online"))
        if online is None:
            continue
        if online == "1":
            return True
        saw_adapter = True
    return not saw_adapter


def _batteries(root):
    try:
        entries = os.listdir(root)
    except OSError:
        return []
    result = []
    for name in entries:
        supply = os.path.join(root, name)
        if _read(os.path.join(supply, "type")) == _BATTERY_TYPE:
            result.append(supply)
    return result


def battery_present(root=POWER_SUPPLY_ROOT):
    # A device qualifies for the battery-level mode when it exposes a battery node
    # with a readable `capacity`. Universal across handhelds (standard Linux
    # power_supply class), independent of the LED driver.
    for supply in _batteries(root):
        if _read(os.path.join(supply, "capacity")) is not None:
            return True
    return False


def battery_level(root=POWER_SUPPLY_ROOT):
    # Current charge as a 0-100 int, or None if unreadable. When several batteries
    # exist (rare on handhelds) average them. Kept cheap: called on a coarse poll.
    levels = []
    for supply in _batteries(root):
        raw = _read(os.path.join(supply, "capacity"))
        if raw is not None and raw.lstrip("-").isdigit():
            levels.append(max(0, min(100, int(raw))))
    if not levels:
        return None
    return int(round(sum(levels) / len(levels)))
