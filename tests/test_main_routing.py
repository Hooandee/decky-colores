import json
import asyncio
import sys
import threading
import time
import types

import pytest

CUSTOM_SENSOR_BANDS = [
    {"min": 90, "color": {"r": 1, "g": 2, "b": 3}},
    {"min": 70, "color": {"r": 4, "g": 5, "b": 6}},
    {"min": 50, "color": {"r": 7, "g": 8, "b": 9}},
    {"min": 25, "color": {"r": 10, "g": 11, "b": 12}},
    {"min": 0, "color": {"r": 13, "g": 14, "b": 15}},
]
PARSED_SENSOR_BANDS = (
    (90, (1, 2, 3)),
    (70, (4, 5, 6)),
    (50, (7, 8, 9)),
    (25, (10, 11, 12)),
    (0, (13, 14, 15)),
)


@pytest.fixture
def main_module():
    saved = sys.modules.get("decky")
    stub = types.ModuleType("decky")
    stub.logger = types.SimpleNamespace(
        info=lambda *a, **k: None,
        error=lambda *a, **k: None,
        warning=lambda *a, **k: None,
    )
    stub.DECKY_USER = "deck"
    stub.DECKY_PLUGIN_SETTINGS_DIR = "/tmp"
    sys.modules["decky"] = stub
    sys.modules.pop("main", None)
    import main

    yield main
    sys.modules.pop("main", None)
    if saved is None:
        sys.modules.pop("decky", None)
    else:
        sys.modules["decky"] = saved


class FakeController:
    def __init__(self, hw=True, per_zone=False):
        self._hw = hw
        self._per_zone = per_zone
        self.calls = []
        self.reconnected = False
        self.invalidated = False
        self.available = True
        self.led_path = None
        self.last_error = None

    def invalidate(self):
        self.invalidated = True

    def supports_hardware_effects(self):
        return self._hw

    def supports_per_zone(self):
        return self._per_zone

    def apply_zones(self, zones, brightness, power):
        self.calls.append(("zones", list(zones), brightness, power))
        return True

    def apply_solid(self, color, brightness, power):
        self.calls.append(("solid", tuple(color), brightness, power))
        return True

    def apply_hardware_effect(self, effect_id, color, speed, brightness, power):
        self.calls.append(
            ("hw_effect", effect_id, tuple(color), speed, brightness, power)
        )
        return True

    def reconnect(self):
        self.reconnected = True
        return True

    def save_startup(self):
        self.calls.append(("save_startup",))
        return True

    def set_sleep_charging_indicator(self, enabled):
        self.calls.append(("sleep_charging", enabled))
        return True

    def supports_sleep_charging_indicator(self):
        return True


class FakeEngine:
    def __init__(self):
        self.events = []

    def stop(self):
        self.events.append(("stop",))

    @property
    def running(self):
        return False

    def set_static(self, zone_colors):
        self.events.append(("static", list(zone_colors)))

    def start_effect(self, effect_id, speed, params):
        self.events.append(("effect", effect_id, params))

    def start_battery(self, state_fn):
        self.events.append(("battery", state_fn()))

    def start_temperature(self, state_fn):
        self.events.append(("temperature", state_fn()))


class FakeAmbilight:
    def __init__(self):
        self.events = []
        self.status = "idle"

    def stop(self):
        self.events.append(("stop",))

    @property
    def running(self):
        return False

    async def stop_and_wait(self):
        self.events.append(("stop_and_wait",))

    def start(self, cfg):
        self.events.append(("start", cfg))


class FakeAudio:
    def __init__(self):
        self.events = []
        self.status = "idle"
        self.level = 0.0

    def stop(self):
        self.events.append(("stop",))

    def start(self, options=None):
        self.events.append(("start", options))


class FakeHhdRgb:
    def __init__(self, reads=None, writes=None):
        self.reads = list(reads or [])
        self.writes = list(writes or [])
        self.calls = []

    def read_rgb(self):
        self.calls.append(("read",))
        return self.reads.pop(0) if self.reads else False

    def set_rgb(self, enabled):
        self.calls.append(("set", enabled))
        return self.writes.pop(0) if self.writes else True


class FakeSuspendMonitor:
    def __init__(self):
        self.events = []

    def start(self):
        self.events.append(("start",))

    async def stop_and_wait(self):
        self.events.append(("stop_and_wait",))

    def stop_now(self):
        self.events.append(("stop_now",))

    def diagnostics(self):
        return {
            "running": True,
            "connected": True,
            "inhibitor_armed": True,
            "sleeping": False,
            "last_error": None,
        }


class FakeSuspendConnection:
    def __init__(self):
        self.callback = None
        self.ready = asyncio.Event()
        self.disconnected = asyncio.Event()
        self.inhibitors = iter((41, 42, 43))
        self.acquire_count = 0

    async def connect(self, callback):
        self.callback = callback
        return self

    async def acquire_inhibitor(self):
        self.acquire_count += 1
        self.ready.set()
        return next(self.inhibitors)

    async def wait_closed(self):
        await self.disconnected.wait()

    def disconnect(self):
        self.disconnected.set()

    def emit(self, sleeping):
        self.callback(sleeping)


def _plugin(
    main_module,
    mode,
    effect=None,
    hw=True,
    power=True,
    per_zone=False,
    per_controller=False,
    hhd_takeover=False,
):
    p = main_module.Plugin()
    p._ready = True
    p._stopping = False
    p._suspend_prepared = False
    p._suspend_lock = asyncio.Lock()
    p._resume_lock = asyncio.Lock()
    p._resume_handled_at = None
    p._hhd_rgb_lock = asyncio.Lock()
    p._hhd_rgb_status = None
    p._controller = FakeController(hw, per_zone)
    p._engine = FakeEngine()
    p._ambilight = FakeAmbilight()
    p._audio = FakeAudio()
    p._zones = 2
    p._capabilities = {
        "zones": 2,
        "perControllerColor": per_controller,
        "perZone": per_zone,
        "conflictsWithSystemRgb": hhd_takeover,
        "hhdRgbTakeover": hhd_takeover,
    }
    p._settings = {
        "power": power,
        "brightness": 80,
        "mode": mode,
        "color": [255, 0, 0],
        "gradient": [[0, 196, 255], [136, 86, 255]],
        "gradient_speed": 30,
        "effect": effect or {"id": "breathing", "speed": 50, "use_gradient": False},
        "ambilight": {"vividness": 27, "smoothing": 75, "fps": 10},
        "force_control": False,
        "hhd_rgb_restore": None,
    }
    p._hhd_rgb = FakeHhdRgb()
    return p


def _hhd_plugin(
    main_module, *, reads=None, writes=None, restore=None, force=False, **plugin_options
):
    plugin = _plugin(main_module, "solid", hhd_takeover=True, **plugin_options)
    plugin._settings.update(force_control=force, hhd_rgb_restore=restore)
    plugin._hhd_rgb = FakeHhdRgb(reads, writes)
    saved = []
    plugin._store = types.SimpleNamespace(save=lambda state: saved.append(dict(state)))
    return plugin, saved


def test_submit_report_forwards_feature_kind(main_module, monkeypatch):
    plugin = main_module.Plugin()
    plugin._ready = True
    plugin._redact_ids = lambda: ("/home/deck", "deck")
    captured = {}

    async def build_bundle(categories, text, home, hostname, kind):
        captured.update(
            categories=categories,
            text=text,
            home=home,
            hostname=hostname,
            kind=kind,
        )
        return {"app": "colores", "kind": kind, "logs": ["context"]}

    plugin._build_report_bundle = build_bundle
    monkeypatch.setattr(
        main_module.report_client,
        "submit",
        lambda *_args, **_kwargs: {"ok": True, "code": "COL-TEST"},
    )

    result = asyncio.run(
        plugin.submit_report(["effects"], "Please add an effect", "feature")
    )

    assert result == {"ok": True, "code": "COL-TEST", "issue_url": None}
    assert captured == {
        "categories": ["effects"],
        "text": "Please add an effect",
        "home": "/home/deck",
        "hostname": "deck",
        "kind": "feature",
    }


def test_report_runtime_diagnostics_exposes_lifecycle_and_rgb_ownership(main_module):
    plugin = _plugin(main_module, "ambient", hhd_takeover=True)
    plugin._suspend_monitor = FakeSuspendMonitor()
    plugin._suspend_prepared = True
    plugin._settings.update(force_control=True, hhd_rgb_restore=True)
    plugin._hhd_rgb_status = "disabled"

    runtime = plugin._report_runtime_diagnostics()

    assert runtime == {
        "suspend": {
            "running": True,
            "connected": True,
            "inhibitor_armed": True,
            "sleeping": False,
            "last_error": None,
            "prepared": True,
        },
        "render": {
            "engine_running": False,
            "ambilight_running": False,
            "ambilight_status": "idle",
            "audio_status": "idle",
        },
        "hhd_rgb": {
            "takeover_supported": True,
            "force_control": True,
            "status": "disabled",
            "restore_pending": True,
        },
    }


