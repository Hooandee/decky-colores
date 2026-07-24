from __future__ import annotations

import glob
import json
import os
import re

SCHEMA = 1

_MAX_TEXT = 4000

_SCRUB_KEY = re.compile(r"serial|uuid|\bmac\b|mac_?addr|hostname|host_name", re.I)
_HOME_PATH = re.compile(r"/home/[^/\s:\"']+")
_MAC = re.compile(r"\b(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}\b")
_UUID = re.compile(
    r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b"
)
_SERIAL_LABELED = re.compile(
    r"((?:board|product|chassis|system|baseboard)?_?serial(?:\s*number)?)(\s*[:=]\s*)(\S+)",
    re.I,
)
_SERIAL_RUN = re.compile(
    r"\b(?=[A-Za-z0-9]*[A-Za-z])(?=[A-Za-z0-9]*\d)[A-Za-z0-9]{10,}\b"
)


def redact_text(s, *, home: str | None = None, hostname: str | None = None):
    if not isinstance(s, str):
        return s
    s = _HOME_PATH.sub("~", s)
    stripped = home.rstrip("/") if home else ""
    if stripped:
        s = s.replace(stripped, "~")
    if hostname and len(hostname) >= 3:
        s = re.sub(rf"\b{re.escape(hostname)}\b", "HOST", s)
    s = _MAC.sub("[mac]", s)
    s = _UUID.sub("[uuid]", s)
    s = _SERIAL_LABELED.sub(lambda m: f"{m.group(1)}{m.group(2)}[serial]", s)
    s = _SERIAL_RUN.sub("[serial]", s)
    return s


def redact_obj(obj, *, home: str | None = None, hostname: str | None = None):
    if isinstance(obj, dict):
        out = {}
        for k, v in obj.items():
            if isinstance(k, str) and _SCRUB_KEY.search(k) and isinstance(v, (str, int, float)):
                out[k] = "[redacted]"
            else:
                out[k] = redact_obj(v, home=home, hostname=hostname)
        return out
    if isinstance(obj, (list, tuple)):
        return [redact_obj(x, home=home, hostname=hostname) for x in obj]
    return redact_text(obj, home=home, hostname=hostname)


def _read_str(path: str) -> str | None:
    try:
        with open(path) as f:
            return f.read().strip()
    except OSError:
        return None


def _tail_file(path: str, n: int) -> str:
    with open(path, "rb") as f:
        f.seek(0, os.SEEK_END)
        size = f.tell()
        start = max(0, size - n)
        f.seek(start)
        raw = f.read()
    txt = raw.decode("utf-8", "replace")
    if start > 0:
        nl = txt.find("\n")
        if nl != -1:
            txt = txt[nl + 1:]
    return txt


def tail_logs(
    log_dir: str,
    *,
    max_files: int = 3,
    max_bytes: int = 200_000,
    home: str | None = None,
    hostname: str | None = None,
) -> list[dict]:
    try:
        files = sorted(
            glob.glob(os.path.join(log_dir, "*.log")),
            key=os.path.getmtime,
            reverse=True,
        )
    except Exception:  # noqa: BLE001
        return []
    out: list[dict] = []
    budget = max_bytes
    for path in files[:max_files]:
        if budget <= 0:
            break
        try:
            data = _tail_file(path, budget)
        except Exception:  # noqa: BLE001
            continue
        out.append({
            "name": os.path.basename(path),
            "text": redact_text(data, home=home, hostname=hostname),
        })
        budget -= len(data)
    return out


_KERNEL_CMDS = {
    "dmesg": ["/usr/bin/dmesg", "--ctime", "--level=err,warn"],
    "journal": ["/usr/bin/journalctl", "-b", "-u", "plugin_loader", "-n", "400", "--no-pager"],
}


def rgb_conflict_cmds(conflicts: bool) -> dict:
    if not conflicts:
        return {}
    return {"hhd": ["/usr/bin/journalctl", "-b", "-u", "hhd.service", "-n", "300", "--no-pager"]}


def kernel_logs(
    run,
    *,
    cap: int = 40_000,
    extra: dict | None = None,
    home: str | None = None,
    hostname: str | None = None,
) -> dict:
    out = {}
    for key, cmd in {**_KERNEL_CMDS, **(extra or {})}.items():
        try:
            text = run(cmd)
        except Exception:  # noqa: BLE001
            text = None
        out[key] = redact_text(text[-cap:], home=home, hostname=hostname) if text else None
    return out


_SNAP_MAX_NODES = 64
_SNAP_MAX_MODULES = 512
_SNAP_CAP = 60_000
_LED_VALUE_NODES = ("multi_index", "max_brightness", "brightness", "enabled", "effect", "effect_index", "speed")


def _glob(root: str, pattern: str) -> list[str]:
    try:
        return glob.glob(os.path.join(root, pattern))
    except Exception:  # noqa: BLE001
        return []


