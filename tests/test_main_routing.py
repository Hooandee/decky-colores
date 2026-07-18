import asyncio
import sys
import types

import pytest


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

    def apply_hardware_effect(self, effect_id, color, speed, power):
        self.calls.append(("hw_effect", effect_id, tuple(color), speed, power))
        return True

    def reconnect(self):
        self.reconnected = True
        return True


class FakeEngine:
    def __init__(self):
        self.events = []

    def stop(self):
        self.events.append(("stop",))

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

    def start(self, cfg):
        self.events.append(("start", cfg))


class FakeAudio:
    def __init__(self):
        self.events = []
        self.status = "idle"

    def stop(self):
        self.events.append(("stop",))

    def start(self, options=None):
        self.events.append(("start", options))


def _plugin(
    main_module,
    mode,
    effect=None,
    hw=True,
    power=True,
    per_zone=False,
    per_controller=False,
):
    p = main_module.Plugin()
    p._ready = True
    p._controller = FakeController(hw, per_zone)
    p._engine = FakeEngine()
    p._ambilight = FakeAmbilight()
    p._audio = FakeAudio()
    p._zones = 2
    p._capabilities = {
        "zones": 2,
        "perControllerColor": per_controller,
        "perZone": per_zone,
    }
    p._settings = {
        "power": power,
        "brightness": 80,
        "mode": mode,
        "color": [255, 0, 0],
        "gradient": [[0, 196, 255], [136, 86, 255]],
        "gradient_speed": 30,
        "effect": effect or {"id": "breathing", "speed": 50, "use_gradient": False},
        "ambilight": {"saturation": 140, "smoothing": 75, "fps": 10},
    }
    return p


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
        await asyncio.sleep(0.02)
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass

    asyncio.run(drive())
    assert p._ac_online is True
    assert any(e[0] == "static" for e in p._engine.events)


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


def test_ambient_runs_capture_on_hardware_device(main_module):
    p = _plugin(main_module, "ambient")
    p._apply()
    assert any(e[0] == "start" for e in p._ambilight.events)
    assert not any(c[0] == "solid" for c in p._controller.calls)


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
    assert battery[1] == {"level": 45, "charging": False, "breathe": True}
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
    assert temp[1] == {"temp": 73.0, "breathe": True}
    assert not any(c[0] == "hw_effect" for c in p._controller.calls)


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


def test_set_battery_breathe_persists(main_module):
    p = _plugin(main_module, "battery", hw=False, per_zone=True)
    saved = {}
    p._store = types.SimpleNamespace(save=lambda s: saved.update({"v": dict(s)}))
    asyncio.run(p.set_battery_breathe(False))
    assert p._settings["battery_breathe"] is False
    assert saved["v"]["battery_breathe"] is False


def test_force_control_default_is_false(main_module):
    assert main_module.DEFAULTS["force_control"] is False


def test_set_force_control_persists_and_applies(main_module):
    p = _plugin(main_module, "solid")
    saved = {}
    p._store = types.SimpleNamespace(save=lambda s: saved.update({"v": dict(s)}))
    asyncio.run(p.set_force_control(True))
    assert p._settings["force_control"] is True
    assert saved["v"]["force_control"] is True
    assert p._controller.calls, "set_force_control must re-apply"


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