def test_report_bundle_wires_error_summary_and_runtime_diagnostics(
    main_module, monkeypatch, tmp_path
):
    plugin = _plugin(main_module, "solid", hhd_takeover=True)
    plugin._suspend_monitor = FakeSuspendMonitor()
    plugin._device = {"name": "ROG Ally", "board": "RC71L", "product": "RC71L"}
    log = tmp_path / "colores.log"
    log.write_text("[ERROR] failed at /home/deck/private\nordinary frame\n")
    monkeypatch.setattr(
        main_module.decky,
        "DECKY_PLUGIN_LOG_DIR",
        str(tmp_path),
        raising=False,
    )
    plugin._report_environment = lambda: {"os": "SteamOS"}
    plugin._report_stores = lambda: {}
    plugin._run_capture = lambda command: None

    async def get_state():
        return {"device": plugin._device, "capabilities": plugin._capabilities}

    plugin.get_state = get_state

    bundle = asyncio.run(
        plugin._build_report_bundle(
            ["color"], "does not light", "/home/deck", "handheld", "bug"
        )
    )

    assert bundle["errors"] == [{
        "name": "colores.log",
        "text": "[ERROR] failed at ~/private",
    }]
    assert bundle["runtime"]["suspend"]["connected"] is True
    assert bundle["capabilities"]["hhd_rgb_takeover"] is True


@pytest.mark.parametrize(
    "power,charger_only,ac_online,expected",
    [
        (False, False, True, False),
        (False, True, True, False),
        (True, False, False, True),
        (True, True, True, True),
        (True, True, False, False),
    ],
)
def test_effective_power_truth_table(main_module, power, charger_only, ac_online, expected):
    p = _plugin(main_module, "solid", power=power)
    p._settings["charger_only"] = charger_only
    p._ac_online = ac_online
    assert p._effective_power() is expected


def test_persist_startup_debounced_and_gated(main_module, monkeypatch):
    monkeypatch.setattr(main_module, "STARTUP_PERSIST_DELAY", 0.01)

    async def drive():
        # Static mode + enabled: schedules and commits exactly once after the delay.
        p = _plugin(main_module, "solid", hw=False, per_zone=True)
        p._settings["remember_startup"] = True
        p._maybe_persist_startup()
        assert p._startup_task is not None
        await p._startup_task
        assert p._controller.calls.count(("save_startup",)) == 1

        # Debounce: a burst of rapid changes (dragging the color wheel) commits ONCE,
        # never once per change — this is the flash-wear protection.
        p._maybe_persist_startup()
        p._maybe_persist_startup()
        p._maybe_persist_startup()
        await p._startup_task
        assert p._controller.calls.count(("save_startup",)) == 2  # one more, total two

        # Effect mode: a per-frame color is never a startup color.
        p2 = _plugin(main_module, "effect", hw=False, per_zone=True)
        p2._settings["remember_startup"] = True
        p2._maybe_persist_startup()
        assert getattr(p2, "_startup_task", None) is None

        # Toggle off: never persist, even in a static mode.
        p3 = _plugin(main_module, "solid", hw=False, per_zone=True)
        p3._settings["remember_startup"] = False
        p3._maybe_persist_startup()
        assert getattr(p3, "_startup_task", None) is None

    asyncio.run(drive())


def test_vu_mode_starts_audio_capture(main_module):
    p = _plugin(main_module, "vu", hw=False, per_zone=True)
    p._apply()
    assert any(e[0] == "start" for e in p._audio.events)
    assert ("stop",) in p._ambilight.events
    assert p._engine.events and p._engine.events[-1][0] == "stop"


def test_non_vu_mode_stops_audio(main_module):
    p = _plugin(main_module, "solid", hw=False, per_zone=True)
    p._apply()
    assert ("stop",) in p._audio.events


def test_charger_only_on_battery_gates_per_zone_off(main_module):
    # Charger-only active + on battery: the per-zone path writes black WITHOUT
    # changing the stored mode/color (it's a gate, not a config change).
    p = _plugin(main_module, "solid", hw=False, per_zone=True)
    p._settings["charger_only"] = True
    p._ac_online = False
    p._apply()
    assert ("static", [(0, 0, 0)] * p._zones) in p._engine.events
    assert p._settings["mode"] == "solid"


def test_charger_only_plugged_in_renders_normally(main_module):
    p = _plugin(main_module, "solid", hw=False, per_zone=True)
    p._settings["charger_only"] = True
    p._ac_online = True
    p._apply()
    assert ("static", [(255, 0, 0)] * p._zones) in p._engine.events


def test_charger_watch_reapplies_only_on_edge(main_module, monkeypatch):
    # The watcher must reapply when the plug state flips (and the gate is on), and
    # must NOT touch anything when the feature is off.
    monkeypatch.setattr(main_module, "CHARGER_POLL_INTERVAL", 0.001)
    p = _plugin(main_module, "solid", hw=False, per_zone=True)
    p._settings["charger_only"] = True
    p._ac_online = True
    states = iter([False, False, True])
    monkeypatch.setattr(main_module, "charger_online", lambda: next(states, True))

    async def drive():
        task = asyncio.create_task(p._charger_watch())
        for _ in range(200):
            await asyncio.sleep(0.005)
            if p._ac_online and any(e[0] == "static" for e in p._engine.events):
                break
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass

    asyncio.run(drive())
    assert p._ac_online is True
    assert any(e[0] == "static" for e in p._engine.events)


def test_charger_watch_reads_power_supply_off_the_event_loop(main_module, monkeypatch):
    monkeypatch.setattr(main_module, "CHARGER_POLL_INTERVAL", 0.001)
    p = _plugin(main_module, "solid", hw=False, per_zone=True)
    p._ac_online = True
    main_thread = threading.get_ident()
    readers = []

    def slow_battery():
        readers.append(threading.get_ident())
        time.sleep(0.2)
        return 55

    monkeypatch.setattr(main_module, "battery_level", slow_battery)
    monkeypatch.setattr(main_module, "apu_temperature", lambda: 61.0)
    monkeypatch.setattr(main_module, "charger_online", lambda: True)

    async def drive():
        ticks = 0

        async def heartbeat():
            nonlocal ticks
            while True:
                await asyncio.sleep(0.01)
                ticks += 1

        beat = asyncio.create_task(heartbeat())
        task = asyncio.create_task(p._charger_watch())
        await asyncio.sleep(0.25)
        task.cancel()
        beat.cancel()
        await asyncio.gather(task, beat, return_exceptions=True)
        return ticks

    ticks = asyncio.run(drive())
    assert readers and all(reader != main_thread for reader in readers)
    assert ticks >= 10
    assert p._battery_level == 55
    assert p._apu_temp == 61.0


def test_wave_on_single_color_device_uses_hardware_effect(main_module):
    # Legion Go S-like: hardware effects, single-color zones, no per-controller.
    # Wave must use the native firmware effect (a single solid color), not the
    # software loop that would collapse to a flat color — and the UI has no
    # gradient tab to point at.
    p = _plugin(
        main_module,
        "effect",
        {"id": "wave", "speed": 50, "use_gradient": False},
        hw=True,
        per_zone=False,
        per_controller=False,
    )
    p._apply()
    assert any(c[0] == "hw_effect" and c[1] == "wave" for c in p._controller.calls)
    assert not any(e[0] == "effect" for e in p._engine.events)


def test_wave_on_per_zone_device_runs_in_software(main_module):
    # Ally/MSI-like: per-zone capable -> software paints the spatial gradient wave.
    p = _plugin(
        main_module,
        "effect",
        {"id": "wave", "speed": 50, "use_gradient": False},
        hw=False,
        per_zone=True,
    )
    p._apply()
    effect_event = next(e for e in p._engine.events if e[0] == "effect")
    assert effect_event[1] == "wave"
    assert effect_event[2]["stops"] == [(0, 196, 255), (136, 86, 255)]
    assert not any(c[0] == "hw_effect" for c in p._controller.calls)


def test_wave_on_per_controller_device_runs_in_software(main_module):
    # Legion Go/Go 2-like: per-controller color -> software paints a two-color
    # wave across the left/right controllers using the gradient stops.
    p = _plugin(
        main_module,
        "effect",
        {"id": "wave", "speed": 50, "use_gradient": False},
        hw=True,
        per_zone=False,
        per_controller=True,
    )
    p._apply()
    assert any(e[0] == "effect" and e[1] == "wave" for e in p._engine.events)
    assert not any(c[0] == "hw_effect" for c in p._controller.calls)


def test_spiral_on_legion_uses_firmware_effect(main_module):
    # Legion Go (hardware effects): spiral is the device's native firmware effect.
    p = _plugin(
        main_module,
        "effect",
        {"id": "spiral", "speed": 50, "use_gradient": False},
        hw=True,
        per_zone=False,
        per_controller=True,
    )
    p._apply()
    assert any(c[0] == "hw_effect" and c[1] == "spiral" for c in p._controller.calls)
    assert not any(e[0] == "effect" for e in p._engine.events)


