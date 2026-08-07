import copy
import json
import os


_NESTED_FIELDS = {"effect", "ambilight"}


def _integer(value, fallback, minimum=0, maximum=100):
    if isinstance(value, bool):
        return fallback
    try:
        return max(minimum, min(maximum, int(value)))
    except (TypeError, ValueError):
        return fallback


def _boolean(value, fallback):
    return value if isinstance(value, bool) else fallback


def _color(value, fallback):
    if not isinstance(value, (list, tuple)) or len(value) != 3:
        return copy.deepcopy(fallback)
    return [_integer(channel, 0, 0, 255) for channel in value]


class LightingProfileStore:
    schema_version = 1

    def __init__(self, path, defaults, legacy_profile=None):
        self._path = os.fspath(path)
        self._defaults = self._normalize(defaults, defaults)
        exists = os.path.exists(self._path)
        self._data = self._load()
        if not exists and legacy_profile is not None:
            self._data["global"]["profiles"]["default"] = self._normalize(
                legacy_profile, self._defaults
            )
        if not exists or legacy_profile is not None:
            self._save()

    def effective(self, app_key):
        if app_key is not None and not self.is_following_global(app_key):
            source = self._app_default(self._key(app_key))
        else:
            source = self._global_default()
        return copy.deepcopy(source)

    def editable(self, scope, app_key):
        if scope == "global":
            return copy.deepcopy(self._global_default())
        if scope != "game":
            raise ValueError("invalid profile scope")
        key = self._key(app_key)
        entry = self._data["apps"].get(key)
        source = self._global_default() if entry is None else self._entry_profile(entry)
        return copy.deepcopy(source)

    def patch(self, scope, app_key, changes):
        if not isinstance(changes, dict):
            raise ValueError("profile changes must be an object")
        current = self.editable(scope, app_key)
        merged = copy.deepcopy(current)
        for key, value in changes.items():
            if key not in self._defaults:
                continue
            if key in _NESTED_FIELDS and isinstance(value, dict):
                merged[key] = {**merged[key], **value}
            else:
                merged[key] = value
        normalized = self._normalize(merged, self._defaults)
        if scope == "global":
            self._data["global"]["profiles"]["default"] = normalized
        else:
            key = self._key(app_key)
            entry = self._data["apps"].get(key)
            if entry is None:
                entry = self._new_entry(normalized, follows_global=False)
                self._data["apps"][key] = entry
            else:
                entry["profiles"]["default"] = normalized
        self._save()
        return copy.deepcopy(normalized)

    def set_follow_global(self, app_key, follow):
        key = self._key(app_key)
        entry = self._data["apps"].get(key)
        if entry is None:
            entry = self._new_entry(self._global_default(), follows_global=bool(follow))
            self._data["apps"][key] = entry
        else:
            entry["followsGlobal"] = bool(follow)
        self._save()
        return self.scope_state(key)

    def forget(self, app_key):
        self._data["apps"].pop(self._key(app_key), None)
        self._save()

    def scope_state(self, app_key):
        if app_key is None:
            return {"has_game_profile": False, "follows_global": True}
        key = self._key(app_key)
        has_game = key in self._data["apps"]
        return {
            "has_game_profile": has_game,
            "follows_global": not has_game or self.is_following_global(key),
        }

    def is_following_global(self, app_key):
        entry = self._data["apps"].get(self._key(app_key))
        return entry is None or entry.get("followsGlobal", True) is True

    def configured_count(self):
        return len(self._data["apps"])

    def _key(self, app_key):
        if app_key is None or not str(app_key).strip():
            raise ValueError("game profile requires an app key")
        return str(app_key)

    def _global_default(self):
        return self._entry_profile(self._data["global"])

    def _app_default(self, key):
        return self._entry_profile(self._data["apps"][key])

    def _entry_profile(self, entry):
        return entry["profiles"]["default"]

    def _new_entry(self, profile, follows_global=None):
        entry = {
            "profiles": {"default": copy.deepcopy(profile)},
            "activeProfile": "default",
        }
        if follows_global is not None:
            entry["followsGlobal"] = follows_global
        return entry

    def _empty_data(self):
        return {
            "schemaVersion": self.schema_version,
            "global": self._new_entry(self._defaults),
            "apps": {},
        }

    def _load(self):
        try:
            with open(self._path) as handle:
                stored = json.load(handle)
        except (OSError, ValueError, TypeError):
            return self._empty_data()
        if not isinstance(stored, dict) or stored.get("schemaVersion") != self.schema_version:
            return self._empty_data()
        result = self._empty_data()
        global_entry = stored.get("global")
        if isinstance(global_entry, dict):
            result["global"] = self._clean_entry(global_entry, self._defaults)
        apps = stored.get("apps")
        if isinstance(apps, dict):
            for raw_key, raw_entry in apps.items():
                if not isinstance(raw_key, str) or not raw_key or not isinstance(raw_entry, dict):
                    continue
                result["apps"][raw_key] = self._clean_entry(
                    raw_entry,
                    result["global"]["profiles"]["default"],
                    include_follow=True,
                )
        return result

    def _clean_entry(self, entry, fallback, include_follow=False):
        profiles = entry.get("profiles")
        profile = profiles.get("default") if isinstance(profiles, dict) else None
        clean = self._new_entry(self._normalize(profile, fallback))
        if include_follow:
            clean["followsGlobal"] = _boolean(entry.get("followsGlobal"), True)
        return clean

    def _normalize(self, profile, fallback):
        source = profile if isinstance(profile, dict) else {}
        base = copy.deepcopy(fallback)
        result = {}
        result["brightness"] = _integer(
            source.get("brightness"), base["brightness"]
        )
        mode = source.get("mode")
        result["mode"] = mode if isinstance(mode, str) and mode else base["mode"]
        result["color"] = _color(source.get("color"), base["color"])
        raw_gradient = source.get("gradient")
        if isinstance(raw_gradient, list) and raw_gradient:
            result["gradient"] = [_color(stop, base["color"]) for stop in raw_gradient]
        else:
            result["gradient"] = copy.deepcopy(base["gradient"])
        result["gradient_speed"] = _integer(
            source.get("gradient_speed"), base["gradient_speed"]
        )

        raw_effect = source.get("effect") if isinstance(source.get("effect"), dict) else {}
        base_effect = base["effect"]
        effect_id = raw_effect.get("id")
        result["effect"] = {
            "id": effect_id if isinstance(effect_id, str) and effect_id else base_effect["id"],
            "speed": _integer(raw_effect.get("speed"), base_effect["speed"]),
            "use_gradient": _boolean(
                raw_effect.get("use_gradient"), base_effect["use_gradient"]
            ),
        }

        raw_ambient = source.get("ambilight") if isinstance(source.get("ambilight"), dict) else {}
        base_ambient = base["ambilight"]
        sampling = raw_ambient.get("sampling")
        result["ambilight"] = {
            "vividness": _integer(raw_ambient.get("vividness"), base_ambient["vividness"]),
            "smoothing": _integer(raw_ambient.get("smoothing"), base_ambient["smoothing"]),
            "fps": _integer(raw_ambient.get("fps"), base_ambient["fps"], 1, 60),
            "sampling": sampling if sampling in ("columns", "average") else base_ambient["sampling"],
        }
        result["battery_breathe"] = _boolean(
            source.get("battery_breathe"), base["battery_breathe"]
        )
        result["temperature_breathe"] = _boolean(
            source.get("temperature_breathe"), base["temperature_breathe"]
        )
        return result

    def _save(self):
        directory = os.path.dirname(self._path)
        if directory:
            os.makedirs(directory, exist_ok=True)
        temporary = self._path + ".tmp"
        with open(temporary, "w") as handle:
            json.dump(self._data, handle)
        os.replace(temporary, self._path)
