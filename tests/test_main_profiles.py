import asyncio
import json

import pytest

pytest_plugins = ("test_main_routing",)


@pytest.fixture
def profile_plugin(main_module, monkeypatch, tmp_path):
    main_module.decky.DECKY_PLUGIN_SETTINGS_DIR = str(tmp_path)
    state = {**main_module.DEFAULTS, "color": [12, 34, 56], "brightness": 64}
    (tmp_path / "state.json").write_text(json.dumps(state))

    controller = type(
        "Controller",
        (),
        {
            "available": True,
            "supports_hardware_effects": lambda self: True,
            "supports_per_zone": lambda self: False,
            "apply_solid": lambda *args: True,
            "apply_zones": lambda *args: True,
            "apply_hardware_effect": lambda *args: True,
        },
    )()

    def setup(plugin, _context):
        plugin._device = {"name": "Test"}
        plugin._capabilities = {
            "zones": 1,
            "color": True,
            "effects": True,
            "ambilight": False,
            "audioMode": True,
            "states": {},
        }
        plugin._zones = 1
        plugin._controller = controller
        plugin._power_led = None
        plugin._cpu_sampler = type("Cpu", (), {"percent": lambda self: None})()
        plugin._engine = type("Engine", (), {"stop": lambda self: None})()
        plugin._ambilight = type(
            "Ambient", (), {"stop": lambda self: None, "status": "idle"}
        )()
        plugin._audio = type(
            "Audio", (), {"stop": lambda self: None, "status": "idle"}
        )()

    monkeypatch.setattr(main_module.Plugin, "_setup_device", setup)
    monkeypatch.setattr(main_module.Plugin, "_build_context", lambda self: {})
    monkeypatch.setattr(main_module, "charger_online", lambda: True)
    monkeypatch.setattr(main_module, "battery_level", lambda: 100)
    monkeypatch.setattr(main_module, "apu_temperature", lambda: None)
    plugin = main_module.Plugin()
    plugin._init()
    monkeypatch.setattr(plugin, "_apply", lambda: None)
    return plugin


def test_existing_visual_state_becomes_global_profile(profile_plugin):
    assert profile_plugin._profiles.editable("global", None)["color"] == [12, 34, 56]


def test_global_power_gates_game_profile(profile_plugin):
    profile_plugin._profiles.patch("game", "42", {"brightness": 20})
    profile_plugin._profiles.set_follow_global("42", False)
    profile_plugin._base_settings["power"] = False
    profile_plugin._current_app_key = "42"

    profile_plugin._sync_effective_profile()

    assert profile_plugin._settings["brightness"] == 20
    assert profile_plugin._effective_power() is False


def test_game_profile_never_leaks_into_legacy_state(profile_plugin, tmp_path):
    async def drive():
        await profile_plugin.patch_profile("game", "42", {"brightness": 20})
        await profile_plugin.set_profile_follow_global("42", False)
        await profile_plugin.set_current_app("42")

    asyncio.run(drive())

    legacy = json.loads((tmp_path / "state.json").read_text())
    assert legacy["brightness"] == 64
    assert profile_plugin._settings["brightness"] == 20


def test_set_current_app_deduplicates_apply(profile_plugin):
    applies = []
    profile_plugin._apply = lambda: applies.append(True)

    async def drive():
        await profile_plugin.set_current_app("42")
        await profile_plugin.set_current_app("42")

    asyncio.run(drive())

    assert len(applies) == 1


def test_unsupported_mode_falls_back_without_overwriting_profile(profile_plugin):
    profile_plugin._profiles.patch("game", "42", {"mode": "ambient"})
    profile_plugin._profiles.set_follow_global("42", False)
    profile_plugin._current_app_key = "42"

    profile_plugin._sync_effective_profile()

    assert profile_plugin._settings["mode"] == "solid"
    assert profile_plugin._profiles.editable("game", "42")["mode"] == "ambient"


def test_profile_state_is_future_ready_and_contains_no_name(profile_plugin):
    state = asyncio.run(profile_plugin.get_profile_state("game", "42"))

    assert state["activeProfile"] == "default"
    assert state["appKey"] == "42"
    assert "name" not in state


def test_game_profile_never_persists_startup(profile_plugin):
    calls = []
    profile_plugin._controller.save_startup = lambda: calls.append(True)
    profile_plugin._profiles.patch("game", "42", {"mode": "solid"})
    profile_plugin._profiles.set_follow_global("42", False)
    profile_plugin._current_app_key = "42"
    profile_plugin._sync_effective_profile()

    profile_plugin._maybe_persist_startup()

    assert getattr(profile_plugin, "_startup_task", None) is None
    assert calls == []


def test_report_contains_only_profile_aggregate(profile_plugin):
    profile_plugin._profiles.patch("game", "private-app-key", {"brightness": 20})

    serialized = json.dumps(profile_plugin._report_stores())

    assert "private-app-key" not in serialized
    assert '"profiles_configured": 1' in serialized