def test_spiral_on_legion_ignores_stale_gradient_and_uses_firmware_effect(main_module):
    p = _plugin(
        main_module,
        "effect",
        {"id": "spiral", "speed": 50, "use_gradient": True},
        hw=True,
        per_zone=False,
        per_controller=False,
    )

    p._apply()

    assert any(c[0] == "hw_effect" and c[1] == "spiral" for c in p._controller.calls)
    assert not any(e[0] == "effect" for e in p._engine.events)


def test_spiral_on_ally_runs_in_software(main_module):
    # Ally (no hardware effects): spiral spins the user's gradient in software.
    p = _plugin(
        main_module,
        "effect",
        {"id": "spiral", "speed": 50, "use_gradient": False},
        hw=False,
        per_zone=True,
    )
    p._apply()
    assert any(e[0] == "effect" and e[1] == "spiral" for e in p._engine.events)
    assert not any(c[0] == "hw_effect" for c in p._controller.calls)


def test_breathing_with_use_gradient_runs_in_software(main_module):
    p = _plugin(main_module, "effect", {"id": "breathing", "speed": 50, "use_gradient": True})
    p._apply()
    assert any(e[0] == "effect" and e[1] == "breathing" for e in p._engine.events)
    assert not any(c[0] == "hw_effect" for c in p._controller.calls)


def test_plain_breathing_uses_hardware_effect(main_module):
    p = _plugin(main_module, "effect", {"id": "breathing", "speed": 50, "use_gradient": False})
    p._apply()
    assert any(c[0] == "hw_effect" and c[1] == "breathing" for c in p._controller.calls)
    assert not any(e[0] == "effect" for e in p._engine.events)


def test_hardware_effect_receives_active_brightness(main_module):
    p = _plugin(main_module, "effect", {"id": "spiral", "speed": 50, "use_gradient": False})
    p._settings["brightness"] = 5

    p._apply()

    assert ("hw_effect", "spiral", (255, 0, 0), 50, 5, True) in p._controller.calls


def test_ambient_runs_capture_on_hardware_device(main_module):
    p = _plugin(main_module, "ambient")
    p._apply()
    assert any(e[0] == "start" for e in p._ambilight.events)
    assert not any(c[0] == "solid" for c in p._controller.calls)


@pytest.mark.parametrize(
    ("per_zone", "per_controller", "global_color"),
    [(False, False, True), (True, False, False), (False, True, False)],
)
def test_ambient_selects_sampling_for_device_capabilities(
    main_module, per_zone, per_controller, global_color
):
    p = _plugin(
        main_module,
        "ambient",
        per_zone=per_zone,
        per_controller=per_controller,
    )

    p._apply()

    start = next(event for event in p._ambilight.events if event[0] == "start")
    assert start[1]["global_color"] is global_color


def test_ambient_vividness_maps_to_saturation_factor(main_module):
    p = _plugin(main_module, "ambient")
    p._settings["ambilight"]["vividness"] = 100

    p._apply()

    start = next(event for event in p._ambilight.events if event[0] == "start")
    assert start[1]["saturation"] == 2.5


def test_set_ambilight_clamps_and_persists_vividness(main_module):
    p = _plugin(main_module, "ambient")
    saved = {}
    p._store = types.SimpleNamespace(save=lambda state: saved.update({"value": dict(state)}))

    asyncio.run(p.set_ambilight(150, 60, 20))

    ambilight = saved["value"]["ambilight"]
    assert ambilight["vividness"] == 100
    assert "saturation" not in ambilight


@pytest.mark.parametrize(
    ("legacy_saturation", "expected_vividness"),
    [(100, 0), (140, 27), (250, 100), (255, 100)],
)
def test_ambilight_settings_migrate_legacy_saturation_to_vividness(
    main_module, legacy_saturation, expected_vividness
):
    settings = main_module._normalize_ambilight_settings(
        {"saturation": legacy_saturation}
    )

    assert settings["vividness"] == expected_vividness
    assert "saturation" not in settings


def test_gradient_on_single_color_device_animates_crossfade(main_module):
    # Legion-like: hardware effects, no per-zone -> animated crossfade, not a
    # static spatial gradient (which would only show the last color)
    p = _plugin(main_module, "gradient", per_zone=False)
    p._apply()
    sweep = [e for e in p._engine.events if e[0] == "effect" and e[1] == "gradient_sweep"]
    assert sweep
    assert sweep[0][2]["stops"] == [(0, 196, 255), (136, 86, 255)]
    assert not any(c[0] == "zones" for c in p._controller.calls)


def test_gradient_on_per_zone_device_stays_spatial(main_module):
    # Ally/MSI-like: per-zone capable -> real spatial gradient, unchanged behavior
    p = _plugin(main_module, "gradient", hw=True, per_zone=True)
    p._apply()
    zone_calls = [c for c in p._controller.calls if c[0] == "zones"]
    assert zone_calls
    assert zone_calls[0][1] == [(0, 196, 255), (136, 86, 255)]
    assert not any(e[0] == "effect" for e in p._engine.events)


def test_reconnect_resets_controller_and_reapplies(main_module):
    p = _plugin(main_module, "solid")
    ok = asyncio.run(p.reconnect())
    assert ok is True
    assert p._controller.reconnected is True
    assert any(c[0] == "solid" for c in p._controller.calls)


def test_reconnect_reasserts_sleep_charging_policy(main_module):
    p = _plugin(main_module, "solid")
    p._sleep_charging_controller = p._controller
    p._capabilities["sleepChargingIndicator"] = True
    p._settings["sleep_charging_indicator"] = True

    assert asyncio.run(p.reconnect()) is True

    assert ("sleep_charging", True) in p._controller.calls


def test_reconnect_restarts_ambient_capture(main_module):
    p = _plugin(main_module, "ambient", hw=False, per_zone=True)
    p._ambilight.events.clear()
    ok = asyncio.run(p.reconnect())
    assert ok is True
    assert [event[0] for event in p._ambilight.events] == ["stop", "start"]


def test_prepare_suspend_stops_capture_without_changing_user_intent(main_module):
    p = _plugin(main_module, "ambient", power=True, per_zone=True)

    asyncio.run(p.prepare_suspend())

    assert p._ambilight.events == [("stop_and_wait",)]
    assert p._audio.events == [("stop",)]
    assert p._settings["mode"] == "ambient"
    assert p._settings["power"] is True


def test_prepare_suspend_is_idempotent_when_hooks_overlap(main_module):
    async def drive():
        p = _plugin(main_module, "ambient", power=True, per_zone=True)

        await asyncio.gather(p.prepare_suspend(), p.prepare_suspend())

        assert p._ambilight.events == [("stop_and_wait",)]

    asyncio.run(drive())


def test_prepare_suspend_reasserts_sleep_charging_policy(main_module):
    async def drive():
        p = _plugin(main_module, "solid", power=True)
        p._sleep_charging_controller = p._controller
        p._capabilities["sleepChargingIndicator"] = True
        p._settings["sleep_charging_indicator"] = True

        await p.prepare_suspend()

        assert ("sleep_charging", True) in p._controller.calls

    asyncio.run(drive())


def test_prepare_suspend_reaps_battery_loop_without_changing_user_intent(main_module):
    async def drive():
        p = _plugin(main_module, "battery", power=True, per_zone=True)
        p._engine = main_module.EffectEngine(p._render, p._zones)
        p._engine.start_battery(p._battery_state)
        task = p._engine._task
        await asyncio.sleep(0)

        await p.prepare_suspend()

        assert task.done()
        assert task.cancelled()
        assert not p._engine.running
        assert p._settings["mode"] == "battery"
        assert p._settings["power"] is True

    asyncio.run(drive())


def test_prepare_suspend_blocks_late_battery_writes(main_module):
    async def drive():
        p = _plugin(main_module, "battery", power=True, per_zone=True)
        p._engine = main_module.EffectEngine(p._render, p._zones)
        p._engine.start_battery(p._battery_state)
        await asyncio.sleep(0)

        await p.prepare_suspend()
        p._controller.calls.clear()
        p._render([(1, 2, 3), (4, 5, 6)])

        assert p._controller.calls == []

    asyncio.run(drive())


def test_full_suspend_signal_cycle_stops_and_restores_battery(
    main_module, monkeypatch
):
    monkeypatch.setattr(main_module, "RESUME_REAPPLY_DELAY", 0)

    async def drive():
        p = _plugin(main_module, "battery", power=True, per_zone=True)
        p._engine = main_module.EffectEngine(p._render, p._zones)
        connection = FakeSuspendConnection()
        closed = []
        monitor = main_module.SuspendMonitor(
            p.prepare_suspend,
            resume_suspend=p._resume_after_suspend_signal,
            connect=connection.connect,
            close_fd=closed.append,
        )
        p._engine.start_battery(p._battery_state)
        render_task = p._engine._task
        monitor.start()
        await connection.ready.wait()

        connection.emit(True)
        for _ in range(20):
            if render_task.done() and closed == [41]:
                break
            await asyncio.sleep(0)

        assert render_task.cancelled()
        assert p._suspend_prepared is True
        assert closed == [41]

        p._controller.calls.clear()
        connection.emit(False)
        for _ in range(20):
            if p._engine.running and p._controller.reconnected:
                break
            await asyncio.sleep(0)

        assert connection.acquire_count == 2
        assert p._suspend_prepared is False
        assert p._controller.reconnected is True
        assert p._engine.running

        await monitor.stop_and_wait()
        await p._engine.stop_and_wait()

    asyncio.run(drive())


