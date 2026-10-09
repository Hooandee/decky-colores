import json
import time

from journal_context import (
    active_services,
    context_changes,
    context_snapshot,
    needs_snapshot,
    plugin_inventory,
)


def _plugin(root, folder, name, version="1.0.0"):
    path = root / folder
    path.mkdir()
    (path / "plugin.json").write_text(json.dumps({"name": name}))
    (path / "package.json").write_text(json.dumps({"version": version}))


def test_inventory_marks_plugins_decky_has_disabled(tmp_path):
    plugins = tmp_path / "plugins"
    plugins.mkdir()
    _plugin(plugins, "colores", "Colores", "0.24.0")
    _plugin(plugins, "huesync", "HueSync", "1.2.0")
    (plugins / "stray.txt").write_text("not a plugin")
    loader = tmp_path / "loader.json"
    loader.write_text(json.dumps({"disabled_plugins": ["HueSync"]}))
    assert plugin_inventory(str(plugins), str(loader)) == [
        {"name": "Colores", "version": "0.24.0", "enabled": True},
        {"name": "HueSync", "version": "1.2.0", "enabled": False},
    ]


def test_inventory_survives_missing_folders_and_settings(tmp_path):
    assert plugin_inventory(str(tmp_path / "none"), str(tmp_path / "none.json")) == []


def test_only_enabled_rgb_plugins_and_rgb_services_are_rivals(tmp_path):
    plugins = tmp_path / "plugins"
    plugins.mkdir()
    _plugin(plugins, "a", "Colores")
    _plugin(plugins, "b", "HueSync")
    _plugin(plugins, "c", "LegionGoRemapper")
    _plugin(plugins, "d", "SteamGridDB")
    _plugin(plugins, "e", "Ally RGB Lighting")
    loader = tmp_path / "loader.json"
    loader.write_text(json.dumps({"disabled_plugins": ["Ally RGB Lighting"]}))
    units = (
        "hhd@deck.service loaded active running Handheld Daemon\n"
        "inputplumber.service loaded active running InputPlumber\n"
        "openrgb.service loaded active running OpenRGB\n"
        "steamos-manager.service loaded active running SteamOS Manager\n"
        "sshd.service loaded active running OpenSSH\n"
    )
    context = context_snapshot(str(plugins), str(loader), lambda command: units)
    assert context["services"] == ["hhd", "inputplumber", "openrgb", "steamos-manager"]
    assert context["rivals"] == [
        {"name": "HueSync", "kind": "plugin", "writes": "rgb"},
        {"name": "LegionGoRemapper", "kind": "plugin", "writes": "rgb"},
        {"name": "hhd", "kind": "service", "writes": "rgb"},
        {"name": "inputplumber", "kind": "service", "writes": "rgb"},
        {"name": "openrgb", "kind": "service", "writes": "rgb"},
        {"name": "steamos-manager", "kind": "service", "writes": "system"},
    ]


def test_armada_services_are_named_even_on_a_stock_hostname():
    units = "armada-rgb.service loaded active running\narmada-control.service loaded active running\n"
    assert active_services(lambda command: units) == ["armada-control", "armada-rgb"]


def test_services_survive_a_failed_systemctl():
    assert active_services(lambda command: None) == []

    def broken(command):
        raise OSError("no systemctl")

    assert active_services(broken) == []


def test_context_changes_lists_only_what_moved():
    before = {"plugins": [{"name": "A", "version": "1", "enabled": True}], "services": ["hhd"]}
    after = {
        "plugins": [{"name": "A", "version": "1", "enabled": False}, {"name": "B", "version": "2", "enabled": True}],
        "services": [],
    }
    assert context_changes(None, after) is None
    assert context_changes(before, before) is None
    assert context_changes(before, after) == {"added": ["B"], "toggled": ["A"], "services_stopped": ["hhd"]}


def test_context_is_written_once_a_day_or_on_change():
    now = time.time()
    current = {"plugins": [], "services": ["hhd"], "rivals": []}
    keys = ("plugins", "services", "rivals")
    assert needs_snapshot(None, current, keys, now=now)
    assert not needs_snapshot({"t": now - 5, **current}, current, keys, now=now)
    assert needs_snapshot({"t": now - 5, **current, "services": []}, current, keys, now=now)
    assert needs_snapshot({"t": now - 2 * 86_400, **current}, current, keys, now=now)
