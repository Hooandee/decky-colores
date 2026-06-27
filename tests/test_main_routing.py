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


class FakeAmbilight:
    def __init__(self):
        self.events = []
        self.status = "idle"

    def stop(self):
        self.events.append(("stop",))

    def start(self, cfg):
        self.events.append(("start", cfg))


def _plugin(main_module, mode, effect=None, hw=True, power=True, per_zone=False):
    p = main_module.Plugin()
    p._ready = True
    p._controller = FakeController(hw, per_zone)
    p._engine = FakeEngine()
    p._ambilight = FakeAmbilight()
    p._zones = 2
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


def test_wave_effect_runs_in_software_on_hardware_device(main_module):
    p = _plugin(main_module, "effect", {"id": "wave", "speed": 50, "use_gradient": False})
    p._apply()
    kinds = [e[0] for e in p._engine.events]
    assert "effect" in kinds
    effect_event = next(e for e in p._engine.events if e[0] == "effect")
    assert effect_event[1] == "wave"
    assert effect_event[2]["stops"] == [(0, 196, 255), (136, 86, 255)]
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
