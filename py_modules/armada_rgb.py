"""Armada OS RGB profile loading and sysfs backends.

The catalog format intentionally mirrors armada-rgb v1. Colores reads the
system copy when available and keeps a bundled copy so older Armada images can
use the same hardware support without modifying the OS configuration.
"""

import json
import logging
import math
import os
import re
import threading

from led_device import LedDevice


logger = logging.getLogger(__name__)

CATALOG_VERSION = 1
SYSTEM_CATALOG = "usr/share/armada-rgb/profiles.json"
BUNDLED_CATALOG = os.path.join(os.path.dirname(__file__), "armada_rgb_profiles.json")
_SAFE_TARGET = re.compile(r"^[A-Za-z0-9:_.-]+$")
_CHANNELS = ("red", "green", "blue")
_CORRECTION_TRIGGERS = {"always", *_CHANNELS}
_ARMADA_WRITE_LOCK = threading.RLock()


class ArmadaProfileError(ValueError):
    pass


def _exact_keys(value, expected, context):
    if not isinstance(value, dict) or set(value) != set(expected):
        raise ArmadaProfileError(f"invalid {context} fields")


def _validate_name(name, context="target"):
    if (
        not isinstance(name, str)
        or not name
        or name in {".", ".."}
        or not _SAFE_TARGET.fullmatch(name)
    ):
        raise ArmadaProfileError(f"invalid {context} name")


def _validate_correction(value):
    _exact_keys(value, {"trigger", *_CHANNELS}, "correction")
    if value["trigger"] not in _CORRECTION_TRIGGERS:
        raise ArmadaProfileError("invalid correction trigger")
    for channel in _CHANNELS:
        reduction = value[channel]
        if isinstance(reduction, bool) or not isinstance(reduction, int) or not 0 <= reduction <= 100:
            raise ArmadaProfileError(f"invalid {channel} correction")
    return dict(value)


def validate_catalog(raw):
    _exact_keys(raw, {"version", "profiles"}, "catalog")
    if (
        type(raw["version"]) is not int
        or raw["version"] != CATALOG_VERSION
        or not isinstance(raw["profiles"], list)
    ):
        raise ArmadaProfileError("unsupported Armada RGB catalog version")

    profiles = []
    seen_models = set()
    for raw_profile in raw["profiles"]:
        allowed = {"models", "backend", "correction"}
        if not isinstance(raw_profile, dict) or not set(raw_profile).issubset(allowed):
            raise ArmadaProfileError("invalid profile fields")
        if not {"models", "backend"}.issubset(raw_profile):
            raise ArmadaProfileError("profile is missing required fields")

        models = raw_profile["models"]
        if not isinstance(models, list) or not models:
            raise ArmadaProfileError("profile model list is empty")
        for model in models:
            if not isinstance(model, str) or not model.strip() or model != model.strip():
                raise ArmadaProfileError("invalid model name")
            if model in seen_models:
                raise ArmadaProfileError(f"duplicate model '{model}'")
            seen_models.add(model)

        backend = raw_profile["backend"]
        _exact_keys(backend, {"type", "targets"}, "backend")
        backend_type = backend["type"]
        targets = backend["targets"]
        if backend_type not in {"multicolor", "channels"}:
            raise ArmadaProfileError("unsupported backend type")
        if not isinstance(targets, list) or not targets:
            raise ArmadaProfileError("RGB target list is empty")

        names = []
        channel_names = {channel: [] for channel in _CHANNELS}
        for target in targets:
            if backend_type == "channels":
                if not isinstance(target, str) or target.count("=") != 1:
                    raise ArmadaProfileError("invalid channel target")
                channel, name = target.split("=", 1)
                if channel not in channel_names:
                    raise ArmadaProfileError("invalid RGB channel")
                _validate_name(name)
                channel_names[channel].append(name)
            else:
                name = target
                _validate_name(name)
            names.append(name)

        if len(names) != len(set(names)):
            raise ArmadaProfileError("duplicate RGB target")
        if backend_type == "channels":
            counts = {len(values) for values in channel_names.values()}
            if len(counts) != 1 or counts == {0}:
                raise ArmadaProfileError("RGB channel lists must have equal lengths")

        profile = {
            "models": list(models),
            "backend": {"type": backend_type, "targets": list(targets)},
        }
        if "correction" in raw_profile:
            profile["correction"] = _validate_correction(raw_profile["correction"])
        profiles.append(profile)
    return {"version": CATALOG_VERSION, "profiles": profiles}


def load_catalog(path):
    try:
        with open(path, encoding="utf-8") as handle:
            return validate_catalog(json.load(handle))
    except (OSError, UnicodeError, json.JSONDecodeError, ArmadaProfileError) as error:
        raise ArmadaProfileError(str(error)) from error


