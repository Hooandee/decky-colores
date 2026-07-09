import os

from report.collector import (
    SCHEMA,
    build_bundle,
    capabilities_from,
    kernel_logs,
    redact_obj,
    redact_text,
    rgb_conflict_cmds,
    sysfs_snapshot,
    tail_logs,
)


def test_redact_text_strips_pii():
    txt = "at /home/deck/x mac aa:bb:cc:dd:ee:ff serial: RC72LA12345"
    out = redact_text(txt, home="/home/deck", hostname=None)
    assert "/home/deck" not in out
    assert "aa:bb:cc:dd:ee:ff" not in out and "[mac]" in out
    assert "[serial]" in out


def test_redact_text_keeps_plain_words():
    assert redact_text("Steam Deck") == "Steam Deck"


def test_serial_run_spares_led_node_and_hid_tokens():
    out = redact_text("node input177:rgb:indicator id 00000B05")
    assert "input177" in out and "00000B05" in out


def test_redact_text_root_home_does_not_mangle():
    assert redact_text("hello world", home="/") == "hello world"


def test_redact_obj_scrubs_serial_like_keys():
    out = redact_obj({"board_serial": "ABC123XYZ", "name": "ROG Ally"})
    assert out["board_serial"] == "[redacted]"
    assert out["name"] == "ROG Ally"


def test_tail_logs_reads_newest_first_and_redacts(tmp_path):
    old = tmp_path / "a.log"
    new = tmp_path / "b.log"
    old.write_text("old /home/deck/x\n")
    new.write_text("new line\n")
    os.utime(old, (1, 1))
    os.utime(new, (2, 2))
    logs = tail_logs(str(tmp_path), home="/home/deck")
    assert logs[0]["name"] == "b.log"
    assert all("/home/deck" not in entry["text"] for entry in logs)


def test_tail_logs_missing_dir_is_empty():
    assert tail_logs("/nope/nope") == []


def test_kernel_logs_redacts_and_caps():
    def run(cmd):
        return "error /home/deck/x failed" if "dmesg" in cmd[0] else None
    out = kernel_logs(run, cap=1000, home="/home/deck")
    assert "~/x" in out["dmesg"] and "/home/deck" not in out["dmesg"]
    assert out["journal"] is None


def test_kernel_logs_runner_raising_is_null():
    def run(cmd):
        raise OSError("boom")
    out = kernel_logs(run)
    assert out["dmesg"] is None and out["journal"] is None


def test_rgb_conflict_cmds():
    assert rgb_conflict_cmds(False) == {}
    cmd = rgb_conflict_cmds(True)["hhd"]
    assert "journalctl" in cmd[0] and "hhd.service" in cmd


def test_kernel_logs_captures_hhd_journal():
    def run(cmd):
        return "HHD reasserted /home/deck/x" if "hhd.service" in cmd else None
    out = kernel_logs(run, extra=rgb_conflict_cmds(True), home="/home/deck")
    assert "~/x" in out["hhd"] and "/home/deck" not in out["hhd"]


def test_capabilities_from_distils_led_caps():
    state = {
        "device": {"name": "ROG Ally X", "board": "RC72LA", "product": "RC72LA"},
        "capabilities": {
            "driver": "sysfs_rgb", "ledPath": "/sys/class/leds/ally:rgb:joystick_rings",
            "color": True, "brightness": True, "zones": 4, "maxBrightness": 255,
            "perZone": True, "hardwareEffects": False, "ambilight": True,
            "batteryMode": True, "powerLed": False, "conflictsWithSystemRgb": False,
            "supportedEffects": ["breathing", "wave"], "enabledExperiments": [],
        },
    }
    caps = capabilities_from(state)
    assert caps["device_name"] == "ROG Ally X"
    assert caps["driver"] == "sysfs_rgb" and caps["zones"] == 4
    assert caps["color"] is True and caps["ambilight"] is True
    assert caps["supported_effects"] == ["breathing", "wave"]
    assert caps["conflicts_with_system_rgb"] is False


def test_capabilities_from_empty_is_safe():
    caps = capabilities_from({})
    assert caps["color"] is False and caps["zones"] is None
    assert caps["supported_effects"] == []


def test_capabilities_from_uses_controller_fallback():
    caps = capabilities_from(
        {"capabilities": {"color": True}, "device": {"name": "ROG Ally"}},
        driver="AsusAllyHidDevice",
        led_path=None,
        last_error="probe with driver asus failed with error -12",
    )
    assert caps["driver"] == "AsusAllyHidDevice"
    assert caps["led_path"] is None
    assert caps["last_error"] == "probe with driver asus failed with error -12"


def test_sysfs_snapshot_lists_led_nodes(tmp_path):
    led = tmp_path / "sys/class/leds/ally:rgb:joystick_rings"
    led.mkdir(parents=True)
    (led / "multi_index").write_text("red green blue red green blue")
    (led / "max_brightness").write_text("255")
    (led / "brightness").write_text("128")
    (led / "multi_intensity").write_text("0 0 0")
    snap = sysfs_snapshot(root=str(tmp_path))
    leds = snap["leds"]
    assert len(leds) == 1
    entry = leds[0]
    assert entry["name"] == "ally:rgb:joystick_rings"
    assert entry["has_multi_intensity"] is True
    assert entry["max_brightness"] == "255"
    assert entry["multi_index"].split() == ["red", "green", "blue", "red", "green", "blue"]


def test_sysfs_snapshot_finds_asus_hid(tmp_path):
    dev = tmp_path / "sys/bus/hid/devices/0003:0B05:1ABE.0001"
    dev.mkdir(parents=True)
    (dev / "uevent").write_text("HID_ID=0003:00000B05:00001ABE\nHID_NAME=ASUS N-KEY Device\n")
    snap = sysfs_snapshot(root=str(tmp_path))
    assert len(snap["hid"]) == 1
    assert snap["hid"][0]["hid_name"] == "ASUS N-KEY Device"


def test_sysfs_snapshot_captures_any_vendor_hid(tmp_path):
    dev = tmp_path / "sys/bus/hid/devices/0003:17EF:6182.0001"
    dev.mkdir(parents=True)
    (dev / "uevent").write_text("HID_ID=0003:000017EF:00006182\nHID_NAME=Legion Controller\n")
    snap = sysfs_snapshot(root=str(tmp_path))
    assert len(snap["hid"]) == 1
    assert snap["hid"][0]["hid_name"] == "Legion Controller"
    assert snap["hid"][0]["path"] == "0003:17EF:6182.0001"


def test_sysfs_snapshot_never_raises_on_missing_root():
    snap = sysfs_snapshot(root="/nope/nope")
    assert snap == {"leds": [], "hid": [], "modules": [], "power_supply": {}}


def test_build_bundle_shape():
    b = build_bundle(
        app="colores", categories=["color"], text="x" * 5000,
        environment={"os": "Bazzite"}, capabilities={"color": True},
        state={}, stores={}, logs=[], kernel={"dmesg": "x", "journal": None},
        sysfs={"leds": []},
    )
    assert b["schema"] == SCHEMA and b["app"] == "colores"
    assert b["categories"] == ["color"]
    assert len(b["text"]) == 4000
    assert b["kernel"] == {"dmesg": "x", "journal": None}
    assert b["sysfs"] == {"leds": []}
