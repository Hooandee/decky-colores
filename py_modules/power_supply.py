import os

POWER_SUPPLY_ROOT = "/sys/class/power_supply"
_ADAPTER_TYPES = ("Mains", "USB")


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
