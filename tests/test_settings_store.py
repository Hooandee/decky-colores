import os

from py_modules.settings_store import SettingsStore

DEFAULTS = {"power": True, "brightness": 80, "color": [255, 255, 255]}


def test_load_returns_defaults_when_missing(tmp_path):
    store = SettingsStore(os.path.join(str(tmp_path), "state.json"))
    assert store.load(DEFAULTS) == DEFAULTS


def test_save_then_load_roundtrip(tmp_path):
    store = SettingsStore(os.path.join(str(tmp_path), "state.json"))
    store.save({"power": False, "brightness": 30, "color": [10, 20, 30]})
    assert store.load(DEFAULTS) == {"power": False, "brightness": 30, "color": [10, 20, 30]}


def test_load_ignores_unknown_keys_and_fills_defaults(tmp_path):
    path = os.path.join(str(tmp_path), "state.json")
    store = SettingsStore(path)
    with open(path, "w") as handle:
        handle.write('{"brightness": 50, "junk": 1}')
    loaded = store.load(DEFAULTS)
    assert loaded["brightness"] == 50
    assert loaded["power"] is True
    assert "junk" not in loaded


def test_load_handles_corrupt_file(tmp_path):
    path = os.path.join(str(tmp_path), "state.json")
    with open(path, "w") as handle:
        handle.write("not json{")
    assert SettingsStore(path).load(DEFAULTS) == DEFAULTS
