import asyncio
import logging
import socket
import sys
import types

import pytest

import journal
from colores_report.collector import build_bundle
from test_main_routing import _plugin


@pytest.fixture
def main_module(tmp_path):
    saved = sys.modules.get("decky")
    logger = logging.getLogger("colores-journal-main-test")
    logger.propagate = False
    logger.setLevel(logging.INFO)
    target = logging.FileHandler(str(tmp_path / "decky.log"))
    logger.addHandler(target)
    stub = types.ModuleType("decky")
    stub.logger = logger
    stub.DECKY_USER = "deck"
    stub.DECKY_PLUGIN_SETTINGS_DIR = str(tmp_path)
    stub.DECKY_PLUGIN_RUNTIME_DIR = str(tmp_path / "runtime")
    stub.DECKY_VERSION = "v3.1.0"
    sys.modules["decky"] = stub
    sys.modules.pop("main", None)
    import main

    yield main
    journal.active = None
    for handler in list(logger.handlers):
        logger.removeHandler(handler)
        handler.close()
    sys.modules.pop("main", None)
    if saved is None:
        sys.modules.pop("decky", None)
    else:
        sys.modules["decky"] = saved


def _records(main_module):
    return list(journal.read_records(main_module.decky.DECKY_PLUGIN_RUNTIME_DIR + "/logs"))


def _with_journal(main_module, plugin, body):
    async def run():
        plugin._start_journal()
        try:
            result = body()
            if asyncio.iscoroutine(result):
                await result
        finally:
            plugin._stop_journal("unload")

    asyncio.run(run())
    return _records(main_module)


def test_journal_session_wraps_decky_logs_and_restores_handlers(main_module):
    plugin = _plugin(main_module, "solid")
    logger = main_module.decky.logger
    before = list(logger.handlers)

    records = _with_journal(main_module, plugin, lambda: logger.warning("Colores: hid write failed"))

    assert [(r["s"], r["m"]) for r in records] == [
        ("session", "start"),
        ("log", "Colores: hid write failed"),
        ("session", "stop"),
    ]
    assert records[0]["decky"] == "v3.1.0" and records[-1]["reason"] == "unload"
    assert logger.handlers == before and journal.active is None


def test_mode_and_device_are_written_only_when_they_change(main_module):
    plugin = _plugin(main_module, "solid", hw=False)

    def body():
        plugin._journal_device()
        plugin._journal_device()
        plugin._apply()
        plugin._apply()
        plugin._settings["mode"] = "effect"
        plugin._settings["effect"] = {"id": "wave", "speed": 50, "use_gradient": False}
        plugin._apply()

    states = [r for r in _with_journal(main_module, plugin, body) if r["s"] == "state"]
    assert [(r["m"], r.get("mode"), r.get("effect")) for r in states] == [
        ("device", None, None),
        ("mode", "solid", None),
        ("mode", "effect", "wave"),
    ]
    assert states[0]["driver"] == "FakeController" and states[0]["available"] is True


def test_led_write_failure_and_recovery_are_recorded_once(main_module):
    plugin = _plugin(main_module, "solid", hw=False)
    plugin._controller.led_path = "/sys/class/leds/multicolor:chassis/multi_intensity"

    def body():
        plugin._controller.last_error = "[Errno 5] Input/output error"
        plugin._render([(255, 0, 0)] * 2)
        plugin._render([(255, 0, 0)] * 2)
        plugin._controller.last_error = None
        plugin._render([(0, 0, 255)] * 2)

    hw = [r for r in _with_journal(main_module, plugin, body) if r["s"] == "hw"]
    assert [(r["m"], r.get("error")) for r in hw] == [("write_failed", "EIO"), ("write_recovered", None)]
    assert hw[0]["target"].endswith("multi_intensity") and hw[0]["by"] == "FakeController"


def test_capture_and_hhd_states_follow_their_status(main_module):
    plugin = _plugin(main_module, "ambient", hhd_takeover=True)

    def body():
        plugin._journal_runtime()
        plugin._ambilight.status = "running"
        plugin._journal_runtime()
        plugin._ambilight.status = "no_source"
        plugin._journal_runtime()
        plugin._hhd_rgb_status = "disabled"
        plugin._journal_runtime()

    states = [(r["m"], r.get("status")) for r in _with_journal(main_module, plugin, body) if r["s"] == "state"]
    assert states == [
        ("hhd_rgb", None),
        ("ambilight", "running"),
        ("ambilight", "no_source"),
        ("hhd_rgb", "disabled"),
    ]


def test_resume_reconnect_is_not_a_user_action(main_module):
    plugin = _plugin(main_module, "solid")
    plugin._apply_sleep_charging_indicator = lambda: False

    async def body():
        await plugin.reconnect()
        await plugin._restore_after_resume()

    records = _with_journal(main_module, plugin, body)
    assert [r["m"] for r in records if r["s"] == "rpc"] == ["reconnect"]
    resume = [r for r in records if r["s"] == "state" and r["m"] == "resume"]
    assert len(resume) == 1 and resume[0]["ok"] is True and resume[0]["attempts"] == 1


def test_user_actions_are_traced_and_polling_is_not(main_module):
    plugin_class = main_module.Plugin
    for name in ("set_power", "set_mode", "set_solid", "patch_profile", "reconnect", "submit_report"):
        assert hasattr(getattr(plugin_class, name), "__wrapped__"), name
    for name in (
        "get_state", "get_temperature", "get_performance", "get_ambilight_status",
        "get_audio_status", "get_version", "get_profile_state", "check_update", "_main", "_unload",
    ):
        assert not hasattr(getattr(plugin_class, name), "__wrapped__"), name


def test_stock_hostnames_are_not_anonymised(main_module, monkeypatch):
    plugin = main_module.Plugin()
    monkeypatch.setattr(main_module.device_tree, "host_os_release", lambda: {"ID": "bazzite"})
    for stock in ("armada", "steamdeck", "localhost", "bazzite"):
        monkeypatch.setattr(socket, "gethostname", lambda stock=stock: stock)
        assert plugin._redact_ids()[1] is None
    monkeypatch.setattr(socket, "gethostname", lambda: "juans-ally")
    assert plugin._redact_ids()[1] == "juans-ally"


def test_report_bundle_carries_the_redacted_journal(main_module):
    plugin = _plugin(main_module, "solid")

    def body():
        main_module.decky.logger.warning("failed reading /home/deck/.config/x on juans-ally")

    async def run():
        plugin._start_journal()
        try:
            body()
            plugin._stop_journal("unload")
            plugin._start_journal()
            await asyncio.sleep(0)
            return plugin._journal_report()
        finally:
            plugin._stop_journal("unload")

    report = asyncio.run(run())
    bundle = build_bundle(
        app="colores", categories=[], text="", environment={}, capabilities={},
        state={}, stores={}, logs=[{"name": "x.log", "text": "kept"}], journal=report,
        home="/home/deck", hostname="juans-ally",
    )
    lines = [r["m"] for r in bundle["journal"]["recent"]]
    assert "failed reading ~/.config/x on HOST" in lines
    assert bundle["journal"]["summary"] and bundle["logs"] == [{"name": "x.log", "text": "kept"}]
    assert {"dropped", "write_failures", "older", "recent_omitted"} <= set(bundle["journal"])


def test_bundle_without_a_journal_keeps_an_empty_field():
    bundle = build_bundle(
        app="colores", categories=[], text="", environment={}, capabilities={},
        state={}, stores={}, logs=[],
    )
    assert bundle["journal"] == {}
