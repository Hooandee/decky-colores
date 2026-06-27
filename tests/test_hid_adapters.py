import importlib
import sys
import types

import pytest


def _install_fake_lib_hid(devices=None, writes=None):
    devices = devices if devices is not None else []
    writes = writes if writes is not None else []

    fake = types.ModuleType("lib_hid")

    class Device:
        def __init__(self, path=None):
            self.path = path

        def write(self, data):
            writes.append(bytes(data))
            return len(data)

        def close(self):
            pass

    def enumerate(vid=0, pid=0):
        return list(devices)

    fake.Device = Device
    fake.enumerate = enumerate
    sys.modules["lib_hid"] = fake
    return writes


def _reload_adapters():
    for name in (
        "msi_led_device_hid",
        "legion_go_tablet_hid",
        "hhd_legino_go_s_hid",
        "legion_led_device_hid",
        "hid_adapters",
    ):
        sys.modules.pop(name, None)
    import hid_adapters

    return importlib.reload(hid_adapters)


@pytest.fixture
def hid_env():
    saved = {
        name: sys.modules.get(name)
        for name in (
            "lib_hid",
            "msi_led_device_hid",
            "legion_go_tablet_hid",
            "hhd_legino_go_s_hid",
            "legion_led_device_hid",
            "hid_adapters",
            "device",
        )
    }
    writes = _install_fake_lib_hid()
    adapters = _reload_adapters()
    yield adapters, writes
    for name, mod in saved.items():
        if mod is None:
            sys.modules.pop(name, None)
        else:
            sys.modules[name] = mod


def _msi_device_entry():
    return {
        "vendor_id": 0x0DB0,
        "product_id": 0x1901,
        "usage_page": 0xFFA0,
        "usage": 0x0001,
        "interface_number": 0,
        "path": b"msi",
        "release_number": 0x0163,
    }


def _legion_tablet_entry():
    return {
        "vendor_id": 0x17EF,
        "product_id": 0x6182,
        "usage_page": 0xFFA0,
        "usage": 0x0001,
        "interface_number": 0,
        "path": b"tab",
    }


def _legion_go_s_entry():
    return {
        "vendor_id": 0x1A86,
        "product_id": 0xE310,
        "usage_page": 0xFFA0,
        "usage": 0x0001,
        "interface_number": 3,
        "path": b"gos",
    }


def test_hid_available_with_fake_lib(hid_env):
    adapters, _ = hid_env
    assert adapters.HID_AVAILABLE is True


def test_msi_solid_color_bytes(hid_env):
    adapters, writes = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_msi_device_entry()]
    dev = adapters.MsiHidDevice.create()
    assert dev.available is True
    assert dev.supports_per_zone() is True
    writes.clear()
    assert dev.apply_solid((255, 0, 0), 100, True) is True
    assert len(writes) == 1
    packet = writes[0]
    expected = bytes.fromhex(
        "0f00003c210101fa200001090364ff0000ff0000ff0000ff0000ff0000ff0000ff0000ff0000ff0000"
    )
    assert packet == expected


def test_msi_per_zone_uses_distinct_zones(hid_env):
    adapters, writes = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_msi_device_entry()]
    dev = adapters.MsiHidDevice.create()
    writes.clear()
    zone_colors = [(i, 0, 0) for i in range(9)]
    assert dev.apply_zones(zone_colors, 100, True) is True
    packet = writes[0]
    payload = packet[14:]
    triples = [tuple(payload[i : i + 3]) for i in range(0, len(payload), 3)]
    assert triples == zone_colors


def test_legion_tablet_solid_sequence(hid_env):
    adapters, writes = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_legion_tablet_entry()]
    dev = adapters.LegionTabletHidDevice.create()
    assert dev.available is True
    assert dev.supports_per_zone() is False
    writes.clear()
    assert dev.apply_solid((255, 0, 0), 100, True) is True
    assert [w.hex() for w in writes] == [
        "050c72010301ff00003f150301",
        "050c72010401ff00003f150301",
        "05067302030301",
        "05067302040301",
        "05067002030101",
        "05067002040101",
    ]


def test_legion_tablet_zones_map_left_to_first(hid_env):
    adapters, writes = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_legion_tablet_entry()]
    dev = adapters.LegionTabletHidDevice.create()
    writes.clear()
    dev.apply_zones([(255, 0, 0), (0, 255, 0)], 100, True)
    left = next(w for w in writes if len(w) >= 6 and w[2] == 0x72 and w[4] == 0x03)
    assert left[6:9] == bytes([255, 0, 0])


def test_legion_tablet_per_controller_gradient(hid_env):
    adapters, writes = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_legion_tablet_entry()]
    dev = adapters.LegionTabletHidDevice.create()
    writes.clear()
    assert dev.apply_zones([(255, 0, 0), (0, 0, 255)], 100, True) is True
    set_profiles = [w for w in writes if len(w) >= 6 and w[2] == 0x72]
    left = next(w for w in set_profiles if w[4] == 0x03)
    right = next(w for w in set_profiles if w[4] == 0x04)
    assert left[6:9] == bytes([255, 0, 0])
    assert right[6:9] == bytes([0, 0, 255])


