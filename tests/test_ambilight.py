import asyncio

import pytest

import py_modules.ambilight as ambilight_mod
from py_modules.ambilight import (
    Ambilight,
    CAP_W,
    CAP_H,
    _gst_command,
    adaptive_alpha,
    alpha_for,
    avg_region,
    boost_saturation,
    lerp,
    subdivide,
)


def _split_frame():
    # Top half red, bottom half blue.
    frame = bytearray(CAP_W * CAP_H * 3)
    for y in range(CAP_H):
        color = (255, 0, 0) if y < CAP_H // 2 else (0, 0, 255)
        for x in range(CAP_W):
            i = (y * CAP_W + x) * 3
            frame[i], frame[i + 1], frame[i + 2] = color
    return bytes(frame)


def test_bottom_edge_sampling_favors_lower_band():
    layout = [{"name": "Bar", "region": [0.0, 0.0, 1.0, 1.0], "zones": [0]}]
    amb = Ambilight(lambda c: None, zones=1, runtime_dir=None, layout=layout)
    amb._options = {"saturation": 1.0, "sampling": "columns"}
    amb._update_targets(_split_frame())
    columns_blue = amb._targets[0][2]
    amb._options = {"saturation": 1.0, "sampling": "bottom_edge"}
    amb._update_targets(_split_frame())
    bottom = amb._targets[0]
    assert bottom[2] > columns_blue  # bottom-edge is bluer than full-column average
    assert bottom[2] > bottom[0]  # and blue-dominant


def test_global_color_sampling_averages_full_frame_for_all_logical_zones():
    layout = [
        {"name": "Left stick", "region": [0.0, 0.0, 0.30, 0.35], "zones": [0]},
        {"name": "Right stick", "region": [0.70, 0.33, 1.0, 0.67], "zones": [1]},
    ]
    amb = Ambilight(lambda c: None, zones=2, runtime_dir=None, layout=layout)
    amb._options = {"saturation": 1.0, "global_color": True}

    amb._update_targets(_split_frame())

    assert amb._targets == [(127, 0, 127), (127, 0, 127)]


def test_run_retries_when_source_missing(monkeypatch):
    # Cold boot: the gamescope node isn't there yet. The capture must keep retrying
    # (and stay alive) instead of giving up after one miss — otherwise ambient mode
    # never recovers without manual intervention.
    monkeypatch.setattr(ambilight_mod, "FAST_RETRY_INTERVAL", 0.001)
    monkeypatch.setattr(ambilight_mod, "RETRY_INTERVAL", 0.001)
    applied = []
    amb = Ambilight(lambda colors: applied.append(list(colors)), zones=4, runtime_dir=None)
    async def _no_node():
        return None

    amb._find_node = _no_node

    async def drive():
        amb.start({"fps": 10})
        task = amb._task
        await asyncio.sleep(0.05)
        assert amb.running
        assert amb.status == "no_source"
        assert len(applied) >= 2
        assert applied[0] == [(0, 0, 0)] * 4
        amb.stop()
        try:
            await task
        except asyncio.CancelledError:
            pass

    asyncio.run(drive())
    assert not amb.running


def test_run_shows_fallback_color_when_source_missing(monkeypatch):
    # No game source -> hold the user's last solid color instead of going dark.
    monkeypatch.setattr(ambilight_mod, "FAST_RETRY_INTERVAL", 0.001)
    monkeypatch.setattr(ambilight_mod, "RETRY_INTERVAL", 0.001)
    applied = []
    amb = Ambilight(lambda colors: applied.append(list(colors)), zones=4, runtime_dir=None)
    async def _no_node():
        return None

    amb._find_node = _no_node

    async def drive():
        amb.start({"fps": 10, "fallback": (10, 20, 30)})
        task = amb._task
        await asyncio.sleep(0.02)
        amb.stop()
        try:
            await task
        except asyncio.CancelledError:
            pass

    asyncio.run(drive())
    assert applied
    assert applied[0] == [(10, 20, 30)] * 4


