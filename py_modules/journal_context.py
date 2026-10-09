from __future__ import annotations

import json
import os
import re
import time
from typing import Callable

from journal import _day

_RIVAL_PLUGIN_NAMES = frozenset({"allycenter", "hhddecky", "handhelddaemon", "legiongoremapper"})
_RIVAL_PLUGIN_TOKENS = ("huesync", "ledcontrol", "rgb", "aura", "lighting")
_SELF_PLUGIN = "colores"
_RGB_SERVICES = {
    "hhd": "rgb",
    "inputplumber": "rgb",
    "openrgb": "rgb",
    "armada-rgb": "rgb",
    "handycon": "rgb",
    "armada-control": "system",
    "steamos-manager": "system",
}
_UNIT = re.compile(r"^(?P<stem>[A-Za-z0-9_.-]+?)(?:@[^.\s]*)?\.service$")
_MAX_PLUGINS = 64


def _normalise(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.lower())


def _manifest(directory: str, file_name: str, key: str) -> str | None:
    try:
        with open(os.path.join(directory, file_name), encoding="utf-8") as handle:
            value = json.load(handle).get(key)
    except (OSError, ValueError, AttributeError):
        return None
    return value[:80] if isinstance(value, str) else None


def plugin_inventory(plugins_dir: str, loader_settings: str) -> list[dict]:
    try:
        with open(loader_settings, encoding="utf-8") as handle:
            disabled = set(json.load(handle).get("disabled_plugins") or [])
    except (OSError, ValueError, AttributeError, TypeError):
        disabled = set()
    plugins = []
    try:
        entries = sorted(os.scandir(plugins_dir), key=lambda entry: entry.name)
    except OSError:
        return plugins
    for entry in entries[:_MAX_PLUGINS]:
        try:
            if not entry.is_dir(follow_symlinks=False):
                continue
        except OSError:
            continue
        name = _manifest(entry.path, "plugin.json", "name") or entry.name
        plugins.append({
            "name": name[:80],
            "version": _manifest(entry.path, "package.json", "version"),
            "enabled": name not in disabled,
        })
    return plugins


def active_services(run: Callable[[list[str]], str | None]) -> list[str]:
    try:
        output = run(["systemctl", "list-units", "--type=service", "--state=active", "--no-legend", "--plain"])
    except Exception:  # noqa: BLE001
        output = None
    found = set()
    for line in (output or "").splitlines():
        unit = line.split(None, 1)[0] if line.strip() else ""
        match = _UNIT.match(unit)
        if match and match.group("stem") in _RGB_SERVICES:
            found.add(match.group("stem"))
    return sorted(found)


def _is_rival_plugin(name: str) -> bool:
    normalised = _normalise(name)
    if not normalised or _SELF_PLUGIN in normalised:
        return False
    return normalised in _RIVAL_PLUGIN_NAMES or any(token in normalised for token in _RIVAL_PLUGIN_TOKENS)


def rivals(plugins: list[dict], services: list[str]) -> list[dict]:
    found = []
    for plugin in plugins:
        if plugin.get("enabled", True) and _is_rival_plugin(plugin["name"]):
            found.append({"name": plugin["name"], "kind": "plugin", "writes": "rgb"})
    for service in services:
        found.append({"name": service, "kind": "service", "writes": _RGB_SERVICES[service]})
    return found


def context_snapshot(plugins_dir: str, loader_settings: str, run: Callable[[list[str]], str | None]) -> dict:
    plugins = plugin_inventory(plugins_dir, loader_settings)
    services = active_services(run)
    return {"plugins": plugins, "services": services, "rivals": rivals(plugins, services)}


def context_changes(previous: dict | None, current: dict) -> dict | None:
    if previous is None:
        return None
    before = {plugin["name"]: plugin for plugin in previous.get("plugins") or [] if isinstance(plugin, dict)}
    after = {plugin["name"]: plugin for plugin in current.get("plugins") or [] if isinstance(plugin, dict)}
    changes = {
        "added": sorted(set(after) - set(before)),
        "removed": sorted(set(before) - set(after)),
        "toggled": sorted(
            name for name in set(after) & set(before)
            if after[name].get("enabled") != before[name].get("enabled")
            or after[name].get("version") != before[name].get("version")
        ),
        "services_started": sorted(set(current.get("services") or []) - set(previous.get("services") or [])),
        "services_stopped": sorted(set(previous.get("services") or []) - set(current.get("services") or [])),
    }
    return {key: value for key, value in changes.items() if value} or None


# Values read back from the diary went through JSON, where tuples became lists.
def _canonical(value: object) -> str:
    return json.dumps(value, sort_keys=True, default=str)


def needs_snapshot(last: dict | None, current: dict, keys: tuple[str, ...], *, now: float | None = None) -> bool:
    if not last or not isinstance(last.get("t"), (int, float)):
        return True
    now = time.time() if now is None else now
    if _day(last["t"]) != _day(now):
        return True
    return any(_canonical(last.get(key)) != _canonical(current.get(key)) for key in keys)
