from py_modules.effects import (
    frame_breathing,
    frame_cycle,
    frame_gradient_sweep,
    frame_rainbow,
    frame_spiral,
    hsv_to_rgb,
    interpolate_gradient,
)


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
    stops = [(255, 0, 0), (0, 0, 255)]
    assert frame_spiral(stops, 4, 0.0, 50) == interpolate_gradient(stops, 4)


def test_frame_spiral_rotates_and_stays_valid():
    stops = [(255, 0, 0), (0, 255, 0), (0, 0, 255)]
    start = frame_spiral(stops, 4, 0.0, 60)
    moved = False
    for t in range(1, 30):
        frame = frame_spiral(stops, 4, t / 10.0, 60)
        assert len(frame) == 4
        assert all(_valid(c) for c in frame)
        if frame != start:
            moved = True
    assert moved
