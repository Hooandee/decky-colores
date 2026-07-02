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


def test_ambilight_is_not_experimental_on_hid_profiles():
    for product in ("Claw 8 AI+ A2VM", "83N0", "83L3"):
        p = resolve_profile("", product)
        assert "ambilight" not in p["experimental"]


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


def test_resolve_profile_experimental_is_not_shared():
    first = resolve_profile("", "83L3")
    first["experimental"].append("mutated")
    second = resolve_profile("", "83L3")
    assert "mutated" not in second["experimental"]


def test_generic_fallback_experimental_is_not_shared():
    first = resolve_profile("X", "MysteryHandheld")
    first["experimental"].append("mutated")
    second = resolve_profile("Y", "OtherMystery")
    assert "mutated" not in second["experimental"]


def test_rog_ally_rc71l_uses_aura_hid():
    p = resolve_profile("RC71L", "ROG Ally RC71L_RC71L")
    assert p["name"] == "ROG Ally"
    assert p["driver"] == "hid_asus_ally"
    assert p["zones"] == 4
    assert p["conflicts_with_system_rgb"] is True


def test_ally_x_stays_sysfs_not_hid():  # regression guard: do NOT touch Ally X
    for board in ("RC72LA", "RC73XA", "RC73YA"):
        p = resolve_profile(board, "whatever")
        assert p["driver"] == "sysfs", f"{board} must stay sysfs"
        assert p.get("conflicts_with_system_rgb", False) is False
