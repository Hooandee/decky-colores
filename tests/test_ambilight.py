from py_modules.ambilight import (
    alpha_for,
    avg_region,
    boost_saturation,
    lerp,
    zone_colors,
)


def _solid_frame(width, height, color):
    return bytes(list(color) * (width * height))


def test_avg_region_solid_frame():
    frame = _solid_frame(4, 4, (10, 20, 30))
    assert avg_region(frame, 4, 4, (0.0, 0.0, 1.0, 1.0)) == (10, 20, 30)


def test_avg_region_isolates_corner():
    frame = bytearray(_solid_frame(4, 4, (0, 0, 0)))
    top_left = (1 * 4 + 1) * 3
    frame[top_left] = 200
    frame[top_left + 1] = 100
    frame[top_left + 2] = 50
    avg = avg_region(frame, 4, 4, (0.0, 0.0, 0.5, 0.5))
    assert avg[0] > 0 and avg[1] > 0


def test_boost_saturation_increases_spread():
    base = (140, 120, 100)
    boosted = boost_saturation(base, 1.6)
    assert max(boosted) - min(boosted) > max(base) - min(base)


def test_boost_saturation_identity():
    assert boost_saturation((100, 100, 100), 1.5) == (100, 100, 100)


def test_lerp_moves_toward_target():
    assert lerp((0, 0, 0), (100, 100, 100), 0.5) == (50, 50, 50)
    assert lerp((0, 0, 0), (100, 0, 0), 1.0) == (100, 0, 0)


def test_alpha_for_mapping():
    assert alpha_for(0) == 1.0
    assert alpha_for(100) == 0.04
    assert 0.2 < alpha_for(75) < 0.3


def test_zone_colors_splits_left_right():
    assert zone_colors((1, 1, 1), (2, 2, 2), 4) == [(1, 1, 1), (1, 1, 1), (2, 2, 2), (2, 2, 2)]
    assert zone_colors((1, 1, 1), (2, 2, 2), 1) == [(1, 1, 1)]