def test_legion_go_s_solid_bytes(hid_env):
    adapters, writes = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_legion_go_s_entry()]
    dev = adapters.LegionGoSHidDevice.create()
    assert dev.available is True
    assert dev.supports_per_zone() is False
    writes.clear()
    assert dev.apply_solid((255, 0, 0), 100, True) is True
    assert [w.hex() for w in writes] == [
        "040601",
        "100203",
        "100500ff00003f3f",
    ]


def test_legion_go_s_solid_honors_brightness(hid_env):
    adapters, writes = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_legion_go_s_entry()]
    dev = adapters.LegionGoSHidDevice.create()
    writes.clear()
    assert dev.apply_solid((255, 0, 0), 50, True) is True
    assert [w.hex() for w in writes] == [
        "040601",
        "100203",
        "100500ff0000203f",
    ]
    profile = writes[-1]
    assert profile[6] == 0x20


def test_legion_go_s_power_off_disables(hid_env):
    adapters, writes = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_legion_go_s_entry()]
    dev = adapters.LegionGoSHidDevice.create()
    writes.clear()
    assert dev.apply_solid((255, 0, 0), 100, False) is True
    assert [w.hex() for w in writes] == ["040600"]


def test_effect_mode_mapping(hid_env):
    adapters, _ = hid_env
    from utils import RGBMode

    assert adapters._effect_mode("breathing") == RGBMode.Pulse
    assert adapters._effect_mode("rainbow") == RGBMode.Rainbow
    assert adapters._effect_mode("wave") == RGBMode.Spiral
    assert adapters._effect_mode("cycle") == RGBMode.Rainbow
    assert adapters._effect_mode("unknown") == RGBMode.Solid


def test_msi_speed_conversion(hid_env):
    adapters, _ = hid_env
    assert adapters._msi_speed(0) == 0
    assert adapters._msi_speed(50) == 10
    assert adapters._msi_speed(100) == 20
    assert adapters._msi_speed(200) == 20


def test_legion_speed_conversion(hid_env):
    adapters, _ = hid_env
    assert adapters._legion_speed(0) == "low"
    assert adapters._legion_speed(33) == "low"
    assert adapters._legion_speed(50) == "medium"
    assert adapters._legion_speed(66) == "medium"
    assert adapters._legion_speed(67) == "high"
    assert adapters._legion_speed(100) == "high"


def _reload_device():
    sys.modules.pop("device", None)
    import device

    return importlib.reload(device)


def _make_dmi_root(tmp_path, product):
    import os

    dmi = os.path.join(str(tmp_path), "sys/class/dmi/id")
    os.makedirs(dmi)
    with open(os.path.join(dmi, "board_name"), "w") as handle:
        handle.write("X")
    with open(os.path.join(dmi, "product_name"), "w") as handle:
        handle.write(product)
    return str(tmp_path)


def test_build_device_legion_hid_available(hid_env, tmp_path):
    adapters, _ = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_legion_go_s_entry()]
    device = _reload_device()
    root = _make_dmi_root(tmp_path, "83L3")
    ctx = device.build_device(sysfs_root=root, ambilight=False)
    assert isinstance(ctx["device"], adapters.LegionGoSHidDevice)
    caps = ctx["capabilities"]
    assert caps["states"]["color"] == "supported"
    assert caps["states"]["effects"] == "supported"
    assert caps["states"]["ambilight"] == "experimental"
    assert caps["perZone"] is False
    assert caps["color"] is True
    sys.modules.pop("device", None)


def test_per_controller_capability_tablet_vs_go_s(hid_env, tmp_path):
    adapters, _ = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_legion_tablet_entry()]
    device = _reload_device()
    tablet_root = _make_dmi_root(tmp_path / "tab", "83E1")
    tablet = device.build_device(sysfs_root=tablet_root, ambilight=False)
    assert tablet["capabilities"]["perControllerColor"] is True

    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_legion_go_s_entry()]
    device = _reload_device()
    go_s_root = _make_dmi_root(tmp_path / "gos", "83L3")
    go_s = device.build_device(sysfs_root=go_s_root, ambilight=False)
    assert go_s["capabilities"]["perControllerColor"] is False
    sys.modules.pop("device", None)


def test_build_device_hid_unavailable_falls_back(hid_env, tmp_path, monkeypatch):
    adapters, _ = hid_env
    monkeypatch.setattr(adapters, "HID_AVAILABLE", False)
    device = _reload_device()
    monkeypatch.setattr(device, "HID_AVAILABLE", False)
    root = _make_dmi_root(tmp_path, "83L3")
    ctx = device.build_device(sysfs_root=root, ambilight=False)
    from led_device import NullDevice

    assert isinstance(ctx["device"], NullDevice)
    caps = ctx["capabilities"]
    assert caps["states"]["color"] == "experimental"
    assert caps["states"]["effects"] == "experimental"
    sys.modules.pop("device", None)


def test_build_device_hid_unavailable_states_do_not_accumulate(hid_env, tmp_path, monkeypatch):
    adapters, _ = hid_env
    monkeypatch.setattr(adapters, "HID_AVAILABLE", False)
    device = _reload_device()
    monkeypatch.setattr(device, "HID_AVAILABLE", False)
    root = _make_dmi_root(tmp_path, "83L3")
    first = device.build_device(sysfs_root=root, ambilight=False)
    second = device.build_device(sysfs_root=root, ambilight=False)
    assert first["capabilities"]["states"] == second["capabilities"]["states"]
    assert first["capabilities"]["experimental"] == second["capabilities"]["experimental"]
    sys.modules.pop("device", None)
