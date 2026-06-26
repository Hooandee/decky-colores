import os


def _packed(r, g, b):
    return f"0x{r:02x}{g:02x}{b:02x}"


class LedController:
    def __init__(self, led_path, zones=1, max_brightness=255):
        self._led_path = led_path
        self._zones = max(1, zones)
        self._max_brightness = max_brightness or 255

    @property
    def available(self):
        return bool(self._led_path)

    def apply(self, color, brightness, power):
        if not self._led_path:
            return False

        r, g, b = color
        level = round((brightness / 100) * self._max_brightness) if power else 0

        try:
            intensity_path = os.path.join(self._led_path, "multi_intensity")
            if os.path.exists(intensity_path):
                values = " ".join(_packed(r, g, b) for _ in range(self._zones))
                with open(intensity_path, "w") as handle:
                    handle.write(values)

            brightness_path = os.path.join(self._led_path, "brightness")
            if os.path.exists(brightness_path):
                with open(brightness_path, "w") as handle:
                    handle.write(str(level))
            return True
        except OSError:
            return False