def catalog_for_root(sysfs_root="/"):
    system_path = os.path.join(sysfs_root, SYSTEM_CATALOG)
    if os.path.isfile(system_path):
        try:
            return load_catalog(system_path)
        except ArmadaProfileError as error:
            logger.warning("Ignoring invalid Armada RGB catalog %s: %s", system_path, error)
    return load_catalog(BUNDLED_CATALOG)


def profile_for_model(model, sysfs_root="/"):
    if not model:
        return None
    try:
        catalog = catalog_for_root(sysfs_root)
    except ArmadaProfileError as error:
        logger.error("Cannot load bundled Armada RGB catalog: %s", error)
        return None
    for profile in catalog["profiles"]:
        if model in profile["models"]:
            return profile
    return None


def _read_positive(path):
    with open(path, encoding="ascii") as handle:
        value = int(handle.read().strip())
    if value <= 0:
        raise ValueError(f"{path} must be positive")
    return value


def _gamma(channel, maximum):
    value = max(0, min(255, int(channel))) / 255
    linear = value / 12.92 if value <= 0.04045 else math.pow((value + 0.055) / 1.055, 2.4)
    return math.floor(linear * maximum + 0.5)


def _scale(percent, maximum):
    return (max(0, min(100, int(percent))) * maximum + 50) // 100


