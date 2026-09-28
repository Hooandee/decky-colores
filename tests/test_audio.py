import array
import asyncio

import py_modules.audio as audio_mod
from py_modules.audio import (
    AudioReactive,
    CAPTURE_LATENCY_MS,
    _capture_command,
    _level_from_pcm,
    _native_env,
)


def _pcm(amplitude, count=256):
    return array.array("h", [amplitude] * count).tobytes()


def test_level_zero_on_silence():
    assert _level_from_pcm(_pcm(0)) == 0.0


def test_level_uses_decibel_range_for_quiet_audio():
    assert _level_from_pcm(_pcm(80)) == 0.0
    assert _level_from_pcm(_pcm(800)) == 0.5
    assert _level_from_pcm(_pcm(8000)) == 1.0


def test_level_clamps_loud_signal():
    assert _level_from_pcm(_pcm(20000)) == 1.0


def test_level_empty_is_zero():
    assert _level_from_pcm(b"") == 0.0


def test_ease_fast_attack_slow_release():
    amb = AudioReactive(lambda c: None, zones=8, runtime_dir=None)
    # attack: jumps most of the way toward a louder target in one step
    up = amb._ease(1.0)
    assert up > 0.5
    # release: falls back only gradually
    amb._level = 1.0
    down = amb._ease(0.0)
    assert 0.5 < down < 1.0


def test_capture_command_sets_explicit_monitor_and_latency():
    command = _capture_command("/host/parec", "speakers.monitor")

    assert command[0] == "/host/parec"
    assert "--device=speakers.monitor" in command
    assert f"--latency-msec={CAPTURE_LATENCY_MS}" in command
    assert f"--process-time-msec={CAPTURE_LATENCY_MS}" in command


def test_native_env_removes_guest_library_paths(monkeypatch):
    monkeypatch.setattr(
        audio_mod,
        "user_env",
        lambda runtime_dir: {
            "XDG_RUNTIME_DIR": runtime_dir,
            "LD_LIBRARY_PATH": "/guest/lib",
            "LD_PRELOAD": "guest.so",
        },
    )

    env = _native_env("/run/user/1000")

    assert env == {"XDG_RUNTIME_DIR": "/run/user/1000"}


def test_default_monitor_uses_pactl_sink(monkeypatch):
    class FakeProcess:
        returncode = 0

        async def communicate(self):
            return b"alsa_output.speakers\n", b""

    async def fake_spawn(*args, **kwargs):
        return FakeProcess()

    monkeypatch.setattr(audio_mod, "_spawn", fake_spawn)
    monkeypatch.setattr(audio_mod, "_native_bin", lambda name: f"/host/{name}")
    audio = AudioReactive(lambda colors: None, zones=1, runtime_dir="/run/user/1000")

    assert asyncio.run(audio._default_monitor()) == "alsa_output.speakers.monitor"
