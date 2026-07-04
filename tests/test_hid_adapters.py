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


def test_legion_tablet_reconnects_after_stale_handle(hid_env):
    adapters, writes = hid_env
    entry = _legion_tablet_entry()
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [entry]
    dev = adapters.LegionTabletHidDevice.create()
    assert dev.available is True
    stale = dev._transport.hid_device

    def boom(_data):
        raise OSError("stale handle after resume")

    stale.write = boom
    writes.clear()
    # apply_zones drives the per-controller path; first write raises, heal re-enumerates
    assert dev.apply_zones([(255, 0, 0), (0, 0, 255)], 100, True) is True
    assert dev._transport.hid_device is not stale
    assert len(writes) == 6


def test_legion_tablet_solid_self_heals(hid_env):
    adapters, writes = hid_env
    entry = _legion_tablet_entry()
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [entry]
    dev = adapters.LegionTabletHidDevice.create()
    assert dev.available is True
    stale = dev._transport.hid_device
    stale.write = lambda _data: (_ for _ in ()).throw(OSError("stale"))
    writes.clear()
    assert dev.apply_solid((255, 0, 0), 100, True) is True
    assert dev._transport.hid_device is not stale


def test_reconnect_drops_handle(hid_env):
    adapters, _ = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_legion_tablet_entry()]
    dev = adapters.LegionTabletHidDevice.create()
    assert dev.available is True
    first = dev._transport.hid_device
    assert dev.reconnect() is True
    assert dev._transport.hid_device is not first
    assert dev._transport.prev_mode is None


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
    assert caps["states"]["ambilight"] == "unsupported"
    assert caps["experimental"] == []
    assert caps["perZone"] is False
    assert caps["color"] is True
    sys.modules.pop("device", None)


def test_legion_ambilight_is_supported_when_capture_available(hid_env, tmp_path):
    adapters, _ = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_legion_go_s_entry()]
    device = _reload_device()
    root = _make_dmi_root(tmp_path, "83L3")
    ctx = device.build_device(sysfs_root=root, ambilight=True)
    caps = ctx["capabilities"]
    assert caps["states"]["ambilight"] == "supported"
    assert caps["ambilight"] is True
    assert "ambilight" not in caps["experimental"]
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


def _ally_entry():
    return {
        "vendor_id": 0x0B05,
        "product_id": 0x1ABE,
        "usage_page": 0xFF31,
        "usage": 0x0080,
        "interface_number": 0,
        "path": b"ally",
    }


def _ally_x_entry():
    # Ally X / Xbox Ally enumerate the same Aura N-KEY interface under a DIFFERENT PID
    # than the original Ally (0x1ABE). We match by VID + usage, so the PID is irrelevant.
    return {
        "vendor_id": 0x0B05,
        "product_id": 0x1B4C,
        "usage_page": 0xFF31,
        "usage": 0x0080,
        "interface_number": 0,
        "path": b"allyx",
    }


def test_ally_hid_registered(hid_env):
    adapters, _ = hid_env
    assert "hid_asus_ally" in adapters.HID_DRIVERS


def test_ally_matches_by_vid_and_usage_ignoring_pid(hid_env):
    adapters, _ = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_ally_x_entry()]
    dev = adapters.AsusAllyHidDevice.create()
    assert dev.available is True


def test_ally_ignores_foreign_vid_even_with_matching_usage(hid_env):
    adapters, _ = hid_env
    foreign = dict(_ally_x_entry(), vendor_id=0x1234)
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [foreign]
    dev = adapters.AsusAllyHidDevice.create()
    assert dev.available is False


def _make_ally_x_root(tmp_path, with_empty_leds=True):
    import os

    dmi = os.path.join(str(tmp_path), "sys/class/dmi/id")
    os.makedirs(dmi)
    with open(os.path.join(dmi, "board_name"), "w") as handle:
        handle.write("RC72LA")
    with open(os.path.join(dmi, "product_name"), "w") as handle:
        handle.write("ROG Ally X RC72LA")
    if with_empty_leds:
        os.makedirs(os.path.join(str(tmp_path), "sys/class/leds"))
    return str(tmp_path)


def test_build_device_ally_x_falls_back_to_hid_without_sysfs(hid_env, tmp_path):
    adapters, _ = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_ally_x_entry()]
    device = _reload_device()
    root = _make_ally_x_root(tmp_path)
    ctx = device.build_device(sysfs_root=root, ambilight=False)
    assert isinstance(ctx["device"], adapters.AsusAllyHidDevice)
    caps = ctx["capabilities"]
    assert caps["color"] is True
    assert caps["hardwareEffects"] is True
    assert caps["reconnectable"] is True
    assert caps["conflictsWithSystemRgb"] is True
    assert "spiral" not in caps["supportedEffects"]
    assert ctx["info"]["name"] == "ROG Ally X"
    sys.modules.pop("device", None)


