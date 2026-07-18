import array

from py_modules.audio import _level_from_pcm, AudioReactive


def _pcm(amplitude, count=256):
    return array.array("h", [amplitude] * count).tobytes()


def test_level_zero_on_silence():
    assert _level_from_pcm(_pcm(0)) == 0.0


def test_level_scales_with_amplitude():
    # RMS of a constant signal is its amplitude; FULL_SCALE is 8000.
    assert _level_from_pcm(_pcm(4000)) == 0.5
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
