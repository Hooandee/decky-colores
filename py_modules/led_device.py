import os


def _clamp8(value):
    return max(0, min(255, int(value)))


class LedDevice:
    @property
    def available(self):
        return False

    def supports_per_zone(self):
        return False

    def apply_zones(self, zone_colors, brightness, power):
        return False


class NullDevice(LedDevice):
    pass


class SysfsRgbDevice(LedDevice):
    def __init__(self, led_path, zones=1, max_brightness=255, color_order="rgb", index_format="hex"):
        self._led_path = led_path
        self._zones = max(1, zones)
        self._max_brightness = max_brightness or 255
        self._color_order = color_order
        self._index_format = index_format
        self.last_error = None
        self._intensity_path = os.path.join(led_path, "multi_intensity") if led_path else None
        self._brightness_path = os.path.join(led_path, "brightness") if led_path else None
        self._has_intensity = bool(self._intensity_path) and os.path.exists(self._intensity_path)
        self._has_brightness = bool(self._brightness_path) and os.path.exists(self._brightness_path)

    @property
    def available(self):
        return bool(self._led_path)

    def supports_per_zone(self):
        return True

    def _level(self, brightness, power):
        pct = max(0, min(100, brightness))
        return round((pct / 100) * self._max_brightness) if power else 0

    def _fit(self, zone_colors):
        colors = list(zone_colors) or [(0, 0, 0)]
        if len(colors) < self._zones:
            colors += [colors[-1]] * (self._zones - len(colors))
        return colors[: self._zones]

    def _order(self, color):
        r, g, b = (_clamp8(c) for c in color)
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