def test_apply_does_not_write_while_suspend_is_prepared(main_module):
    p = _plugin(main_module, "solid", power=True)
    p._suspend_prepared = True

    p._apply()

    assert p._controller.calls == []


def test_restore_after_resume_restarts_requested_battery_mode(main_module):
    async def drive():
        p = _plugin(main_module, "battery", power=True, per_zone=True)
        p._engine = main_module.EffectEngine(p._render, p._zones)
        p._suspend_prepared = True

        restored = await p._restore_after_resume()

        assert restored is True
        assert p._controller.reconnected is True
        assert p._engine.running
        await p._engine.stop_and_wait()

    asyncio.run(drive())


def test_resume_watch_reconnects_after_suspend_clock_jump(main_module, monkeypatch):
    monkeypatch.setattr(main_module, "RESUME_POLL_INTERVAL", 0)
    monkeypatch.setattr(main_module, "RESUME_REAPPLY_DELAY", 0)
    readings = iter([10.0, 10.1, 13.5])
    monkeypatch.setattr(main_module, "_suspend_clock", lambda: next(readings))
    p = _plugin(main_module, "solid")
    reconnects = 0

    async def reconnect():
        nonlocal reconnects
        reconnects += 1
        raise asyncio.CancelledError

    p.reconnect = reconnect

    async def drive():
        try:
            await p._resume_watch()
        except asyncio.CancelledError:
            pass

    asyncio.run(drive())
    assert reconnects == 1


def test_resume_restore_retries_until_controller_is_ready(main_module, monkeypatch):
    monkeypatch.setattr(main_module, "RESUME_RECONNECT_INTERVAL", 0)
    p = _plugin(main_module, "solid")
    results = iter([False, False, True])
    reconnects = 0

    async def reconnect():
        nonlocal reconnects
        reconnects += 1
        return next(results)

    p.reconnect = reconnect
    assert asyncio.run(p._restore_after_resume()) is True
    assert reconnects == 3


def test_overlapping_resume_signals_reconnect_only_once(main_module):
    async def drive():
        p = _plugin(main_module, "battery", power=True, per_zone=True)
        p._suspend_prepared = True
        reconnects = 0

        async def reconnect():
            nonlocal reconnects
            reconnects += 1
            await asyncio.sleep(0)
            return True

        p.reconnect = reconnect

        restored = await asyncio.gather(
            p._restore_after_resume(),
            p._restore_after_resume(),
        )

        assert restored == [True, True]
        assert reconnects == 1

    asyncio.run(drive())


def test_resume_restore_stops_after_bounded_failures(main_module, monkeypatch):
    monkeypatch.setattr(main_module, "RESUME_RECONNECT_INTERVAL", 0)
    monkeypatch.setattr(main_module, "RESUME_RECONNECT_ATTEMPTS", 3)
    p = _plugin(main_module, "solid")
    reconnects = 0

    async def reconnect():
        nonlocal reconnects
        reconnects += 1
        return False

    p.reconnect = reconnect
    assert asyncio.run(p._restore_after_resume()) is False
    assert reconnects == 3


def test_apex_reconnect_claims_hhd_before_controller_write(main_module):
    events = []
    p = _plugin(main_module, "solid", hhd_takeover=True)
    p._settings["force_control"] = True
    p._store = types.SimpleNamespace(save=lambda _s: None)

    class OrderedHhd:
        def read_rgb(self):
            events.append("hhd-read")
            return True

        def set_rgb(self, enabled):
            events.append(f"hhd-set-{enabled}")
            return True

    class OrderedController(FakeController):
        def reconnect(self):
            events.append("controller-reconnect")
            return super().reconnect()

        def apply_solid(self, color, brightness, power):
            events.append("controller-write")
            return super().apply_solid(color, brightness, power)

    p._hhd_rgb = OrderedHhd()
    p._controller = OrderedController()

    assert asyncio.run(p.reconnect()) is True
    assert events[:3] == ["hhd-read", "hhd-set-False", "controller-reconnect"]
    assert events.index("hhd-set-False") < events.index("controller-write")


def _late_ctx(device):
    return {
        "info": {"name": "ROG Xbox Ally X"},
        "capabilities": {"zones": 4, "color": True, "ambilight": True, "layout": []},
        "device": device,
        "power_led": None,
    }


def test_reprobe_recovers_late_led_and_rebuilds_zones(main_module, monkeypatch):
    # Cold-boot: the LED node wasn't present at load (NullDevice). Once it appears,
    # _reprobe_device must swap in the real controller and rebuild the engine to the
    # device's real zone count.
    p = _plugin(main_module, "solid")
    p._controller.available = False
    new_ctrl = FakeController(hw=False, per_zone=True)
    monkeypatch.setattr(main_module, "build_device", lambda **k: _late_ctx(new_ctrl))
    assert p._reprobe_device() is True
    assert p._controller is new_ctrl
    assert p._zones == 4


def test_reprobe_is_noop_when_already_available(main_module, monkeypatch):
    # Healthy machine: a present LED must never trigger a re-probe (build_device must
    # not even be called) — this is the guarantee that we don't disturb working setups.
    p = _plugin(main_module, "solid")
    monkeypatch.setattr(
        main_module, "build_device",
        lambda **k: (_ for _ in ()).throw(AssertionError("must not reprobe")),
    )
    assert p._reprobe_device() is True


def test_acquire_applies_when_led_appears_late(main_module, monkeypatch):
    monkeypatch.setattr(main_module, "ACQUIRE_INTERVAL", 0.001)
    monkeypatch.setattr(main_module, "REASSERT_DELAY", 0.001)
    p = _plugin(main_module, "solid")
    p._controller.available = False
    new_ctrl = FakeController(hw=False, per_zone=True)
    monkeypatch.setattr(main_module, "build_device", lambda **k: _late_ctx(new_ctrl))
    asyncio.run(p._acquire_and_reassert())
    assert p._controller is new_ctrl
    assert any(c[0] == "zones" for c in new_ctrl.calls)


def test_reassert_reapplies_static_mode(main_module, monkeypatch):
    # Static mode (solid): a single write can be lost to a late default, so re-assert.
    monkeypatch.setattr(main_module, "ACQUIRE_INTERVAL", 0.001)
    monkeypatch.setattr(main_module, "REASSERT_DELAY", 0.001)
    p = _plugin(main_module, "solid", hw=False, per_zone=True)
    asyncio.run(p._acquire_and_reassert())
    assert any(e[0] == "static" for e in p._engine.events)


def test_reassert_does_not_restart_running_effect(main_module, monkeypatch):
    # No-regression: in a render-loop mode the engine already rewrites continuously, so
    # the deferred re-assert must NOT reapply (which would restart the effect at frame 0)
    # and must not rebuild/swap anything on a healthy machine.
    monkeypatch.setattr(main_module, "ACQUIRE_INTERVAL", 0.001)
    monkeypatch.setattr(main_module, "REASSERT_DELAY", 0.001)
    monkeypatch.setattr(
        main_module, "build_device",
        lambda **k: (_ for _ in ()).throw(AssertionError("must not reprobe")),
    )
    p = _plugin(
        main_module, "effect",
        {"id": "breathing", "speed": 50, "use_gradient": True},
        hw=False, per_zone=True,
    )
    engine = p._engine
    ctrl = p._controller
    asyncio.run(p._acquire_and_reassert())
    assert p._engine is engine
    assert p._controller is ctrl
    assert not engine.events


def test_battery_mode_starts_battery_loop(main_module):
    p = _plugin(main_module, "battery", hw=False, per_zone=True)
    p._battery_level = 45
    p._ac_online = False
    p._apply()
    battery = next(e for e in p._engine.events if e[0] == "battery")
    assert battery[1] == {
        "level": 45,
        "charging": False,
        "breathe": True,
        "bands": main_module.BATTERY_BANDS,
    }
    assert not any(c[0] == "hw_effect" for c in p._controller.calls)


def test_battery_mode_wants_render_loop(main_module):
    p = _plugin(main_module, "battery")
    assert p._wants_render_loop() is True


def test_battery_mode_on_hardware_device_stays_software(main_module):
    # Even on a firmware-effects device (Legion) battery bands are painted in
    # software (easing/breathing), never a hardware effect.
    p = _plugin(main_module, "battery", hw=True, per_zone=False, per_controller=True)
    p._apply()
    assert any(e[0] == "battery" for e in p._engine.events)
    assert not any(c[0] == "hw_effect" for c in p._controller.calls)


def test_battery_mode_gated_off_when_power_off(main_module):
    p = _plugin(main_module, "battery", hw=False, per_zone=True, power=False)
    p._apply()
    assert ("static", [(0, 0, 0)] * p._zones) in p._engine.events
    assert not any(e[0] == "battery" for e in p._engine.events)


