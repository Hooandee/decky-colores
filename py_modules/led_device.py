import os
import re


def _clamp8(value):
    return max(0, min(255, int(value)))


def _clamp_pct(value):
    return max(0, min(100, int(value)))


def apply_gain(color, gains):
    return tuple(_clamp8(round(c * gains[i])) for i, c in enumerate(color))


class LedDevice:
    led_path = None
    last_error = None

    @property
    def available(self):
        return False

    def supports_per_zone(self):
        return False

    def supports_hardware_effects(self):
        return False

    def reconnect(self):
        return self.available

    def _level(self, brightness, power):
        return round((_clamp_pct(brightness) / 100) * self._max_brightness) if power else 0

    def _fit(self, zone_colors):
        colors = list(zone_colors) or [(0, 0, 0)]
        if len(colors) < self._zones:
            colors += [colors[-1]] * (self._zones - len(colors))
        return colors[: self._zones]

    def invalidate(self):
        # Drop any cached "already in this mode" state so the next apply re-sends the
        # full init/commit sequence. No-op for devices that always write in full (sysfs).
        return None

    def apply_zones(self, zone_colors, brightness, power):
        return False

    def apply_solid(self, color, brightness, power):
        return False

    def apply_hardware_effect(self, effect_id, color, speed, power):
        return False


class NullDevice(LedDevice):
    pass


class SysfsRgbDevice(LedDevice):
    def __init__(self, led_path, zones=1, max_brightness=255, color_order="rgb", index_format="hex", color_correction=(1.0, 1.0, 1.0)):
        self._led_path = led_path
        self._zones = max(1, zones)
        self._max_brightness = max_brightness or 255
        self._color_order = color_order
        self._index_format = index_format
        self._color_correction = tuple(color_correction)
        self.last_error = None
        self._intensity_path = os.path.join(led_path, "multi_intensity") if led_path else None
        self._brightness_path = os.path.join(led_path, "brightness") if led_path else None
        self._has_intensity = bool(self._intensity_path) and os.path.exists(self._intensity_path)
        self._has_brightness = bool(self._brightness_path) and os.path.exists(self._brightness_path)

    @property
    def available(self):
        return bool(self._led_path)

    @property
    def led_path(self):
        return self._led_path

    def supports_per_zone(self):
        return True

    def _order(self, color):
        r, g, b = apply_gain(color, self._color_correction)
        if self._color_order == "bgr":
            return b, g, r
        return r, g, b

    def _format_zone(self, color):
        r, g, b = self._order(color)
        if self._index_format == "decimal":
            return f"{r} {g} {b}"
        return f"0x{r:02x}{g:02x}{b:02x}"

    def apply_zones(self, zone_colors, brightness, power):
        self.last_error = None
        if not self._led_path:
            self.last_error = "no led path"
            return False
        level = self._level(brightness, power)
        try:
            if self._has_intensity:
                values = " ".join(self._format_zone(c) for c in self._fit(zone_colors))
                with open(self._intensity_path, "w") as handle:
                    handle.write(values)
            if self._has_brightness:
                with open(self._brightness_path, "w") as handle:
                    handle.write(str(level))
            return True
        except OSError as error:
            self.last_error = str(error)
            return False


_VALVE_NODE_RE = re.compile(r"^valve-leds\[(\d+)\]$")


def discover_valve_leds(leds_dir):
    try:
        entries = os.listdir(leds_dir)
    except OSError:
        return []
    nodes = []
    for name in entries:
        match = _VALVE_NODE_RE.match(name)
        if match:
            nodes.append((int(match.group(1)), os.path.join(leds_dir, name)))
    nodes.sort(key=lambda item: item[0])
    return [path for _, path in nodes]


class ValveLedsDevice(LedDevice):
    def __init__(self, node_paths, max_brightness=255, color_correction=(1.0, 1.0, 1.0), reverse=False):
        self._nodes = list(node_paths)
        self._write_order = list(reversed(self._nodes)) if reverse else list(self._nodes)
        self._zones = len(self._nodes)
        self._max_brightness = max_brightness or 255
        self._color_correction = tuple(color_correction)
        self.last_error = None
        self._ctrl = self._nodes[0] if self._nodes else None
        self._intensity_paths = [os.path.join(p, "multi_intensity") for p in self._write_order]
        self._startup_paths = [os.path.join(p, "multi_intensity_startup") for p in self._write_order]
        self._scale_path = os.path.join(self._ctrl, "brightness_scale") if self._ctrl else None
        self._brightness_startup_path = os.path.join(self._ctrl, "brightness_startup") if self._ctrl else None
        self._manual_set = False
        self._last_level = None
        self._last_intensity = None

    @property
    def available(self):
        return bool(self._nodes)

    @property
    def led_path(self):
        return self._ctrl

    def supports_per_zone(self):
        return True

    def supports_hardware_effects(self):
        return False

    def invalidate(self):
        self._manual_set = False
        self._last_level = None

    @staticmethod
    def _write(path, value):
        with open(path, "w") as handle:
            handle.write(value)

    def _ensure_manual(self):
        if self._manual_set or not self._ctrl:
            return
        try:
            self._write(os.path.join(self._ctrl, "enabled"), "1")
        except OSError:
            pass
        self._write(os.path.join(self._ctrl, "effect"), "manual")
        for path in self._nodes:
            try:
                self._write(os.path.join(path, "brightness"), str(self._max_brightness))
            except OSError:
                pass
        self._manual_set = True

    def apply_zones(self, zone_colors, brightness, power):
        self.last_error = None
        if not self._nodes:
            self.last_error = "no valve-leds nodes"
            return False
        level = self._level(brightness, power)
        try:
            self._ensure_manual()
            values = []
            for path, color in zip(self._intensity_paths, self._fit(zone_colors)):
                r, g, b = apply_gain(color, self._color_correction)
                text = f"{r} {g} {b}"
                self._write(path, text)
                values.append(text)
            self._last_intensity = values
            if level != self._last_level:
                self._write(self._scale_path, str(level))
                self._last_level = level
            return True
        except OSError as error:
            self.last_error = str(error)
            self._manual_set = False
            self._last_level = None
            return False

    def apply_solid(self, color, brightness, power):
        return self.apply_zones([tuple(color)] * self._zones, brightness, power)

    def save_startup(self):
        if not self._nodes or self._last_intensity is None:
            return False
        try:
            for path, value in zip(self._startup_paths, self._last_intensity):
                self._write(path, value)
            if self._last_level is not None and self._brightness_startup_path:
                self._write(self._brightness_startup_path, str(self._last_level))
            return True
        except OSError as error:
            self.last_error = str(error)
            return False

    def read_startup(self):
        if not self._nodes:
            return None
        try:
            intensities = [open(path).read().strip() for path in self._startup_paths]
            level = open(self._brightness_startup_path).read().strip()
            return {"intensities": intensities, "level": level}
        except OSError:
            return None

    def restore_startup(self, saved):
        if not self._nodes or not saved:
            return False
        try:
            for path, value in zip(self._startup_paths, saved.get("intensities", [])):
                self._write(path, value)
            level = saved.get("level")
            if level is not None and self._brightness_startup_path:
                self._write(self._brightness_startup_path, str(level))
            return True
        except OSError as error:
            self.last_error = str(error)
            return False