def _correct(color, correction):
    rgb = [max(0, min(255, int(channel))) for channel in color]
    if not correction:
        return tuple(rgb)
    trigger = correction["trigger"]
    triggered = trigger == "always" or rgb[_CHANNELS.index(trigger)] > 0
    if not triggered:
        return tuple(rgb)
    return tuple((value * (100 - correction[channel]) + 50) // 100 for channel, value in zip(_CHANNELS, rgb))


def _write_open_file(handle, value):
    data = f"{value}\n".encode("ascii")
    offset = 0
    while offset < len(data):
        written = os.write(handle, data[offset:])
        if written <= 0:
            raise OSError("short sysfs write")
        offset += written
    try:
        os.ftruncate(handle, len(data))
    except OSError:
        # sysfs attributes do not support truncation; regular files in tests do.
        pass


def _open_write(path):
    return os.open(path, os.O_WRONLY | getattr(os, "O_CLOEXEC", 0))


def _close_all(prepared):
    for item in prepared:
        for handle in item.get("handles", ()):
            try:
                os.close(handle)
            except OSError:
                pass


class ArmadaRgbDevice(LedDevice):
    def __init__(self, leds_dir, backend, correction=None):
        self._leds_dir = leds_dir
        self._backend = backend["type"]
        self._targets = list(backend["targets"])
        self._correction = correction
        self._zones = self._zone_count()
        self._max_brightness = 255
        self.last_error = None
        # A channels profile (Odin 3) is one logical frame spread over 24
        # sysfs attributes. Keep each frame/blank transaction indivisible if
        # callers ever reach this device from more than one worker thread.
        self._write_lock = _ARMADA_WRITE_LOCK

    def _zone_count(self):
        if self._backend == "multicolor":
            return len(self._targets)
        return sum(target.startswith("red=") for target in self._targets)

    @property
    def available(self):
        try:
            self._inspect()
            return True
        except (OSError, UnicodeError, ValueError):
            return False

    @property
    def led_path(self):
        if not self._targets:
            return None
        name = self._targets[0].split("=", 1)[-1]
        return os.path.join(self._leds_dir, name)

    @property
    def zone_count(self):
        return self._zones

    def supports_per_zone(self):
        return self._zones > 1

    def reconnect(self):
        return self.available

    def _inspect(self):
        if self._backend == "channels":
            for target in self._targets:
                _, name = target.split("=", 1)
                node = os.path.join(self._leds_dir, name)
                _read_positive(os.path.join(node, "max_brightness"))
                if not os.path.isfile(os.path.join(node, "brightness")):
                    raise OSError(f"missing {name} brightness")
            return
        for name in self._targets:
            node = os.path.join(self._leds_dir, name)
            with open(os.path.join(node, "multi_index"), encoding="ascii") as handle:
                order = handle.read().lower().split()
            if len(order) != 3 or set(order) != set(_CHANNELS):
                raise ValueError(f"invalid RGB order for {name}")
            _read_positive(os.path.join(node, "max_brightness"))
            for attribute in ("multi_intensity", "brightness"):
                if not os.path.isfile(os.path.join(node, attribute)):
                    raise OSError(f"missing {name} {attribute}")

    def _channel_zones(self):
        channels = {channel: [] for channel in _CHANNELS}
        for target in self._targets:
            channel, name = target.split("=", 1)
            channels[channel].append(name)
        return list(zip(*(channels[channel] for channel in _CHANNELS)))

    def _prepare_channels(self, colors, brightness, power):
        prepared = []
        try:
            for names, color in zip(self._channel_zones(), colors):
                corrected = _correct(color, self._correction)
                for channel_index, name in enumerate(names):
                    path = os.path.join(self._leds_dir, name)
                    value = 0
                    if power:
                        maximum = _read_positive(os.path.join(path, "max_brightness"))
                        value = _scale(brightness, _gamma(corrected[channel_index], maximum))
                    handle = _open_write(os.path.join(path, "brightness"))
                    prepared.append({
                        "name": name,
                        "brightness_path": os.path.join(path, "brightness"),
                        "handles": [handle],
                        "writes": [(handle, str(value))],
                    })
            return prepared
        except Exception:
            _close_all(prepared)
            raise

    def _prepare_multicolor(self, colors, brightness, power):
        prepared = []
        try:
            for name, color in zip(self._targets, colors):
                path = os.path.join(self._leds_dir, name)
                brightness_path = os.path.join(path, "brightness")
                handles = []
                writes = []
                try:
                    blank_handle = _open_write(brightness_path)
                    handles.append(blank_handle)
                    if power:
                        maximum = _read_positive(os.path.join(path, "max_brightness"))
                        with open(os.path.join(path, "multi_index"), encoding="ascii") as handle:
                            order = handle.read().lower().split()
                        if len(order) != 3 or set(order) != set(_CHANNELS):
                            raise ValueError(f"invalid RGB order for {name}")
                        corrected = dict(zip(_CHANNELS, _correct(color, self._correction)))
                        intensity = " ".join(
                            str(_gamma(corrected[channel], maximum)) for channel in order
                        )
                        intensity_handle = _open_write(os.path.join(path, "multi_intensity"))
                        brightness_handle = _open_write(brightness_path)
                        handles.extend([intensity_handle, brightness_handle])
                        writes.extend([
                            (intensity_handle, intensity),
                            (brightness_handle, str(_scale(brightness, maximum))),
                        ])
                    else:
                        writes.append((blank_handle, "0"))
                    prepared.append({
                        "name": name,
                        "brightness_path": brightness_path,
                        "handles": handles,
                        "writes": writes,
                    })
                except Exception:
                    for handle in handles:
                        os.close(handle)
                    raise
            return prepared
        except Exception:
            _close_all(prepared)
            raise

    @staticmethod
    def _blank(prepared):
        for item in prepared:
            try:
                handle = _open_write(item["brightness_path"])
                try:
                    _write_open_file(handle, "0")
                finally:
                    os.close(handle)
            except OSError:
                pass

    def apply_zones(self, zone_colors, brightness, power):
        with self._write_lock:
            self.last_error = None
            colors = self._fit(zone_colors)
            prepared = []
            try:
                prepared = (
                    self._prepare_channels(colors, brightness, power)
                    if self._backend == "channels"
                    else self._prepare_multicolor(colors, brightness, power)
                )
                for item in prepared:
                    for handle, value in item["writes"]:
                        _write_open_file(handle, value)
                return True
            except (OSError, UnicodeError, ValueError) as error:
                self.last_error = str(error)
                self._blank(prepared)
                return False
            finally:
                _close_all(prepared)

    def apply_solid(self, color, brightness, power):
        return self.apply_zones([tuple(color)] * self._zones, brightness, power)


def build_armada_device(model, leds_dir, sysfs_root="/"):
    profile = profile_for_model(model, sysfs_root)
    if profile is None:
        return None
    device = ArmadaRgbDevice(
        leds_dir,
        profile["backend"],
        correction=profile.get("correction"),
    )
    return profile, device


def layout_for_profile(model, profile, zones):
    """Return calibrated Ambilight grouping only where physical sides are known."""
    side_models = {
        "AYN Odin 2 Portal",
        "AYN Thor",
        "AYN Thor Lite",
        "Retroid Pocket 5",
        "Retroid Pocket 5 Visionox",
        "Retroid Pocket Flip2",
        "Retroid Pocket Flip2 Visionox",
        "Retroid Pocket 6",
        "Retroid Pocket 6 TOP-DPAD",
        "Retroid Pocket Nova",
        "AYN Odin 3",
    }
    if model in side_models and zones > 1 and zones % 2 == 0:
        half = zones // 2
        return [
            {
                "name": "Left lights",
                "region": [0.0, 0.0, 0.20, 1.0],
                "zones": list(range(half)),
                "kind": "shared-edge",
            },
            {
                "name": "Right lights",
                "region": [0.80, 0.0, 1.0, 1.0],
                "zones": list(range(half, zones)),
                "kind": "shared-edge",
            },
        ]
    return [
        {
            "name": "Lights",
            "region": [0.0, 0.0, 1.0, 1.0],
            "zones": list(range(zones)),
            "kind": "shared-full",
        }
    ] if zones else []
