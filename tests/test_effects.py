from py_modules.effects import (
    frame_breathing,
    frame_cycle,
    frame_rainbow,
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
    color = (200, 100, 50)
    speed = 50
    samples = [frame_breathing(color, 4, t / 10.0, speed) for t in range(40)]
    for frame in samples:
        assert len(frame) == 4
        assert all(_valid(c) for c in frame)
    brightnesses = [f[0][0] for f in samples]
    assert min(brightnesses) < max(brightnesses)


def test_frame_rainbow_valid():
    for t in range(20):
        frame = frame_rainbow(4, t / 5.0, 50)
        assert len(frame) == 4
        assert all(_valid(c) for c in frame)


def test_frame_cycle_valid():
    for t in range(20):
        frame = frame_cycle(4, t / 5.0, 50)
        assert len(frame) == 4
        assert all(_valid(c) for c in frame)
