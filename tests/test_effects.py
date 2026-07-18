from py_modules.effects import (
    BATTERY_BANDS,
    TEMPERATURE_BANDS,
    battery_band_color,
    temperature_band_color,
    frame_breathing,
    frame_cycle,
    frame_gradient_sweep,
    frame_rainbow,
    frame_spiral,
    frame_comet,
    frame_sparkle,
    frame_ripple,
    frame_aurora,
    frame_meter,
    hsv_to_rgb,
    interpolate_gradient,
)


def test_meter_fills_proportionally():
    frame = frame_meter(0.5, 10)
    assert len(frame) == 10
    lit = [c for c in frame if sum(c) > 0]
    assert len(lit) == 5  # half of 10 zones lit


def test_meter_zero_is_dark_and_full_is_all_lit():
    assert all(c == (0, 0, 0) for c in frame_meter(0.0, 8))
    assert all(sum(c) > 0 for c in frame_meter(1.0, 8))


def test_meter_clamps_and_handles_zero_zones():
    assert frame_meter(5.0, 4) == frame_meter(1.0, 4)
    assert frame_meter(0.5, 0) == []


def _base(n, color=(200, 100, 50)):
    return [color] * n


def test_comet_returns_one_color_per_zone_and_has_a_peak():
    frame = frame_comet(_base(17), 0.0, 50)
    assert len(frame) == 17
    # At t=0 the comet sits at zone 0, so it is the brightest.
    assert frame[0] == (200, 100, 50)
    assert sum(frame[-1]) < sum(frame[0])


def test_comet_is_deterministic():
    assert frame_comet(_base(17), 1.3, 40) == frame_comet(_base(17), 1.3, 40)


def test_sparkle_length_and_bounds():
    frame = frame_sparkle(_base(17, (255, 255, 255)), 2.0, 60)
    assert len(frame) == 17
    for c in frame:
        for ch in c:
            assert 0 <= ch <= 255


def test_sparkle_is_deterministic():
    assert frame_sparkle(_base(17), 0.7, 50) == frame_sparkle(_base(17), 0.7, 50)


def test_ripple_modulates_brightness_along_strip():
    frame = frame_ripple(_base(17, (100, 100, 100)), 0.0, 50)
    assert len(frame) == 17
    assert len({c for c in frame}) > 1  # not uniform — a wave is present


def test_aurora_fills_all_zones_with_color():
    frame = frame_aurora(17, 0.5, 50)
    assert len(frame) == 17
    assert all(isinstance(c, tuple) and len(c) == 3 for c in frame)


def test_new_effects_handle_zero_zones():
    assert frame_comet([], 0.0, 50) == []
    assert frame_sparkle([], 0.0, 50) == []
    assert frame_ripple([], 0.0, 50) == []
    assert frame_aurora(0, 0.0, 50) == []


def _valid(color):
    return all(isinstance(c, int) and 0 <= c <= 255 for c in color)


def test_interpolate_two_stops_across_four_zones():
    result = interpolate_gradient([(0, 0, 0), (255, 255, 255)], 4)
    assert len(result) == 4
    assert result[0] == (0, 0, 0)
    assert result[-1] == (255, 255, 255)
    assert result[0][0] < result[1][0] < result[2][0] < result[3][0]


def test_interpolate_single_stop_all_equal():
    result = interpolate_gradient([(10, 20, 30)], 4)
    assert result == [(10, 20, 30)] * 4


def test_interpolate_four_stops_each_at_its_zone():
    stops = [(255, 0, 0), (0, 255, 0), (0, 0, 255), (255, 255, 0)]
    result = interpolate_gradient(stops, 4)
    assert result == stops


def test_interpolate_single_zone():
    assert interpolate_gradient([(1, 2, 3), (4, 5, 6)], 1) == [(1, 2, 3)]


def test_hsv_primaries():
    assert hsv_to_rgb(0, 1, 1) == (255, 0, 0)
    assert hsv_to_rgb(120, 1, 1) == (0, 255, 0)
    assert hsv_to_rgb(240, 1, 1) == (0, 0, 255)
    assert hsv_to_rgb(0, 0, 0) == (0, 0, 0)


def test_frame_breathing_shape_and_variation():
    base = [(200, 100, 50)] * 4
    speed = 50
    samples = [frame_breathing(base, t / 10.0, speed) for t in range(40)]
    for frame in samples:
        assert len(frame) == 4
        assert all(_valid(c) for c in frame)
    brightnesses = [f[0][0] for f in samples]
    assert min(brightnesses) < max(brightnesses)