def _snap_leds(root: str) -> list[dict]:
    out: list[dict] = []
    for led in sorted(_glob(root, "sys/class/leds/*"))[:_SNAP_MAX_NODES]:
        entry = {"name": os.path.basename(led), "has_multi_intensity":
                 os.path.exists(os.path.join(led, "multi_intensity"))}
        for node in _LED_VALUE_NODES:
            entry[node] = _read_str(os.path.join(led, node))
        out.append(entry)
    return out


def _snap_hid(root: str) -> list[dict]:
    out: list[dict] = []
    for dev in sorted(_glob(root, "sys/bus/hid/devices/*"))[:_SNAP_MAX_NODES]:
        uevent = _read_str(os.path.join(dev, "uevent")) or ""
        info = {"path": os.path.basename(dev)}
        for line in uevent.splitlines():
            if line.startswith("HID_ID=") or line.startswith("HID_NAME="):
                k, _, v = line.partition("=")
                info[k.lower()] = v
        out.append(info)
    return out


def _snap_modules(root: str) -> list[str]:
    names: list[str] = []
    try:
        with open(os.path.join(root, "proc/modules")) as f:
            for line in f:
                name = line.split(" ", 1)[0].strip()
                if name:
                    names.append(name)
                if len(names) >= _SNAP_MAX_MODULES:
                    break
    except OSError:
        return []
    return sorted(names)


def _snap_power_supply(root: str) -> dict:
    out: dict = {}
    for node in sorted(_glob(root, "sys/class/power_supply/*"))[:_SNAP_MAX_NODES]:
        out[os.path.basename(node)] = _read_str(os.path.join(node, "type"))
    return out


def _within(obj, cap: int) -> bool:
    try:
        return len(json.dumps(obj, default=str)) <= cap
    except Exception:  # noqa: BLE001
        return True


def sysfs_snapshot(
    root: str = "/",
    *,
    cap: int = _SNAP_CAP,
    home: str | None = None,
    hostname: str | None = None,
) -> dict:
    snap: dict = {"leds": [], "hid": [], "modules": [], "power_supply": {}}
    for key, fn in (
        ("leds", _snap_leds),
        ("hid", _snap_hid),
        ("modules", _snap_modules),
        ("power_supply", _snap_power_supply),
    ):
        try:
            snap[key] = fn(root)
        except Exception:  # noqa: BLE001
            pass
    if not _within(snap, cap):
        snap["truncated"] = True
        for key in ("modules", "hid", "leds", "power_supply"):
            if _within(snap, cap):
                break
            snap[key] = [] if isinstance(snap[key], list) else {}
    return redact_obj(snap, home=home, hostname=hostname)


def capabilities_from(state: dict, *, driver=None, led_path=None, last_error=None) -> dict:
    state = state or {}
    caps = state.get("capabilities") or {}
    dev = state.get("device") or {}
    effects = caps.get("supportedEffects")
    return {
        "device_name": dev.get("name"),
        "board": dev.get("board"),
        "product": dev.get("product"),
        "driver": caps.get("driver") or driver,
        "led_path": caps.get("ledPath") or led_path,
        "last_error": last_error,
        "color": bool(caps.get("color")),
        "brightness": bool(caps.get("brightness")),
        "zones": caps.get("zones"),
        "max_brightness": caps.get("maxBrightness"),
        "per_zone": bool(caps.get("perZone")),
        "per_controller_color": bool(caps.get("perControllerColor")),
        "hardware_effects": bool(caps.get("hardwareEffects")),
        "supported_effects": list(effects) if isinstance(effects, list) else [],
        "ambilight": bool(caps.get("ambilight")),
        "battery_mode": bool(caps.get("batteryMode")),
        "power_led": bool(caps.get("powerLed")),
        "reconnectable": bool(caps.get("reconnectable")),
        "conflicts_with_system_rgb": bool(caps.get("conflictsWithSystemRgb")),
        "enabled_experiments": caps.get("enabledExperiments") or [],
    }


def build_bundle(
    *,
    app: str,
    categories,
    text,
    environment: dict,
    capabilities: dict,
    state: dict,
    stores: dict,
    logs: list,
    kernel: dict | None = None,
    sysfs: dict | None = None,
    home: str | None = None,
    hostname: str | None = None,
) -> dict:
    bundle = {
        "schema": SCHEMA,
        "app": app,
        "categories": list(categories or []),
        "text": (text or "")[:_MAX_TEXT],
        "environment": environment or {},
        "capabilities": capabilities or {},
        "state": state or {},
        "stores": stores or {},
        "logs": logs or [],
        "kernel": kernel or {},
        "sysfs": sysfs or {},
    }
    return redact_obj(bundle, home=home, hostname=hostname)