def test_gst_command_uses_leaky_queue_before_scaling():
    cmd = _gst_command(68, 24, 14)
    assert "queue" in cmd
    assert "leaky=downstream" in cmd
    assert cmd.index("queue") < cmd.index("videoscale")
    assert "path=68" in cmd


def test_gst_command_supports_framerate_throttling():
    cmd = _gst_command(42, 32, 18, fps=15)
    assert "videorate" in cmd
    assert "framerate=15/1" in " ".join(cmd)


def test_capture_interval_respects_device_render_limit():
    amb = Ambilight(lambda colors: None, zones=1, runtime_dir=None, max_fps=10)
    amb._options = {"fps": 30}
    assert amb._capture_interval() == pytest.approx(0.1)


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


def test_avg_region_weights_vibrant_colors_over_muted():
    # 4 pixels: 3 muted grey-browns, 1 vibrant red laser
    frame = bytearray(4 * 3)
    # 3 muted pixels (100, 100, 100)
    for p in range(3):
        frame[p * 3] = 100
        frame[p * 3 + 1] = 100
        frame[p * 3 + 2] = 100
    # 1 vibrant red pixel (255, 0, 0)
    frame[3 * 3] = 255
    frame[3 * 3 + 1] = 0
    frame[3 * 3 + 2] = 0
    avg = avg_region(bytes(frame), 2, 2, (0.0, 0.0, 1.0, 1.0))
    # Red should strongly dominate over grey
    assert avg[0] > 180
    assert avg[1] < 60
    assert avg[2] < 60


def test_boost_saturation_increases_spread():
    base = (140, 120, 100)
    boosted = boost_saturation(base, 1.6)
    assert max(boosted) - min(boosted) > max(base) - min(base)


def test_boost_saturation_identity():
    assert boost_saturation((100, 100, 100), 1.5) == (100, 100, 100)


def test_lerp_moves_toward_target():
    assert lerp((0, 0, 0), (100, 100, 100), 0.5) == (50, 50, 50)
    assert lerp((0, 0, 0), (100, 0, 0), 1.0) == (100, 0, 0)


def test_adaptive_alpha_snaps_on_large_diff():
    base = 0.25
    # Small change stays close to base alpha
    subtle = adaptive_alpha(base, (50, 50, 50), (55, 55, 55))
    assert subtle == base

    # Huge explosion / flashbang increases alpha for instant response
    flash = adaptive_alpha(base, (0, 0, 0), (255, 255, 255))
    assert flash > base * 2.0
    assert flash <= 1.0


def test_alpha_for_mapping():
    assert alpha_for(0) == 1.0
    assert alpha_for(100) == 0.04
    assert 0.2 < alpha_for(75) < 0.3


def test_subdivide_splits_region_horizontally():
    subs = subdivide([0.0, 0.0, 1.0, 1.0], 2)
    assert len(subs) == 2
    assert subs[0] == (0.0, 0.0, 0.5, 1.0)
    assert subs[1] == (0.5, 0.0, 1.0, 1.0)


def test_subdivide_single_returns_region():
    assert subdivide([0.1, 0.2, 0.3, 0.4], 1) == [(0.1, 0.2, 0.3, 0.4)]