def test_build_device_ally_x_prefers_sysfs_when_node_present(hid_env, tmp_path):
    import os

    adapters, _ = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_ally_x_entry()]
    device = _reload_device()
    root = _make_ally_x_root(tmp_path, with_empty_leds=False)
    led = os.path.join(root, "sys/class/leds", "ally:rgb:joystick_rings")
    os.makedirs(led)
    for name, content in {
        "multi_intensity": "0 0 0 0",
        "multi_index": "rgb rgb rgb rgb",
        "max_brightness": "255",
        "brightness": "0",
    }.items():
        with open(os.path.join(led, name), "w") as handle:
            handle.write(content)
    ctx = device.build_device(sysfs_root=root, ambilight=False)
    from led_device import SysfsRgbDevice

    assert isinstance(ctx["device"], SysfsRgbDevice)
    assert ctx["capabilities"]["conflictsWithSystemRgb"] is False
    sys.modules.pop("device", None)


def test_build_device_ally_x_null_when_no_sysfs_and_no_hid(hid_env, tmp_path):
    adapters, _ = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: []  # HID device absent too
    device = _reload_device()
    root = _make_ally_x_root(tmp_path)
    ctx = device.build_device(sysfs_root=root, ambilight=False)
    from led_device import NullDevice

    assert isinstance(ctx["device"], NullDevice)
    assert ctx["capabilities"]["color"] is False
    sys.modules.pop("device", None)


def test_ally_first_solid_sends_init_and_apply(hid_env):
    adapters, writes = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_ally_entry()]
    dev = adapters.AsusAllyHidDevice.create()
    assert dev.available is True
    assert dev.supports_per_zone() is True
    assert dev.supports_hardware_effects() is True
    writes.clear()
    assert dev.apply_solid((255, 0, 0), 100, True) is True
    assert len(writes) == 8
    assert writes[0][:15] == bytes([0x5D]) + b"ASUS Tech.Inc."
    assert writes[1][:5] == bytes.fromhex("5abac5c403")
    zone_packets = writes[2:6]
    assert [p[2] for p in zone_packets] == [0x01, 0x02, 0x03, 0x04]
    assert all(tuple(p[4:7]) == (255, 0, 0) for p in zone_packets)
    assert writes[6][:2] == bytes([0x5D, 0xB5])
    assert writes[7][:2] == bytes([0x5D, 0xB4])


def test_ally_second_solid_skips_init(hid_env):
    adapters, writes = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_ally_entry()]
    dev = adapters.AsusAllyHidDevice.create()
    dev.apply_solid((255, 0, 0), 100, True)
    writes.clear()
    assert dev.apply_solid((0, 0, 255), 100, True) is True
    assert len(writes) == 5
    assert all(tuple(p[4:7]) == (0, 0, 255) for p in writes[1:5])


def test_ally_per_zone_distinct_colors(hid_env):
    adapters, writes = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_ally_entry()]
    dev = adapters.AsusAllyHidDevice.create()
    dev.apply_solid((1, 0, 0), 100, True)
    writes.clear()
    colors = [(10, 0, 0), (20, 0, 0), (30, 0, 0), (40, 0, 0)]
    assert dev.apply_zones(colors, 100, True) is True
    zone_packets = writes[1:5]
    assert [tuple(p[4:7]) for p in zone_packets] == colors


def test_ally_power_off_blacks_out(hid_env):
    adapters, writes = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_ally_entry()]
    dev = adapters.AsusAllyHidDevice.create()
    dev.apply_solid((255, 0, 0), 100, True)
    writes.clear()
    assert dev.apply_zones([(255, 0, 0)] * 4, 100, False) is True
    assert writes[0][:5] == bytes.fromhex("5abac5c400")
    assert tuple(writes[1][4:7]) == (0, 0, 0)


def test_ally_hardware_effect_uses_mode_and_speed(hid_env):
    adapters, writes = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_ally_entry()]
    dev = adapters.AsusAllyHidDevice.create()
    writes.clear()
    assert dev.apply_hardware_effect("rainbow", (0, 255, 0), 90, True) is True
    zone_packets = [p for p in writes if p[:2] == bytes([0x5D, 0xB3])]
    assert zone_packets
    assert all(p[3] == 0x02 for p in zone_packets)
    assert all(p[7] == 0xF5 for p in zone_packets)


def test_ally_color_correction_threads_through(hid_env):
    adapters, writes = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_ally_entry()]
    dev = adapters.AsusAllyHidDevice.create()
    dev.set_color_correction((1.0, 0.85, 1.0))  # what _build_hid_context passes from the profile
    writes.clear()
    dev.apply_solid((0, 255, 0), 100, True)
    green_zone = writes[2]  # after init + brightness
    assert tuple(green_zone[4:7]) == (0, round(255 * 0.85), 0)


def test_ally_invalidate_forces_reinit(hid_env):
    adapters, writes = hid_env
    sys.modules["lib_hid"].enumerate = lambda vid=0, pid=0: [_ally_entry()]
    dev = adapters.AsusAllyHidDevice.create()
    dev.apply_solid((255, 0, 0), 100, True)  # prev_mode -> "solid"
    dev.invalidate()  # drop the cached mode
    writes.clear()
    dev.apply_solid((255, 0, 0), 100, True)  # must re-send the full init+apply
    assert len(writes) == 8
    assert writes[0][:15] == bytes([0x5D]) + b"ASUS Tech.Inc."
    assert writes[-1][:2] == bytes([0x5D, 0xB4])  # APPLY