def test_frame_breathing_preserves_per_zone_palette():
    base = [(200, 0, 0), (0, 200, 0)]
    frame = frame_breathing(base, 0.0, 50)
    assert frame[0][0] >= frame[0][1] and frame[0][0] >= frame[0][2]
    assert frame[1][1] >= frame[1][0] and frame[1][1] >= frame[1][2]


def test_frame_rainbow_valid():
    for t in range(20):
        frame = frame_rainbow(4, t / 5.0, 50)
        assert len(frame) == 4
        assert all(_valid(c) for c in frame)


def test_frame_gradient_sweep_all_zones_equal_and_valid():
    stops = [(0, 196, 255), (136, 86, 255)]
    frame = frame_gradient_sweep(stops, 2, 0.7, 14)
    assert len(frame) == 2
    assert frame[0] == frame[1]  # replicated: every zone shares one color
    assert _valid(frame[0])


def test_frame_gradient_sweep_starts_and_turns_at_palette_ends():
    stops = [(0, 0, 0), (255, 255, 255)]
    # phase 0 -> pos 0 -> first stop
    assert frame_gradient_sweep(stops, 1, 0.0, 14)[0] == (0, 0, 0)
    # half phase -> pos 1 -> last stop (freq*t = 0.5 when t chosen so phase=0.5)
    from py_modules.effects import _freq

    t_half = 0.5 / _freq(14)
    assert frame_gradient_sweep(stops, 1, t_half, 14)[0] == (255, 255, 255)


def test_frame_cycle_valid():
    for t in range(20):
        frame = frame_cycle(4, t / 5.0, 50)
        assert len(frame) == 4
        assert all(_valid(c) for c in frame)


def test_frame_spiral_at_t0_matches_palette():
    palette = interpolate_gradient([(255, 0, 0), (0, 0, 255)], 4)
    assert frame_spiral(palette, 0.0, 50) == palette


def test_frame_spiral_rotates_and_stays_valid():
    palette = interpolate_gradient([(255, 0, 0), (0, 255, 0), (0, 0, 255)], 4)
    start = frame_spiral(palette, 0.0, 60)
    moved = False
    for t in range(1, 30):
        frame = frame_spiral(palette, t / 10.0, 60)
        assert len(frame) == 4
        assert all(_valid(c) for c in frame)
        if frame != start:
            moved = True
    assert moved


def test_battery_band_color_thresholds():
    assert battery_band_color(100) == (0, 120, 255)
    assert battery_band_color(81) == (0, 120, 255)
    assert battery_band_color(80) == (0, 200, 60)
    assert battery_band_color(61) == (0, 200, 60)
    assert battery_band_color(60) == (255, 200, 0)
    assert battery_band_color(41) == (255, 200, 0)
    assert battery_band_color(40) == (255, 110, 0)
    assert battery_band_color(21) == (255, 110, 0)
    assert battery_band_color(20) == (255, 30, 20)
    assert battery_band_color(0) == (255, 30, 20)


def test_battery_bands_cover_full_range_descending():
    thresholds = [b[0] for b in BATTERY_BANDS]
    assert thresholds == sorted(thresholds, reverse=True)
    assert thresholds[-1] == 0
    assert all(_valid(color) for _, color in BATTERY_BANDS)


def test_temperature_band_color_thresholds():
    assert temperature_band_color(30) == (0, 120, 255)
    assert temperature_band_color(54) == (0, 120, 255)
    assert temperature_band_color(55) == (0, 200, 60)
    assert temperature_band_color(67) == (0, 200, 60)
    assert temperature_band_color(68) == (255, 200, 0)
    assert temperature_band_color(79) == (255, 200, 0)
    assert temperature_band_color(80) == (255, 110, 0)
    assert temperature_band_color(89) == (255, 110, 0)
    assert temperature_band_color(90) == (255, 30, 20)
    assert temperature_band_color(105) == (255, 30, 20)


def test_temperature_bands_cover_full_range_descending():
    thresholds = [b[0] for b in TEMPERATURE_BANDS]
    assert thresholds == sorted(thresholds, reverse=True)
    assert thresholds[-1] == 0
    assert all(_valid(color) for _, color in TEMPERATURE_BANDS)
