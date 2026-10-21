import pytest

from color_calibration import NEUTRAL, Calibration, channel_curves, parse_steps


def test_neutral_keeps_colors():
    frame = [(255, 128, 0)]
    assert Calibration(NEUTRAL).apply_all(frame) is frame


def test_thor_yellow_and_orange_are_fixed_together():
    calibration = Calibration({"yellow": 37, "orange": -56, "white": 0})
    assert calibration.apply((255, 255, 0)) == (161, 255, 0)
    assert calibration.apply((255, 128, 0)) == (161, 81, 0)


def test_white_step_warms_by_lowering_blue():
    assert Calibration({"yellow": 0, "orange": 0, "white": -20}).apply((255, 255, 255)) == (255, 255, 204)


def test_white_step_cools_by_lowering_red_and_green():
    assert Calibration({"yellow": 0, "orange": 0, "white": 20}).apply((255, 255, 255)) == (204, 204, 255)


def test_yellow_step_lowers_green_when_negative():
    gains, gammas = channel_curves({"yellow": -30, "orange": 0, "white": 0})
    assert gains == [100.0, 70.0, 100.0]
    assert gammas == [1.0, 1.0, 1.0]


def test_rounding_is_half_up():
    assert Calibration({"yellow": 0, "orange": 0, "white": -50}).apply((0, 0, 253)) == (0, 0, 127)


@pytest.mark.parametrize(
    "raw,expected",
    [
        ({"yellow": 80, "orange": -150, "white": 10}, {"yellow": 60, "orange": -100, "white": 10}),
        ({}, NEUTRAL),
        ({"yellow": 12.6}, {"yellow": 13, "orange": 0, "white": 0}),
    ],
)
def test_parse_steps_clamps(raw, expected):
    assert parse_steps(raw) == expected


@pytest.mark.parametrize(
    "raw",
    [None, [1, 2, 3], {"yellow": "a"}, {"orange": True}, {"white": float("nan")}, {"white": float("inf")}],
)
def test_parse_steps_rejects_garbage(raw):
    with pytest.raises(ValueError):
        parse_steps(raw)
