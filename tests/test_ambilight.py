import asyncio

import py_modules.ambilight as ambilight_mod
from py_modules.ambilight import (
    Ambilight,
    CAP_W,
    CAP_H,
    _gst_command,
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


def test_run_retries_when_source_missing(monkeypatch):
    # Cold boot: the gamescope node isn't there yet. The capture must keep retrying
    # (and stay alive) instead of giving up after one miss — otherwise ambient mode
    # never recovers without manual intervention.
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


def test_subdivide_splits_region_horizontally():
    subs = subdivide([0.0, 0.0, 1.0, 1.0], 2)
    assert len(subs) == 2
    assert subs[0] == (0.0, 0.0, 0.5, 1.0)
    assert subs[1] == (0.5, 0.0, 1.0, 1.0)


def test_subdivide_single_returns_region():
    assert subdivide([0.1, 0.2, 0.3, 0.4], 1) == [(0.1, 0.2, 0.3, 0.4)]
