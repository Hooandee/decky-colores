import json

from py_modules.lighting_profiles import LightingProfileStore


DEFAULT_PROFILE = {
    "brightness": 80,
    "mode": "solid",
    "color": [255, 255, 255],
    "gradient": [[0, 196, 255], [136, 86, 255]],
    "gradient_speed": 30,
    "effect": {"id": "breathing", "speed": 50, "use_gradient": False},
    "ambilight": {"vividness": 27, "smoothing": 75, "fps": 10, "sampling": "columns"},
    "battery_breathe": True,
    "temperature_breathe": True,
}


def test_unseen_game_follows_global(tmp_path):
    store = LightingProfileStore(tmp_path / "profiles.json", DEFAULT_PROFILE)

    assert store.effective("42") == DEFAULT_PROFILE
    assert store.scope_state("42") == {
        "has_game_profile": False,
        "follows_global": True,
    }


def test_game_profile_is_seeded_and_preserved_while_following(tmp_path):
    store = LightingProfileStore(tmp_path / "profiles.json", DEFAULT_PROFILE)

    store.patch("game", "42", {"brightness": 35})
    store.set_follow_global("42", True)
    assert store.effective("42")["brightness"] == DEFAULT_PROFILE["brightness"]

    store.set_follow_global("42", False)
    assert store.effective("42")["brightness"] == 35


def test_global_patch_updates_inheriting_games(tmp_path):
    store = LightingProfileStore(tmp_path / "profiles.json", DEFAULT_PROFILE)

    store.patch("global", None, {"brightness": 45})

    assert store.effective("42")["brightness"] == 45


def test_nested_effect_and_ambilight_updates_are_merged(tmp_path):
    store = LightingProfileStore(tmp_path / "profiles.json", DEFAULT_PROFILE)

    store.patch("global", None, {"effect": {"speed": 20}})
    store.patch("global", None, {"ambilight": {"fps": 30}})

    profile = store.editable("global", None)
    assert profile["effect"] == {
        "id": "breathing",
        "speed": 20,
        "use_gradient": False,
    }
    assert profile["ambilight"] == {
        "vividness": 27,
        "smoothing": 75,
        "fps": 30,
        "sampling": "columns",
    }


def test_numeric_and_rgb_values_are_bounded(tmp_path):
    store = LightingProfileStore(tmp_path / "profiles.json", DEFAULT_PROFILE)

    store.patch(
        "global",
        None,
        {"brightness": -1, "color": [300, -2, 120], "gradient_speed": 101},
    )

    profile = store.editable("global", None)
    assert profile["brightness"] == 0
    assert profile["color"] == [255, 0, 120]
    assert profile["gradient_speed"] == 100


def test_corrupt_json_recovers_to_defaults(tmp_path):
    path = tmp_path / "profiles.json"
    path.write_text("not json{")

    store = LightingProfileStore(path, DEFAULT_PROFILE)

    assert store.effective(None) == DEFAULT_PROFILE


def test_legacy_migration_only_runs_for_a_missing_store(tmp_path):
    path = tmp_path / "profiles.json"
    first = LightingProfileStore(
        path,
        DEFAULT_PROFILE,
        legacy_profile={**DEFAULT_PROFILE, "brightness": 25},
    )
    assert first.effective(None)["brightness"] == 25

    second = LightingProfileStore(
        path,
        DEFAULT_PROFILE,
        legacy_profile={**DEFAULT_PROFILE, "brightness": 90},
    )
    assert second.effective(None)["brightness"] == 25


def test_schema_keeps_default_profile_slots(tmp_path):
    path = tmp_path / "profiles.json"
    store = LightingProfileStore(path, DEFAULT_PROFILE)
    store.patch("game", "42", {"brightness": 20})

    data = json.loads(path.read_text())
    assert set(data) == {"schemaVersion", "global", "apps"}
    assert data["global"]["activeProfile"] == "default"
    assert data["apps"]["42"]["activeProfile"] == "default"


def test_forget_removes_game_profile_and_restores_inheritance(tmp_path):
    store = LightingProfileStore(tmp_path / "profiles.json", DEFAULT_PROFILE)
    store.patch("game", "42", {"brightness": 20})
    store.set_follow_global("42", False)

    store.forget("42")

    assert store.scope_state("42") == {
        "has_game_profile": False,
        "follows_global": True,
    }
    assert store.effective("42") == DEFAULT_PROFILE