def test_temperature_mode_starts_temperature_loop(main_module):
    p = _plugin(main_module, "temperature", hw=False, per_zone=True)
    p._apu_temp = 73.0
    p._apply()
    temp = next(e for e in p._engine.events if e[0] == "temperature")
    assert temp[1] == {
        "temp": 73.0,
        "breathe": True,
        "bands": main_module.TEMPERATURE_BANDS,
    }
    assert not any(c[0] == "hw_effect" for c in p._controller.calls)


def test_temperature_reading_is_canonical_at_fractional_thresholds(main_module):
    assert main_module._temperature_reading(89.64) == 89.6
    assert main_module._temperature_reading(89.96) == 90.0

    p = _plugin(main_module, "temperature", hw=False, per_zone=True)
    p._apu_temp = 89.64

    assert p._temperature_state()["temp"] == 89.6
    assert asyncio.run(p.get_temperature()) == 89.6


def test_temperature_mode_wants_render_loop(main_module):
    p = _plugin(main_module, "temperature")
    assert p._wants_render_loop() is True


def test_temperature_mode_on_hardware_device_stays_software(main_module):
    p = _plugin(main_module, "temperature", hw=True, per_zone=False, per_controller=True)
    p._apu_temp = 91.0
    p._apply()
    assert any(e[0] == "temperature" for e in p._engine.events)
    assert not any(c[0] == "hw_effect" for c in p._controller.calls)


def test_temperature_mode_gated_off_when_power_off(main_module):
    p = _plugin(main_module, "temperature", hw=False, per_zone=True, power=False)
    p._apply()
    assert ("static", [(0, 0, 0)] * p._zones) in p._engine.events
    assert not any(e[0] == "temperature" for e in p._engine.events)


def test_battery_breathe_default_is_true(main_module):
    assert main_module.DEFAULTS["battery_breathe"] is True


def test_parse_sensor_bands_accepts_five_strictly_descending_bands(main_module):
    assert (
        main_module._parse_sensor_bands("battery", CUSTOM_SENSOR_BANDS)
        == PARSED_SENSOR_BANDS
    )


@pytest.mark.parametrize(
    "sensor,bands",
    [
        (
            "battery",
            [
                {"min": 80, "color": [1, 2, 3]},
                {"min": 80, "color": [4, 5, 6]},
                {"min": 50, "color": [7, 8, 9]},
                {"min": 25, "color": [10, 11, 12]},
                {"min": 0, "color": [13, 14, 15]},
            ],
        ),
        (
            "temperature",
            [
                {"min": 121, "color": [1, 2, 3]},
                {"min": 85, "color": [4, 5, 6]},
                {"min": 70, "color": [7, 8, 9]},
                {"min": 50, "color": [10, 11, 12]},
                {"min": 0, "color": [13, 14, 15]},
            ],
        ),
        (
            "battery",
            [
                {"min": 90, "color": [256, 2, 3]},
                {"min": 70, "color": [4, 5, 6]},
                {"min": 50, "color": [7, 8, 9]},
                {"min": 25, "color": [10, 11, 12]},
                {"min": 0, "color": [13, 14, 15]},
            ],
        ),
        (
            "battery",
            [
                {"min": 90, "color": [1, 2, 3]},
                {"min": 70, "color": [4, 5, 6]},
                {"min": 50, "color": [7, 8, 9]},
                {"min": 25, "color": [10, 11, 12]},
                {"min": 1, "color": [13, 14, 15]},
            ],
        ),
    ],
)
def test_parse_sensor_bands_rejects_invalid_values(main_module, sensor, bands):
    with pytest.raises(ValueError):
        main_module._parse_sensor_bands(sensor, bands)


def test_normalize_sensor_bands_falls_back_to_defaults(main_module):
    assert main_module._normalize_sensor_bands("battery", []) == main_module.BATTERY_BANDS
    assert (
        main_module._normalize_sensor_bands("temperature", None)
        == main_module.TEMPERATURE_BANDS
    )


def test_normalize_sensor_settings_handles_invalid_outer_shape(main_module):
    assert main_module._normalize_sensor_settings([]) == {
        "battery": main_module.BATTERY_BANDS,
        "temperature": main_module.TEMPERATURE_BANDS,
    }


def test_set_battery_breathe_persists(main_module):
    p = _plugin(main_module, "battery", hw=False, per_zone=True)
    saved = {}
    p._store = types.SimpleNamespace(save=lambda s: saved.update({"v": dict(s)}))
    asyncio.run(p.set_battery_breathe(False))
    assert p._settings["battery_breathe"] is False
    assert saved["v"]["battery_breathe"] is False


def test_set_sensor_bands_persists_and_feeds_battery_loop(main_module):
    p = _plugin(main_module, "battery", hw=False, per_zone=True)
    p._settings["sensor_bands"] = {
        "battery": main_module.BATTERY_BANDS,
        "temperature": main_module.TEMPERATURE_BANDS,
    }
    saved = {}
    p._store = types.SimpleNamespace(save=lambda s: saved.update({"v": dict(s)}))
    result = asyncio.run(p.set_sensor_bands("battery", CUSTOM_SENSOR_BANDS))

    assert result == CUSTOM_SENSOR_BANDS
    assert p._battery_state()["bands"] == PARSED_SENSOR_BANDS
    assert saved["v"]["sensor_bands"]["battery"] == p._battery_state()["bands"]


def test_set_sensor_bands_keeps_live_state_when_save_fails(main_module):
    p = _plugin(main_module, "battery", hw=False, per_zone=True)
    p._settings["sensor_bands"] = {
        "battery": main_module.BATTERY_BANDS,
        "temperature": main_module.TEMPERATURE_BANDS,
    }
    p._store = types.SimpleNamespace(
        save=lambda settings: (_ for _ in ()).throw(OSError("disk full"))
    )
    with pytest.raises(OSError, match="disk full"):
        asyncio.run(p.set_sensor_bands("battery", CUSTOM_SENSOR_BANDS))

    assert p._battery_state()["bands"] == main_module.BATTERY_BANDS


def test_sensor_bands_survive_settings_store_roundtrip(main_module, tmp_path):
    store = main_module.SettingsStore(str(tmp_path / "state.json"))
    settings = dict(main_module.DEFAULTS)
    settings["sensor_bands"] = {
        "battery": PARSED_SENSOR_BANDS,
        "temperature": main_module.TEMPERATURE_BANDS,
    }

    store.save(settings)
    loaded = store.load(main_module.DEFAULTS)

    assert main_module._normalize_sensor_bands(
        "battery", loaded["sensor_bands"]["battery"]
    ) == PARSED_SENSOR_BANDS


def test_force_control_default_is_false(main_module):
    assert main_module.DEFAULTS["force_control"] is False
    assert main_module.DEFAULTS["hhd_rgb_restore"] is None


def test_set_force_control_persists_and_applies(main_module):
    p = _plugin(main_module, "solid")
    saved = {}
    p._store = types.SimpleNamespace(save=lambda s: saved.update({"v": dict(s)}))
    asyncio.run(p.set_force_control(True))
    assert p._settings["force_control"] is True
    assert saved["v"]["force_control"] is True
    assert p._controller.calls, "set_force_control must re-apply"


def test_apex_force_control_disables_hhd_rgb_and_saves_restore_state(main_module):
    p, saved = _hhd_plugin(main_module, reads=[True], writes=[True])

    asyncio.run(p.set_force_control(True))

    assert p._hhd_rgb.calls == [("read",), ("set", False)]
    assert p._settings["hhd_rgb_restore"] is True
    assert saved[-1]["hhd_rgb_restore"] is True
    assert p._controller.invalidated is True


@pytest.mark.parametrize(
    "confirmed,expected_marker",
    [(True, None), (False, True)],
    ids=["confirmed-clears-marker", "failed-keeps-marker"],
)
def test_apex_hhd_restore_marker(main_module, confirmed, expected_marker):
    p, saved = _hhd_plugin(main_module, writes=[confirmed], restore=True, force=True)

    asyncio.run(p.set_force_control(False))

    assert p._hhd_rgb.calls == [("set", True)]
    assert p._settings["hhd_rgb_restore"] is expected_marker
    assert saved[-1]["hhd_rgb_restore"] is expected_marker


def test_force_control_does_not_touch_hhd_on_other_devices(main_module):
    p = _plugin(main_module, "solid", hhd_takeover=False)
    p._hhd_rgb = FakeHhdRgb(reads=[True], writes=[True])
    p._store = types.SimpleNamespace(save=lambda _s: None)

    asyncio.run(p.set_force_control(True))

    assert p._hhd_rgb.calls == []


def test_unload_restores_hhd_rgb_ownership(main_module):
    p, _ = _hhd_plugin(main_module, writes=[True], restore=True)

    asyncio.run(p._unload())

    assert p._hhd_rgb.calls == [("set", True)]
    assert p._settings["hhd_rgb_restore"] is None


def test_stopping_background_tasks_waits_for_suspend_monitor(main_module):
    async def drive():
        p = _plugin(main_module, "solid", hhd_takeover=True)
        p._suspend_monitor = FakeSuspendMonitor()

        await p._stop_background_tasks()

        assert p._suspend_monitor.events == [("stop_and_wait",)]

    asyncio.run(drive())


