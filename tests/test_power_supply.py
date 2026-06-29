import os

from py_modules.power_supply import charger_online


def _supply(root, name, kind, online=None):
    path = os.path.join(root, name)
    os.makedirs(path)
    with open(os.path.join(path, "type"), "w") as handle:
        handle.write(kind + "\n")
    if online is not None:
        with open(os.path.join(path, "online"), "w") as handle:
            handle.write(online + "\n")


def test_plugged_in_when_mains_online(tmp_path):
    root = str(tmp_path)
    _supply(root, "BAT0", "Battery")
    _supply(root, "ADP0", "Mains", "1")
    assert charger_online(root) is True


def test_on_battery_when_mains_offline(tmp_path):
    root = str(tmp_path)
    _supply(root, "BAT0", "Battery")
    _supply(root, "ADP0", "Mains", "0")
    assert charger_online(root) is False


def test_usb_pd_adapter_counts_as_charger(tmp_path):
    root = str(tmp_path)
    _supply(root, "ucsi-source", "USB", "1")
    assert charger_online(root) is True


def test_any_adapter_online_wins(tmp_path):
    root = str(tmp_path)
    _supply(root, "ADP0", "Mains", "0")
    _supply(root, "ucsi-source", "USB", "1")
    assert charger_online(root) is True


def test_battery_only_node_is_ignored(tmp_path):
    # A device that reports only a battery (no adapter node) must assume plugged,
    # never strand its LEDs off.
    root = str(tmp_path)
    _supply(root, "BAT0", "Battery")
    assert charger_online(root) is True


def test_missing_root_assumes_plugged(tmp_path):
    assert charger_online(os.path.join(str(tmp_path), "nope")) is True
