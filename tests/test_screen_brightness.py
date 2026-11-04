import pytest

from screen_brightness import (
    FLOOR,
    FactorRamp,
    led_brightness,
    has_backlight,
    perceived_level,
    read_backlight,
)


def _backlight(root, name, brightness, maximum, bl_power="0"):
    node = root / name
    node.mkdir()
    (node / "brightness").write_text(f"{brightness}\n")
    (node / "max_brightness").write_text(f"{maximum}\n")
    if bl_power is not None:
        (node / "bl_power").write_text(f"{bl_power}\n")
    return node


def test_reads_single_backlight_fraction(tmp_path):
    _backlight(tmp_path, "amdgpu_bl0", 19483, 65535)
    assert read_backlight(str(tmp_path)) == pytest.approx(19483 / 65535)


def test_has_backlight_even_while_panel_is_dark(tmp_path):
    _backlight(tmp_path, "amdgpu_bl0", 0, 65535)
    assert has_backlight(str(tmp_path))
    assert not has_backlight(str(tmp_path / "missing"))


def test_missing_root_is_unavailable(tmp_path):
    assert read_backlight(str(tmp_path / "missing")) is None


def test_ignores_blanked_and_broken_nodes(tmp_path):
    _backlight(tmp_path, "a_blanked", 100, 100, bl_power="4")
    _backlight(tmp_path, "b_zero_max", 5, 0)
    _backlight(tmp_path, "c_live", 30, 100)
    assert read_backlight(str(tmp_path)) == pytest.approx(0.3)


def test_dark_panel_reads_as_unknown(tmp_path):
    _backlight(tmp_path, "amdgpu_bl0", 0, 65535)
    assert read_backlight(str(tmp_path)) is None


def test_dual_screen_follows_brightest_lit_panel(tmp_path):
    _backlight(tmp_path, "dsi0", 40, 255)
    _backlight(tmp_path, "dsi1", 200, 255)
    assert read_backlight(str(tmp_path)) == pytest.approx(200 / 255)


def test_clamps_driver_values_above_max(tmp_path):
    _backlight(tmp_path, "pwm", 300, 255, bl_power=None)
    assert read_backlight(str(tmp_path)) == 1.0


@pytest.mark.parametrize(
    "raw,slider",
    [
        (5016 / 65535, 0.3),
        (19483 / 65535, 0.5),
        (42494 / 65535, 0.8),
        (55108 / 65535, 0.9),
    ],
)
def test_perceived_level_tracks_steam_slider_on_legion_go_s(raw, slider):
    assert perceived_level(raw) == pytest.approx(slider, abs=0.12)


def test_perceived_level_bounds():
    assert perceived_level(0.0) == 0.0
    assert perceived_level(1.0) == pytest.approx(1.0)
    assert perceived_level(2.0) == pytest.approx(1.0)


def test_led_brightness_scales_user_maximum():
    assert led_brightness(80, 1.0) == 80
    assert led_brightness(80, 0.5) == 40


def test_led_brightness_keeps_a_visible_floor():
    assert led_brightness(100, 0.0) == round(100 * FLOOR)
    assert led_brightness(5, 0.0) == 1


def test_led_brightness_rounds_half_up_like_android():
    assert led_brightness(25, 0.5) == 13


def test_led_brightness_zero_stays_off():
    assert led_brightness(0, 1.0) == 0


def test_ramp_starts_settled_at_full():
    ramp = FactorRamp(duration=0.4)
    assert ramp.value(0.0) == 1.0
    assert ramp.settled(0.0)


def test_ramp_eases_to_target_without_overshoot():
    ramp = FactorRamp(duration=0.4)
    ramp.set_target(0.2, 10.0)
    assert not ramp.settled(10.0)
    values = [ramp.value(10.0 + step * 0.05) for step in range(10)]
    assert values[0] == 1.0
    assert all(a >= b for a, b in zip(values, values[1:]))
    assert values[-1] == pytest.approx(0.2)
    assert ramp.settled(10.4)


def test_ramp_retarget_mid_way_continues_from_current_value():
    ramp = FactorRamp(duration=0.4)
    ramp.set_target(0.0, 0.0)
    mid = ramp.value(0.2)
    ramp.set_target(1.0, 0.2)
    assert ramp.value(0.2) == pytest.approx(mid)
    assert ramp.value(0.6) == pytest.approx(1.0)


def test_ramp_snap_skips_animation():
    ramp = FactorRamp(duration=0.4)
    ramp.snap(0.5)
    assert ramp.value(0.0) == 0.5
    assert ramp.settled(0.0)