def test_unload_waits_for_inflight_hhd_claim_before_restore(main_module, monkeypatch):
    monkeypatch.setattr(main_module, "FORCE_CONTROL_INTERVAL", 0.001)
    p = _plugin(main_module, "solid", hhd_takeover=True)
    p._settings["force_control"] = True
    p._store = types.SimpleNamespace(save=lambda _s: None)
    claim_started = threading.Event()
    release_claim = threading.Event()

    class BlockingHhd:
        def __init__(self):
            self.calls = []

        def read_rgb(self):
            self.calls.append(("read",))
            return True

        def set_rgb(self, enabled):
            self.calls.append(("set", enabled))
            if not enabled:
                claim_started.set()
                release_claim.wait(1)
            return True

    p._hhd_rgb = BlockingHhd()

    async def drive():
        p._force_control_task = asyncio.create_task(p._force_control_watch())
        while not claim_started.is_set():
            await asyncio.sleep(0.001)
        unload = asyncio.create_task(p._unload())
        await asyncio.sleep(0.01)
        assert ("set", True) not in p._hhd_rgb.calls
        release_claim.set()
        await unload

    asyncio.run(drive())

    assert p._hhd_rgb.calls[-2:] == [("set", False), ("set", True)]
    assert p._settings["hhd_rgb_restore"] is None


def test_uninstall_stops_watcher_before_restoring_hhd(main_module):
    p, _ = _hhd_plugin(main_module, writes=[True], restore=True)

    async def drive():
        p._force_control_task = asyncio.create_task(asyncio.sleep(60))
        p._resume_task = asyncio.create_task(asyncio.sleep(60))
        await p._uninstall()
        assert p._force_control_task.cancelled()
        assert p._resume_task.cancelled()

    asyncio.run(drive())
    assert p._hhd_rgb.calls == [("set", True)]


def test_force_control_cannot_reclaim_hhd_after_unload_begins(main_module):
    p, _ = _hhd_plugin(main_module, reads=[True], writes=[True])

    async def drive():
        await p._unload()
        await p.set_force_control(True)

    asyncio.run(drive())

    assert p._hhd_rgb.calls == []
    assert p._controller.invalidated is False


def test_hardware_gradient_uses_device_zone_count(main_module):
    p = _plugin(main_module, "gradient", hw=True, per_zone=True)
    p._zones = 4
    p._capabilities["zones"] = 4
    p._apply()
    zone_calls = [c for c in p._controller.calls if c[0] == "zones"]
    assert zone_calls, "expected a per-zone hardware gradient write"
    assert len(zone_calls[0][1]) == 4


def _drive_watch(main_module, plugin):
    async def drive():
        task = asyncio.create_task(plugin._force_control_watch())
        await asyncio.sleep(0.02)
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass

    asyncio.run(drive())


def test_force_control_watch_reasserts_static_mode(main_module, monkeypatch):
    # Force control on + a static mode: the watch must re-assert, but GENTLY — no
    # invalidate()/re-init (that would blink the LEDs); just re-write the colors.
    monkeypatch.setattr(main_module, "FORCE_CONTROL_INTERVAL", 0.001)
    p = _plugin(main_module, "solid", hw=True, per_zone=True)
    p._settings["force_control"] = True
    p._controller.calls.clear()
    _drive_watch(main_module, p)
    assert p._controller.invalidated is False, "maintenance re-assert must not re-init"
    assert p._controller.calls, "watch must re-apply in a static mode"


@pytest.mark.parametrize(
    "hhd_enabled,expected_invalidated",
    [(True, True), (False, False)],
    ids=["reclaim-relatches", "already-disabled"],
)
def test_apex_force_control_watch(
    main_module, monkeypatch, hhd_enabled, expected_invalidated
):
    monkeypatch.setattr(main_module, "FORCE_CONTROL_INTERVAL", 0.001)
    p, _ = _hhd_plugin(
        main_module,
        reads=[True] if hhd_enabled else [False] * 30,
        writes=[True],
        force=True,
        hw=False,
    )
    p._controller.calls.clear()
    p._engine.events.clear()

    _drive_watch(main_module, p)

    assert (("set", False) in p._hhd_rgb.calls) is hhd_enabled
    assert p._controller.invalidated is expected_invalidated
    assert p._engine.events


def test_force_control_watch_skips_running_effect(main_module, monkeypatch):
    # A software render loop (wave on a per-zone device) already rewrites ~30fps;
    # the watch must NOT re-apply or it would restart the effect at frame 0.
    monkeypatch.setattr(main_module, "FORCE_CONTROL_INTERVAL", 0.001)
    p = _plugin(
        main_module,
        "effect",
        {"id": "wave", "speed": 50, "use_gradient": False},
        hw=False,
        per_zone=True,
    )
    p._settings["force_control"] = True
    p._controller.calls.clear()
    p._engine.events.clear()
    _drive_watch(main_module, p)
    assert p._controller.invalidated is False
    assert not p._engine.events, "watch must leave a running effect untouched"


def test_force_control_watch_noop_when_off(main_module, monkeypatch):
    monkeypatch.setattr(main_module, "FORCE_CONTROL_INTERVAL", 0.001)
    p = _plugin(main_module, "solid", hw=True, per_zone=True)
    p._settings["force_control"] = False
    p._controller.calls.clear()
    _drive_watch(main_module, p)
    assert p._controller.invalidated is False
    assert not p._controller.calls


def test_report_environment_names_arm_host_under_fex(main_module, monkeypatch):
    plugin = _plugin(main_module, "solid")
    plugin._device = {"name": "AYN Thor", "model": "AYN Thor"}
    tree = main_module.device_tree
    monkeypatch.setattr(tree, "is_arm", lambda root="/": True)
    monkeypatch.setattr(tree, "model", lambda root="/": "AYN Thor")
    monkeypatch.setattr(tree, "compatible", lambda root="/": ["ayn,thor", "qcom,sm8550"])
    monkeypatch.setattr(tree, "soc", lambda root="/": "Snapdragon SM8550")
    monkeypatch.setattr(tree, "host_os_release", lambda root="/": {"PRETTY_NAME": "Armada OS"})

    env = plugin._report_environment()

    assert env["os"] == "Armada OS"
    assert env["architecture"] == "arm64"
    assert env["device_tree_model"] == "AYN Thor"
    assert env["device_tree_compatible"] == ["ayn,thor", "qcom,sm8550"]
    assert env["soc"] == "Snapdragon SM8550"


def test_unload_without_hhd_finishes_without_yielding_to_the_loop(main_module):
    # Decky closes the plugin socket on stop and its read loop then spins without
    # yielding, so an unload that needs another loop turn is killed after 5 s.
    async def drive():
        p = _plugin(main_module, "solid")
        p._suspend_monitor = FakeSuspendMonitor()
        p._charger_task = asyncio.get_running_loop().create_future()
        coro = p._unload()
        try:
            coro.send(None)
        except StopIteration:
            pass
        else:
            coro.close()
            raise AssertionError("_unload suspended")
        assert p._suspend_monitor.events == [("stop_now",)]
        assert p._charger_task.cancelled()
        assert p._stopping is True

    asyncio.run(drive())


def test_unload_before_main_still_finishes(main_module):
    asyncio.run(main_module.Plugin()._unload())




def _in_loop(action):
    async def run():
        action()
        for task in asyncio.all_tasks():
            if task is not asyncio.current_task():
                task.cancel()

    asyncio.run(run())


def _smart(main_module, mode="solid", level=None, **options):
    p = _plugin(main_module, mode, **options)
    p._settings["smart_brightness"] = True
    p._screen_level = level
    p._screen_ramp = main_module.FactorRamp(duration=0)
    p._screen_ramp.snap(p._screen_target())
    return p


def test_led_brightness_without_smart_brightness_is_user_brightness(main_module):
    p = _plugin(main_module, "solid")
    p._apply()
    assert ("solid", (255, 0, 0), 80, True) in p._controller.calls


def test_smart_brightness_scales_static_color(main_module):
    p = _smart(main_module, level=19483 / 65535)
    p._apply()
    expected = main_module.led_brightness(80, main_module.perceived_level(19483 / 65535))
    assert expected < 80
    assert ("solid", (255, 0, 0), expected, True) in p._controller.calls


def test_smart_brightness_without_reading_keeps_user_brightness(main_module):
    p = _smart(main_module, level=None)
    p._apply()
    assert ("solid", (255, 0, 0), 80, True) in p._controller.calls


def test_smart_brightness_scales_render_loop_frames(main_module):
    p = _smart(main_module, mode="ambient", level=0.3, hw=False)
    p._render([(10, 20, 30), (40, 50, 60)])
    expected = main_module.led_brightness(80, main_module.perceived_level(0.3))
    assert p._controller.calls[-1] == ("zones", [(10, 20, 30), (40, 50, 60)], expected, True)


def test_screen_change_rerenders_last_frame_on_software_path(main_module):
    p = _smart(main_module, mode="solid", level=1.0, hw=False)
    p._render([(1, 2, 3), (4, 5, 6)])
    p._last_render_at = 0.0
    _in_loop(lambda: p._on_screen_level(0.3))
    p._refresh_led_brightness(final=True)
    expected = main_module.led_brightness(80, main_module.perceived_level(0.3))
    assert p._controller.calls[-1] == ("zones", [(1, 2, 3), (4, 5, 6)], expected, True)