def test_dominant_colors_region_extracts_four_distinct_colors():
    from py_modules.ambilight import dominant_colors_region

    # Frame with 4 distinct quadrants: Red, Green, Blue, Yellow
    frame = bytearray(CAP_W * CAP_H * 3)
    half_w = CAP_W // 2
    half_h = CAP_H // 2
    for y in range(CAP_H):
        for x in range(CAP_W):
            if y < half_h and x < half_w:
                c = (255, 0, 0)
            elif y < half_h and x >= half_w:
                c = (0, 255, 0)
            elif y >= half_h and x < half_w:
                c = (0, 0, 255)
            else:
                c = (255, 255, 0)
            i = (y * CAP_W + x) * 3
            frame[i], frame[i + 1], frame[i + 2] = c

    dom = dominant_colors_region(bytes(frame), CAP_W, CAP_H, (0.0, 0.0, 1.0, 1.0), count=4)
    assert len(dom) == 4
    # All 4 colors must be represented
    for expected in [(255, 0, 0), (0, 255, 0), (0, 0, 255), (255, 255, 0)]:
        assert any(max(abs(c[j] - expected[j]) for j in range(3)) < 16 for c in dom)


def test_dominant_colors_pads_single_color():
    from py_modules.ambilight import dominant_colors_region

    frame = _solid_frame(CAP_W, CAP_H, (200, 50, 150))
    dom = dominant_colors_region(frame, CAP_W, CAP_H, (0.0, 0.0, 1.0, 1.0), count=4)
    assert len(dom) == 4
    assert all(c == (200, 50, 150) for c in dom)


def test_dominant_colors_black_fallback():
    from py_modules.ambilight import dominant_colors_region

    frame = _solid_frame(CAP_W, CAP_H, (0, 0, 0))
    dom = dominant_colors_region(frame, CAP_W, CAP_H, (0.0, 0.0, 1.0, 1.0), count=4)
    assert len(dom) == 4
    assert all(c == (0, 0, 0) for c in dom)


def test_update_targets_maps_left_and_right_dominant_colors_to_sticks():
    # Left half: Red, Green, Blue, Yellow in 4 strips
    # Right half: Cyan, Magenta, White, Orange in 4 strips
    frame = bytearray(CAP_W * CAP_H * 3)
    left_palette = [(255, 0, 0), (0, 255, 0), (0, 0, 255), (255, 255, 0)]
    right_palette = [(0, 255, 255), (255, 0, 255), (240, 240, 240), (255, 128, 0)]

    for y in range(CAP_H):
        band = min(3, y // (CAP_H // 4 + 1))
        for x in range(CAP_W):
            c = left_palette[band] if x < CAP_W // 2 else right_palette[band]
            i = (y * CAP_W + x) * 3
            frame[i], frame[i + 1], frame[i + 2] = c

    # 8 zones total: 4 for left stick, 4 for right stick
    layout = [
        {"name": "Left stick", "region": [0.0, 0.0, 0.30, 0.35], "zones": [0, 1, 2, 3]},
        {"name": "Right stick", "region": [0.70, 0.33, 1.0, 0.67], "zones": [4, 5, 6, 7]},
    ]
    amb = Ambilight(lambda c: None, zones=8, runtime_dir=None, layout=layout)
    amb._options = {"saturation": 1.0}
    amb._update_targets(bytes(frame))

    # Left stick zones (0..3) should match left palette colors
    for zone in range(4):
        target = amb._targets[zone]
        assert any(max(abs(target[j] - c[j]) for j in range(3)) < 20 for c in left_palette)

    # Right stick zones (4..7) should match right palette colors
    for zone in range(4, 8):
        target = amb._targets[zone]
        assert any(max(abs(target[j] - c[j]) for j in range(3)) < 20 for c in right_palette)


def test_update_targets_respects_average_algorithm():
    layout = [
        {"name": "Left stick", "region": [0.0, 0.0, 0.5, 1.0], "zones": [0, 1]},
    ]
    # Split frame: top half red (255, 0, 0), bottom half blue (0, 0, 255)
    amb = Ambilight(lambda c: None, zones=2, runtime_dir=None, layout=layout)
    amb._options = {"saturation": 1.0, "algorithm": "average"}
    amb._update_targets(_split_frame())

    # In average mode, both sub-regions span full height (y 0..1) so they average red & blue
    assert amb._targets[0] == (127, 0, 127)
    assert amb._targets[1] == (127, 0, 127)



