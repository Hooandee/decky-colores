import os


def _clamp8(value):
    return max(0, min(255, int(value)))


def _packed(color):
    r, g, b = color
    return f"0x{_clamp8(r):02x}{_clamp8(g):02x}{_clamp8(b):02x}"


class LedController:
    def __init__(self, led_path, zones=1, max_brightness=255):
        self._led_path = led_path
        self._zones = max(1, zones)
        self._max_brightness = max_brightness or 255
        self.last_error = None

    @property
    def available(self):
        return bool(self._led_path)

    def _level(self, brightness, power):
        pct = max(0, min(100, brightness))
        return round((pct / 100) * self._max_brightness) if power else 0

    def _fit(self, zone_colors):
        colors = list(zone_colors) or [(0, 0, 0)]
        if len(colors) < self._zones:
            colors += [colors[-1]] * (self._zones - len(colors))
        return colors[: self._zones]

    def _write(self, zone_colors, level):
        self.last_error = None
        if not self._led_path:
            self.last_error = "no led path"
            return False
        try:
            intensity_path = os.path.join(self._led_path, "multi_intensity")
            if os.path.exists(intensity_path):
                values = " ".join(_packed(c) for c in self._fit(zone_colors))
                with open(intensity_path, "w") as handle:
                    handle.write(values)

            brightness_path = os.path.join(self._led_path, "brightness")
            if os.path.exists(brightness_path):
                with open(brightness_path, "w") as handle:
                    handle.write(str(level))
            return True
        except OSError as error:
            self.last_error = str(error)
            return False

    def apply(self, color, brightness, power):
        return self._write([color] * self._zones, self._level(brightness, power))

    def apply_zones(self, zone_colors, brightness, power):
        return self._write(zone_colors, self._level(brightness, power))