def test_screen_change_reapplies_hardware_solid(main_module):
    p = _smart(main_module, mode="solid", level=1.0)
    _in_loop(lambda: p._on_screen_level(0.3))
    p._refresh_led_brightness(final=False)
    expected = main_module.led_brightness(80, main_module.perceived_level(0.3))
    assert p._controller.calls[-1] == ("solid", (255, 0, 0), expected, True)


def test_hardware_effect_only_rewritten_when_ramp_settles(main_module):
    p = _smart(
        main_module,
        mode="effect",
        effect={"id": "breathing", "speed": 50, "use_gradient": False},
        level=1.0,
    )
    _in_loop(lambda: p._on_screen_level(0.3))
    p._refresh_led_brightness(final=False)
    assert p._controller.calls == []
    p._refresh_led_brightness(final=True)
    assert p._controller.calls[-1][0] == "hw_effect"


def test_screen_refresh_skips_when_lights_are_off(main_module):
    p = _smart(main_module, mode="solid", level=1.0, power=False)
    _in_loop(lambda: p._on_screen_level(0.3))
    p._refresh_led_brightness(final=True)
    assert p._controller.calls == []


def test_unreadable_screen_holds_last_level(main_module):
    p = _smart(main_module, level=0.3)
    p._on_screen_level(None)
    assert p._screen_level == 0.3


def test_disabling_smart_brightness_ramps_back_to_full(main_module):
    p = _smart(main_module, level=0.3)
    p._settings["smart_brightness"] = False
    _in_loop(p._retarget_screen)
    assert p._screen_ramp.target == 1.0


def test_set_smart_brightness_persists_and_retargets(main_module, monkeypatch):
    p = _plugin(main_module, "solid")
    p._capabilities["screenBrightness"] = True
    p._screen_ramp = main_module.FactorRamp(duration=0)
    saved = []
    p._store = types.SimpleNamespace(save=lambda state: saved.append(dict(state)))
    monkeypatch.setattr(main_module, "read_backlight", lambda *a, **k: 0.3)

    asyncio.run(p.set_smart_brightness(True))

    assert p._settings["smart_brightness"] is True
    assert saved and saved[-1]["smart_brightness"] is True
    assert p._screen_ramp.target == pytest.approx(main_module.perceived_level(0.3))


def test_set_smart_brightness_ignored_without_backlight(main_module):
    p = _plugin(main_module, "solid")
    p._capabilities["screenBrightness"] = False
    asyncio.run(p.set_smart_brightness(True))
    assert not p._settings.get("smart_brightness")


def test_screen_ramp_steps_down_smoothly_and_lands_on_target(main_module):
    p = _smart(main_module, mode="solid", level=1.0, hw=False)
    p._capabilities["maxRenderFps"] = 30
    p._screen_ramp = main_module.FactorRamp(duration=0.15)
    p._screen_ramp.snap(1.0)
    p._render([(9, 9, 9), (9, 9, 9)])

    async def run():
        p._on_screen_level(0.05)
        await p._screen_ramp_task

    asyncio.run(run())

    levels = [call[2] for call in p._controller.calls]
    expected = main_module.led_brightness(80, main_module.perceived_level(0.05))
    assert levels[0] == 80
    assert levels[-1] == expected
    assert len(set(levels)) >= 3
    assert levels == sorted(levels, reverse=True)


def test_screen_level_is_journaled_in_coarse_steps(main_module, monkeypatch):
    p = _smart(main_module, level=1.0)
    written = []
    monkeypatch.setattr(
        main_module.journal,
        "active",
        types.SimpleNamespace(write=lambda *args, **fields: written.append((args, fields))),
    )
    _in_loop(lambda: p._on_screen_level(0.3))
    _in_loop(lambda: p._on_screen_level(0.301))
    assert written == [
        (("INFO", "state", "screen_brightness"), {"available": True, "pct": 60, "led": 50})
    ]


def test_mode_change_forgets_previous_frame_for_the_ramp(main_module):
    p = _smart(main_module, mode="ambient", level=1.0, hw=False)
    p._render([(1, 1, 1), (1, 1, 1)])
    p._apply()
    p._controller.calls.clear()
    p._refresh_led_brightness(final=True)
    assert p._controller.calls == []


def test_startup_is_not_saved_while_screen_dims_the_leds(main_module):
    p = _smart(main_module, level=0.2, hw=False)
    p._settings["remember_startup"] = True
    p._current_app_key = None
    p._maybe_persist_startup()
    assert getattr(p, "_startup_task", None) is None


def test_stop_background_tasks_cancels_smart_brightness_tasks(main_module):
    p = _plugin(main_module, "solid")
    p._suspend_monitor = FakeSuspendMonitor()

    async def run():
        p._screen_task = asyncio.create_task(asyncio.sleep(60))
        p._screen_ramp_task = asyncio.create_task(asyncio.sleep(60))
        await p._stop_background_tasks()
        await asyncio.sleep(0)
        return p._screen_task.cancelled(), p._screen_ramp_task.cancelled()

    assert asyncio.run(run()) == (True, True)

THOR = {"yellow": 37, "orange": -56, "white": 0}


def test_color_calibration_scales_hardware_solid(main_module):
    p = _plugin(main_module, "solid")
    p._settings["color"] = [255, 255, 0]
    p._settings["color_calibration"] = THOR
    p._apply()
    assert ("solid", (161, 255, 0), 80, True) in p._controller.calls


def test_color_calibration_scales_rendered_frames(main_module):
    p = _plugin(main_module, "ambient", hw=False)
    p._settings["color_calibration"] = THOR
    p._render([(255, 128, 0), (0, 0, 0)])
    assert p._controller.calls[-1] == ("zones", [(161, 81, 0), (0, 0, 0)], 80, True)


def test_color_calibration_scales_hardware_effect_color(main_module):
    p = _plugin(main_module, "effect", {"id": "breathing", "speed": 50, "use_gradient": False})
    p._settings["color_calibration"] = THOR
    p._apply()
    assert ("hw_effect", "breathing", (161, 0, 0), 50, 80, True) in p._controller.calls


def test_neutral_calibration_keeps_frames_untouched(main_module):
    p = _plugin(main_module, "ambient", hw=False)
    frame = [(1, 2, 3), (4, 5, 6)]
    p._render(frame)
    assert p._controller.calls[-1] == ("zones", frame, 80, True)


def test_calibration_preview_shows_key_color_and_pauses_effects(main_module):
    p = _plugin(main_module, "ambient", hw=False)
    asyncio.run(p.preview_color_calibration(THOR, [255, 255, 0]))
    assert ("stop",) in p._ambilight.events
    assert p._controller.calls[-1] == ("zones", [(161, 255, 0)] * 2, 80, True)

    p._controller.calls.clear()
    p._render([(9, 9, 9), (9, 9, 9)])
    assert p._controller.calls == []

    asyncio.run(p.preview_color_calibration(None))
    assert p._ambilight.events[-1][0] == "start"


def test_calibration_preview_lights_up_even_when_power_is_off(main_module):
    p = _plugin(main_module, "solid", power=False)
    asyncio.run(p.preview_color_calibration(THOR))
    assert p._controller.calls[-1] == ("zones", [(161, 255, 255)] * 2, 80, True)


def test_set_color_calibration_persists_and_ends_preview(main_module):
    p = _plugin(main_module, "solid")
    saved = []
    p._store = types.SimpleNamespace(save=lambda state: saved.append(dict(state)))
    asyncio.run(p.preview_color_calibration(THOR))

    result = asyncio.run(p.set_color_calibration({"yellow": 99, "orange": 0, "white": -10}))

    assert result == {"yellow": 60, "orange": 0, "white": -10}
    assert saved[-1]["color_calibration"] == result
    assert p._calibration_preview is None
    assert p._controller.calls[-1] == ("solid", (102, 0, 0), 80, True)


def test_saving_calibration_refreshes_the_startup_color(main_module):
    p = _plugin(main_module, "solid")
    p._store = types.SimpleNamespace(save=lambda state: None)
    p._settings["remember_startup"] = True
    p._current_app_key = None

    async def run():
        await p.set_color_calibration(THOR)
        return p._startup_task

    assert asyncio.run(run()) is not None


def test_startup_is_not_saved_during_calibration_preview(main_module):
    p = _plugin(main_module, "solid")
    p._settings["remember_startup"] = True
    p._current_app_key = None

    async def run():
        await p.preview_color_calibration(THOR)
        p._startup_task = None
        p._maybe_persist_startup()
        return p._startup_task

    assert asyncio.run(run()) is None


def test_calibration_preview_expires_on_its_own(main_module, monkeypatch):
    p = _plugin(main_module, "solid")
    monkeypatch.setattr(main_module, "CALIBRATION_PREVIEW_TIMEOUT", 0.01)

    async def run():
        await p.preview_color_calibration(THOR)
        await p._calibration_expiry_task

    asyncio.run(run())

    assert p._calibration_preview is None
    assert p._controller.calls[-1] == ("solid", (255, 0, 0), 80, True)
