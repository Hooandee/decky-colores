from py_modules.device_profiles import resolve_profile


def test_ally_x_profile_is_supported_sysfs_rgb():
    p = resolve_profile("RC72LA", "ROG Ally X RC72LA")
    assert p["name"] == "ROG Ally X"
    assert p["driver"] == "sysfs"
    assert p["color_order"] == "rgb"
    assert p["experimental"] == []


def test_xbox_ally_x_matches_by_board():
    p = resolve_profile("RC73XA", "whatever")
    assert p["name"] == "ROG Xbox Ally X"
    assert p["driver"] == "sysfs"


def test_msi_claw_8_profile_is_bgr_hid():
    p = resolve_profile("", "Claw 8 AI+ A2VM")
    assert p["name"] == "MSI Claw 8 AI+"
    assert p["driver"] == "hid_msi"
    assert p["color_order"] == "bgr"


def test_legion_go_2_profile_is_hid_tablet():
    p = resolve_profile("", "83N0")
    assert p["name"] == "Legion Go 2"
    assert p["driver"] == "hid_legion_tablet"


def test_legion_go_s_profile_is_its_own_driver():
    p = resolve_profile("", "83L3")
    assert p["driver"] == "hid_legion_go_s"


def test_unknown_device_is_generic_experimental():
    p = resolve_profile("X", "MysteryHandheld")
    assert p["name"] == "MysteryHandheld"
    assert p["driver"] == "sysfs"
    assert set(p["experimental"]) == {"color", "brightness", "effects", "ambilight"}
