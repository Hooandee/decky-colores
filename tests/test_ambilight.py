import asyncio

import pytest

import py_modules.ambilight as ambilight_mod
from py_modules.ambilight import (
    Ambilight,
    CAP_W,
    CAP_H,
    _gst_command,
    _read_latest_frames,
    _replace_with_latest,
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


def test_host_capture_python_prefers_the_host_root(tmp_path):
    host = tmp_path / "proc/self/root"
    (host / "usr/bin").mkdir(parents=True)
    (host / "usr/bin/python3").write_text("")
    (host / "usr/lib64").mkdir(parents=True)
    (host / "usr/lib64/libpipewire-0.3.so.0").write_text("")
    assert ambilight_mod.host_capture_python(str(tmp_path)) == str(host / "usr/bin/python3")


def test_host_capture_python_needs_libpipewire(tmp_path):
    (tmp_path / "usr/bin").mkdir(parents=True)
    (tmp_path / "usr/bin/python3").write_text("")
    assert ambilight_mod.host_capture_python(str(tmp_path)) is None


def test_run_uses_the_pipewire_helper_without_pw_dump(monkeypatch):
    monkeypatch.setattr(ambilight_mod, "RETRY_INTERVAL", 0.001)
    commands = []

    async def fake_exec(*command, **kwargs):
        commands.append(command)
        raise OSError("stop here")

    async def no_pipewiresrc():
        return False

    async def must_not_run():
        raise AssertionError("pw-dump used")

    monkeypatch.setattr(ambilight_mod, "_gst_has_pipewiresrc", no_pipewiresrc)
    monkeypatch.setattr(ambilight_mod, "_helper_python", lambda: "/host/python3")
    monkeypatch.setattr(ambilight_mod.asyncio, "create_subprocess_exec", fake_exec)
    monkeypatch.setattr(ambilight_mod, "_backend", None)
    amb = Ambilight(lambda colors: None, zones=2, runtime_dir=None)
    amb._find_node = must_not_run

    async def drive():
        amb.start({"fps": 10})
        await asyncio.sleep(0.02)
        amb.stop()

    asyncio.run(drive())
    captures = [command for command in commands if command[0] != "wpctl"]
    assert captures[0] == (
        "/host/python3", ambilight_mod.PW_CAPTURE, "gamescope",
        str(ambilight_mod.CAP_W), str(ambilight_mod.CAP_H), "10",
    )


def test_the_capture_helper_is_only_offered_on_arm(monkeypatch):
    monkeypatch.setattr(ambilight_mod, "host_capture_python", lambda root="/": "/host/python3")
    monkeypatch.setattr(ambilight_mod.shutil, "which", lambda name: None)
    monkeypatch.setattr(ambilight_mod.device_tree, "is_arm", lambda root="/": False)
    assert ambilight_mod.capture_available() is False
    monkeypatch.setattr(ambilight_mod.device_tree, "is_arm", lambda root="/": True)
    assert ambilight_mod.capture_available() is True


def test_a_slow_gst_inspect_does_not_pin_the_gst_backend(monkeypatch):
    class Slow:
        killed = False

        async def wait(self):
            await asyncio.sleep(60)

        def kill(self):
            Slow.killed = True

    async def fake_exec(*command, **kwargs):
        return Slow()

    async def fast_wait_for(awaitable, timeout):
        awaitable.close()
        raise asyncio.TimeoutError

    monkeypatch.setattr(ambilight_mod.shutil, "which", lambda name: "/usr/bin/" + name)
    monkeypatch.setattr(ambilight_mod.asyncio, "create_subprocess_exec", fake_exec)
    monkeypatch.setattr(ambilight_mod.asyncio, "wait_for", fast_wait_for)
    assert asyncio.run(ambilight_mod._gst_has_pipewiresrc()) is False
    assert Slow.killed


WPCTL_STATUS = """Video
 ├─ Devices:
 │      47. Iris Encoder                        [v4l2]
 │
 ├─ Sinks:
 │
 ├─ Sources:
 │  *   64. (null)
 │      67. (null)
 │
 ├─ Filters:
 │
 └─ Streams:
        70. colores-ambilight
             71. input_1         < gamescope:capture_1	[active]

Settings
 └─ Default Configured Devices:
         0. Audio/Sink    alsa_output.platform-sound.HiFi__Speaker__sink
"""


def _wpctl_inspect(fields):
    lines = [f"id {fields.pop('id')}, type PipeWire:Interface:Node"]
    lines += [f'  * {key} = "{value}"' for key, value in fields.items()]
    return "\n".join(lines)


def test_wpctl_status_lists_only_the_video_sources():
    assert ambilight_mod.wpctl_video_sources(WPCTL_STATUS) == ["64", "67"]


def test_wpctl_props_reads_both_marked_and_plain_lines():
    props = ambilight_mod.wpctl_props('id 64, type x\n  * node.name = "gamescope"\n    node.async = "true"\n')
    assert props == {"node.name": "gamescope", "node.async": "true"}


def test_lease_companion_is_recognised_by_its_command_line(tmp_path):
    (tmp_path / "10").mkdir()
    (tmp_path / "10/cmdline").write_bytes(b"/usr/bin/gamescope\0--lease-connector\0DSI-1\0")
    (tmp_path / "20").mkdir()
    (tmp_path / "20/cmdline").write_bytes(b"gamescope\0--backend\0drm\0--drm-lease-client\0/tmp/gamescope-lease.sock\0")
    assert not ambilight_mod.is_lease_companion("10", proc_root=str(tmp_path))
    assert ambilight_mod.is_lease_companion("20", proc_root=str(tmp_path))
    assert not ambilight_mod.is_lease_companion("30", proc_root=str(tmp_path))


def _wpctl_world(monkeypatch, companions):
    inspect = {
        "64": _wpctl_inspect({"id": 64, "node.name": "gamescope", "media.class": "Video/Source",
                              "client.id": "63", "object.serial": "80"}),
        "67": _wpctl_inspect({"id": 67, "node.name": "gamescope", "media.class": "Video/Source",
                              "client.id": "66", "object.serial": "115"}),
        "63": _wpctl_inspect({"id": 63, "application.process.id": "2450"}),
        "66": _wpctl_inspect({"id": 66, "application.process.id": "6188"}),
    }
    calls = []

    async def output(self, argv):
        calls.append(argv)
        return WPCTL_STATUS if argv[1] == "status" else inspect[argv[2]]

    monkeypatch.setattr(Ambilight, "_output", output)
    monkeypatch.setattr(ambilight_mod, "is_lease_companion", lambda pid, proc_root="/proc": pid in companions)
    return calls


def test_two_gamescopes_capture_the_main_screen_not_the_lease_companion(monkeypatch):
    _wpctl_world(monkeypatch, companions={"6188"})
    amb = Ambilight(lambda colors: None, zones=2, runtime_dir=None)
    assert asyncio.run(amb._pipewire_target()) == "80"
    _wpctl_world(monkeypatch, companions={"2450"})
    assert asyncio.run(amb._pipewire_target()) == "115"


def test_without_wpctl_the_capture_keeps_targeting_by_name(monkeypatch):
    async def output(self, argv):
        return ""

    monkeypatch.setattr(Ambilight, "_output", output)
    amb = Ambilight(lambda colors: None, zones=2, runtime_dir=None)
    assert asyncio.run(amb._pipewire_target()) == "gamescope"


def test_a_lone_lease_companion_is_no_source(monkeypatch):
    calls = _wpctl_world(monkeypatch, companions={"6188"})
    status = WPCTL_STATUS.replace(" │  *   64. (null)\n", "")

    async def output(self, argv):
        calls.append(argv)
        if argv[1] == "status":
            return status
        return {
            "67": _wpctl_inspect({"id": 67, "node.name": "gamescope", "media.class": "Video/Source",
                                  "client.id": "66", "object.serial": "115"}),
            "66": _wpctl_inspect({"id": 66, "application.process.id": "6188"}),
        }[argv[2]]

    monkeypatch.setattr(Ambilight, "_output", output)
    amb = Ambilight(lambda colors: None, zones=2, runtime_dir=None)
    assert asyncio.run(amb._pipewire_target()) is None


def test_a_single_main_gamescope_is_targeted_by_serial(monkeypatch):
    status = WPCTL_STATUS.replace(" │      67. (null)\n", "")
    inspect = {
        "64": _wpctl_inspect({"id": 64, "node.name": "gamescope", "media.class": "Video/Source",
                              "client.id": "63", "object.serial": "80"}),
        "63": _wpctl_inspect({"id": 63, "application.process.id": "2450"}),
    }

    async def output(self, argv):
        return status if argv[1] == "status" else inspect[argv[2]]

    monkeypatch.setattr(Ambilight, "_output", output)
    monkeypatch.setattr(ambilight_mod, "is_lease_companion", lambda pid, proc_root="/proc": False)
    amb = Ambilight(lambda colors: None, zones=2, runtime_dir=None)
    assert asyncio.run(amb._pipewire_target()) == "80"


def test_letterbox_bars_are_left_out_of_the_sample():
    width, height = ambilight_mod.CAP_W, ambilight_mod.CAP_H
    frame = bytearray(width * height * 3)
    for y in range(3, height - 3):
        for x in range(width):
            i = (y * width + x) * 3
            frame[i : i + 3] = bytes((200, 40, 20))
    layout = [{"name": "Bottom", "region": [0.0, 0.72, 1.0, 1.0], "zones": [0]}]
    amb = Ambilight(lambda c: None, zones=1, runtime_dir=None, layout=layout)
    amb._options = {"saturation": 1.0}

    amb._update_targets(bytes(frame))
    assert amb._targets[0] == (200, 40, 20)