def _aurora(main_module, audio=True):
    p = _plugin(main_module, "ambient", hw=False)
    p._settings["ambilight"] = {**p._settings["ambilight"], "audio": audio}
    p._audio.status = "running"
    return p


def test_aurora_meters_audio_alongside_ambilight(main_module):
    p = _aurora(main_module)
    p._apply()
    assert p._ambilight.events[-1][0] == "start"
    kind, options = p._audio.events[-1]
    assert kind == "start" and callable(options["on_level"])


def test_plain_ambilight_keeps_audio_stopped(main_module):
    p = _aurora(main_module, audio=False)
    p._apply()
    assert p._audio.events[-1] == ("stop",)


def test_aurora_scales_the_screen_colors_with_the_sound(main_module):
    p = _aurora(main_module)
    p._audio.level = 0.0
    p._render([(200, 100, 0), (0, 0, 40)])
    assert p._controller.calls[-1][1] == [(50, 25, 0), (0, 0, 10)]
    p._audio.level = 1.0
    p._render([(200, 100, 0), (0, 0, 40)])
    assert p._controller.calls[-1][1] == [(200, 100, 0), (0, 0, 40)]


def test_aurora_level_repaints_the_last_screen_frame(main_module):
    p = _aurora(main_module)
    p._capabilities["maxRenderFps"] = 30
    p._audio.level = 1.0
    p._render([(200, 100, 0), (0, 0, 40)])
    p._last_render_at = 0.0
    p._audio.level = 0.5
    p._on_aurora_level(0.5)
    assert p._controller.calls[-1][1] == [(125, 63, 0), (0, 0, 25)]


def test_aurora_level_respects_device_cadence(main_module):
    p = _aurora(main_module)
    p._capabilities["maxRenderFps"] = 2
    p._render([(200, 100, 0), (0, 0, 40)])
    calls = len(p._controller.calls)
    p._on_aurora_level(0.5)
    assert len(p._controller.calls) == calls


def test_aurora_does_not_touch_other_modes(main_module):
    p = _plugin(main_module, "effect", hw=False)
    p._settings["ambilight"] = {**p._settings["ambilight"], "audio": True}
    p._render([(200, 100, 0), (0, 0, 40)])
    assert p._controller.calls[-1][1] == [(200, 100, 0), (0, 0, 40)]


def test_ambilight_audio_is_a_profile_setting(main_module):
    p = _aurora(main_module, audio=False)
    p._store = types.SimpleNamespace(save=lambda state: None)
    asyncio.run(p.patch_profile("global", None, {"ambilight": {"audio": True}}))
    assert p._settings["ambilight"]["audio"] is True
    assert p._audio.events[-1][0] == "start"


def test_aurora_waits_for_the_meter_before_dimming(main_module):
    p = _aurora(main_module)
    p._audio.status = "idle"
    p._render([(200, 100, 0), (0, 0, 40)])
    assert p._controller.calls[-1][1] == [(200, 100, 0), (0, 0, 40)]


def test_settings_change_forgets_the_aurora_frame(main_module):
    p = _aurora(main_module)
    p._render([(200, 100, 0), (0, 0, 40)])
    p._apply()
    p._controller.calls.clear()
    p._last_render_at = 0.0
    p._on_aurora_level(0.5)
    assert p._controller.calls == []


def test_aurora_without_audio_falls_back_to_plain_ambilight(main_module):
    p = _aurora(main_module)
    p._audio.status = "no_source"
    p._render([(200, 100, 0), (0, 0, 40)])
    assert p._controller.calls[-1][1] == [(200, 100, 0), (0, 0, 40)]
def _styles_plugin(main_module, tmp_path):
    p = _plugin(main_module, "solid")
    p._settings.update(
        styles=[],
        brightness=70,
        battery_breathe=True,
        temperature_breathe=True,
    )
    p._settings["ambilight"] = {**p._settings["ambilight"], "sampling": "columns"}
    saved = []
    p._store = types.SimpleNamespace(save=lambda state: saved.append(dict(state)))
    return p, saved


def test_save_style_captures_the_edited_profile(main_module, tmp_path):
    p, saved = _styles_plugin(main_module, tmp_path)
    styles = asyncio.run(p.save_style("global", None, "  Rojo  "))
    assert [style["name"] for style in styles] == ["Rojo"]
    assert styles[0]["mode"] == "color"
    assert styles[0]["colors"] == [{"r": 255, "g": 0, "b": 0}]
    assert saved[-1]["styles"] == styles


def test_apply_style_patches_the_profile(main_module, tmp_path):
    p, _ = _styles_plugin(main_module, tmp_path)
    p._settings["styles"] = [{"name": "Azul", "mode": "effect", "colors": [{"r": 0, "g": 0, "b": 255}]}]
    asyncio.run(p.apply_style("global", None, "azul"))
    assert p._settings["mode"] == "effect"
    assert p._settings["color"] == [0, 0, 255]


def test_apply_unknown_style_fails(main_module, tmp_path):
    p, _ = _styles_plugin(main_module, tmp_path)
    with pytest.raises(ValueError):
        asyncio.run(p.apply_style("global", None, "nada"))


def test_delete_style(main_module, tmp_path):
    p, saved = _styles_plugin(main_module, tmp_path)
    p._settings["styles"] = [{"name": "Azul"}, {"name": "Rojo"}]
    assert [s["name"] for s in asyncio.run(p.delete_style("AZUL"))] == ["Rojo"]
    assert saved[-1]["styles"] == [{"name": "Rojo"}]


def test_export_and_import_styles_through_a_file(main_module, tmp_path, monkeypatch):
    p, _ = _styles_plugin(main_module, tmp_path)
    monkeypatch.setattr(main_module, "_user_home", lambda: str(tmp_path))
    monkeypatch.setattr(main_module, "_user_creds", lambda: ("/run/user/1000", None, None))
    p._settings["styles"] = [{"name": "Azul", "mode": "color"}]

    exported = asyncio.run(p.export_styles())
    assert exported["count"] == 1
    path = exported["path"]

    p._settings["styles"] = [{"name": "Rojo", "mode": "color"}]
    result = asyncio.run(p.import_styles(path))
    assert [s["name"] for s in result["styles"]] == ["Rojo", "Azul"]
    assert (result["added"], result["replaced"], result["skipped"]) == (1, 0, 0)
    assert result["imported"] == ["Azul"]


def test_import_rejects_files_that_are_not_json_or_too_big(main_module, tmp_path):
    p, _ = _styles_plugin(main_module, tmp_path)
    big = tmp_path / "big.json"
    big.write_text(" " * (main_module.STYLES_FILE_LIMIT + 1))
    with pytest.raises(ValueError):
        asyncio.run(p.import_styles(str(big)))
    other = tmp_path / "notes.txt"
    other.write_text("{}")
    with pytest.raises(ValueError):
        asyncio.run(p.import_styles(str(other)))


def test_export_replaces_a_planted_symlink_without_following_it(main_module, tmp_path, monkeypatch):
    p, _ = _styles_plugin(main_module, tmp_path)
    monkeypatch.setattr(main_module, "_user_home", lambda: str(tmp_path))
    monkeypatch.setattr(main_module, "_user_creds", lambda: ("/run/user/1000", None, None))
    target = tmp_path / "victim.txt"
    target.write_text("keep")
    (tmp_path / "Documents").mkdir()
    (tmp_path / "Documents" / "Colores").mkdir()
    planted = tmp_path / "Documents" / "Colores" / "colores-estilos.json"
    planted.symlink_to(target)
    p._settings["styles"] = [{"name": "Azul"}]

    asyncio.run(p.export_styles())

    assert target.read_text() == "keep"
    assert not planted.is_symlink()
    assert json.loads(planted.read_text())["styles"] == [{"name": "Azul"}]


def test_export_refuses_a_symlinked_folder(main_module, tmp_path, monkeypatch):
    p, _ = _styles_plugin(main_module, tmp_path)
    monkeypatch.setattr(main_module, "_user_home", lambda: str(tmp_path))
    monkeypatch.setattr(main_module, "_user_creds", lambda: ("/run/user/1000", None, None))
    elsewhere = tmp_path / "elsewhere"
    elsewhere.mkdir()
    (tmp_path / "Documents").symlink_to(elsewhere)

    with pytest.raises(OSError):
        asyncio.run(p.export_styles())
    assert list(elsewhere.iterdir()) == []


def test_save_style_fails_when_the_list_is_full(main_module, tmp_path):
    p, _ = _styles_plugin(main_module, tmp_path)
    p._settings["styles"] = [{"name": f"Estilo {i}"} for i in range(50)]
    with pytest.raises(ValueError):
        asyncio.run(p.save_style("global", None, "Uno más"))


def test_import_rejects_deeply_nested_json(main_module, tmp_path):
    p, _ = _styles_plugin(main_module, tmp_path)
    bomb = tmp_path / "bomb.json"
    bomb.write_text("[" * 100000)
    with pytest.raises(ValueError):
        asyncio.run(p.import_styles(str(bomb)))
