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
    # "spiral" is the Legion-only firmware effect ("Espiral GO") — never on the Ally.
    assert "spiral" not in p["supported_effects"]


def test_legion_keeps_spiral_effect():  # regression guard for the Legion-only spiral
    for product in ("83E1", "83N0", "83L3"):
        assert "spiral" in resolve_profile("", product)["supported_effects"]


def test_asus_sysfs_profiles_expose_aura_hid_fallback():
    # When the kernel sysfs RGB node is missing (varies by kernel/Bazzite version),
    # build_device drops to this Aura HID driver instead of showing "no LEDs".
    for board in ("RC72LA", "RC73XA", "RC73YA"):
        p = resolve_profile(board, "whatever")
        fb = p["fallback"]
        assert fb["driver"] == "hid_asus_ally"
        assert fb["conflicts_with_system_rgb"] is True
        # spiral renders as the Legion-only "Espiral GO" under hardwareEffects — keep it out.
        assert "spiral" not in fb["supported_effects"]


def test_asus_fallback_is_private_copy():
    first = resolve_profile("RC72LA", "x")
    first["fallback"]["experimental"].append("mutated")
    second = resolve_profile("RC72LA", "x")
    assert "mutated" not in second["fallback"]["experimental"]


def test_generic_and_hid_profiles_have_no_asus_fallback():
    assert "fallback" not in resolve_profile("X", "MysteryHandheld")
    assert "fallback" not in resolve_profile("RC71L", "ROG Ally RC71L_RC71L")


def test_ally_x_stays_sysfs_not_hid():  # regression guard: do NOT touch Ally X
    for board in ("RC72LA", "RC73XA", "RC73YA"):
        p = resolve_profile(board, "whatever")
        assert p["driver"] == "sysfs", f"{board} must stay sysfs"
        assert p.get("conflicts_with_system_rgb", False) is False


def test_steam_machine_profile_by_board():
    p = resolve_profile("Fremont", "Fremont")
    assert p["name"] == "Steam Machine"
    assert p["driver"] == "valve_leds"
    assert p["zones"] == 17
    assert p["layout_kind"] == "bar"
    assert p["conflicts_with_system_rgb"] is True
    assert p["indicator_led"] is True
    assert p["persistent_startup"] is True


def test_steam_machine_preproduction_by_product():
    p = resolve_profile("", "F7F")
    assert p["name"] == "Steam Machine"
    assert p["driver"] == "valve_leds"
