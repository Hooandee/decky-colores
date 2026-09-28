import asyncio

import pytest

import py_modules.ambilight as ambilight_mod
from py_modules.ambilight import (
    Ambilight,
    CAP_W,
    CAP_H,
    CaptureBackend,
    _gst_command,
    _native_env,
    _read_latest_frames,
    _replace_with_latest,
    alpha_for,
    avg_region,
    boost_saturation,
    lerp,
    resolve_capture_backend,
    subdivide,
)


@pytest.fixture(autouse=True)
def _capture_backend(monkeypatch):
    backend = CaptureBackend("test", "/usr/bin/pw-dump", "/usr/bin/gst-launch-1.0")
    monkeypatch.setattr(ambilight_mod, "resolve_capture_backend", lambda: backend)


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
    cmd = _gst_command("/native/gst-launch-1.0", 68, 24, 14)
    assert cmd[0] == "/native/gst-launch-1.0"
    assert "queue" in cmd
    assert "leaky=downstream" in cmd
    assert cmd.index("queue") < cmd.index("videoscale")
    assert "path=68" in cmd


def test_native_env_removes_guest_library_and_gstreamer_paths(monkeypatch):
    monkeypatch.setattr(
        ambilight_mod,
        "user_env",
        lambda runtime_dir: {
            "XDG_RUNTIME_DIR": runtime_dir,
            "LD_LIBRARY_PATH": "/guest/lib",
            "LD_PRELOAD": "guest.so",
            "GST_PLUGIN_PATH": "/guest/gst",
            "GST_PLUGIN_SYSTEM_PATH": "/guest/system-gst",
        },
    )

    env = _native_env("/run/user/1000", "/native/gst")

    assert env["XDG_RUNTIME_DIR"] == "/run/user/1000"
    assert env["GST_PLUGIN_PATH"] == "/native/gst"
    assert "LD_LIBRARY_PATH" not in env
    assert "LD_PRELOAD" not in env
    assert "GST_PLUGIN_SYSTEM_PATH" not in env


def test_resolve_capture_backend_prefers_complete_system_backend(monkeypatch):
    paths = {
        "gst-launch-1.0": "/host/gst-launch-1.0",
        "gst-inspect-1.0": "/host/gst-inspect-1.0",
        "pw-dump": "/host/pw-dump",
    }
    monkeypatch.setattr(ambilight_mod, "_native_bin", paths.get)
    monkeypatch.setattr(ambilight_mod, "_has_pipewiresrc", lambda *args: True)

    backend = resolve_capture_backend()

    assert backend == CaptureBackend("system", "/host/pw-dump", "/host/gst-launch-1.0")


def test_resolve_capture_backend_uses_valid_arm64_bundle(monkeypatch):
    paths = {
        "gst-launch-1.0": "/host/gst-launch-1.0",
        "gst-inspect-1.0": "/host/gst-inspect-1.0",
        "pw-dump": None,
    }
    monkeypatch.setattr(ambilight_mod, "_native_bin", paths.get)
    monkeypatch.setattr(ambilight_mod, "_host_architecture", lambda: "aarch64")
    monkeypatch.setattr(ambilight_mod, "_valid_vendor_files", lambda: True)
    monkeypatch.setattr(ambilight_mod, "_has_pipewiresrc", lambda *args: True)

    backend = resolve_capture_backend()

    assert backend.name == "bundled-arm64"
    assert backend.gst_plugin_path == str(ambilight_mod._VENDOR_GST_DIR)


def test_resolve_capture_backend_rejects_bundle_on_wrong_architecture(monkeypatch):
    monkeypatch.setattr(
        ambilight_mod,
        "_native_bin",
        lambda name: None if name == "pw-dump" else f"/host/{name}",
    )
    monkeypatch.setattr(ambilight_mod, "_host_architecture", lambda: "x86_64")
    monkeypatch.setattr(ambilight_mod, "_valid_vendor_files", lambda: True)
    monkeypatch.setattr(ambilight_mod, "_has_pipewiresrc", lambda *args: True)

    assert resolve_capture_backend() is None


def test_vendor_validation_rejects_checksum_mismatch(tmp_path, monkeypatch):
    executable = tmp_path / "pw-dump"
    executable.write_bytes(b"unexpected")
    executable.chmod(0o755)
    monkeypatch.setattr(ambilight_mod, "_VENDOR_PW_DUMP", executable)
    monkeypatch.setattr(ambilight_mod, "_VENDOR_HASHES", {executable: "0" * 64})

    assert ambilight_mod._valid_vendor_files() is False


def test_replace_with_latest_keeps_only_latest_item():
    queue = asyncio.Queue(maxsize=1)
    _replace_with_latest(queue, b"old")
    _replace_with_latest(queue, b"new")
    assert queue.get_nowait() == b"new"


def test_frame_reader_keeps_latest_frame_while_stream_is_open():
    async def drive():
        reader = asyncio.StreamReader()
        queue = asyncio.Queue(maxsize=1)
        reader.feed_data(b"oldnew")
        task = asyncio.create_task(_read_latest_frames(reader, 3, queue))

        await asyncio.sleep(0)
        latest = queue.get_nowait()
        task.cancel()
        await asyncio.gather(task, return_exceptions=True)
        return latest

    assert asyncio.run(drive()) == b"new"


def test_frame_reader_drains_stream_and_reports_eof():
    async def drive():
        reader = asyncio.StreamReader()
        queue = asyncio.Queue(maxsize=1)
        reader.feed_data(b"oldnew")
        reader.feed_eof()

        await _read_latest_frames(reader, 3, queue)
        return queue.get_nowait()

    assert isinstance(asyncio.run(drive()), asyncio.IncompleteReadError)


def test_capture_interval_respects_device_render_limit():
    amb = Ambilight(lambda colors: None, zones=1, runtime_dir=None, max_fps=10)
    amb._options = {"fps": 30}
    assert amb._capture_interval() == pytest.approx(0.1)


def test_stop_and_wait_reaps_capture_before_returning():
    class FakeProcess:
        def __init__(self):
            self.killed = False
            self.waited = False

        def kill(self):
            self.killed = True

        async def wait(self):
            self.waited = True

    async def drive():
        amb = Ambilight(lambda colors: None, zones=1, runtime_dir=None)
        process = FakeProcess()
        task = asyncio.create_task(asyncio.sleep(60))
        amb._proc = process
        amb._task = task
        amb.status = "running"

        await amb.stop_and_wait()

        assert task.done()
        assert process.killed
        assert process.waited
        assert amb._task is None
        assert amb._proc is None
        assert amb.status == "idle"

    asyncio.run(drive())


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
