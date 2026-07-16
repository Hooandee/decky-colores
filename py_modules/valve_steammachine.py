import os
import glob

from led_device import LedDevice, _clamp8

# Write all LED attributes in the correct order to maximise the window where
# our values are live before the Steam client (or another tool) overwrites
# them.  The key insight: write *enabled* first (so the EC keeps the LED
# engine on), then *effect=manual* (so the EC won't run a firmware
# animation), then color + brightness last so those are the freshest values.
#
# We do NOT cache "last written effect" — the Steam client can silently
# reset effect to e.g. "rainbow" or "off" at any time, so every apply
# must unconditionally re-set it.


class ValveSteamMachineDevice(LedDevice):
    """Driver for Steam Machine (Fremont) with 17 individually addressable valve-leds."""

    def __init__(self, zones=17, max_brightness=255, color_order="rgb",
                 color_correction=(1.0, 1.0, 1.0)):
        self._zones = zones
        self._max_brightness = max_brightness or 255
        self._color_order = color_order
        self._color_correction = tuple(color_correction)
        self._led_paths = []
        self._effect_paths = []
        self.last_error = None
        self._discover_leds()

    def _discover_leds(self):
        """Find all valve-leds[0] through valve-leds[N] in /sys/class/leds/."""
        leds_dir = "/sys/class/leds"
        if not os.path.isdir(leds_dir):
            return

        paths = []
        for i in range(20):
            path = os.path.join(leds_dir, f"valve-leds[{i}]")
            if os.path.isdir(path) and os.path.exists(os.path.join(path, "multi_intensity")):
                paths.append(path)

        self._led_paths = sorted(paths, key=lambda p: int(p.split("[")[-1].rstrip("]")))
        self._zones = len(self._led_paths)

        # Take exclusive control: stop any running hardware effect immediately
        if self._led_paths:
            self._seize_control()

    def _seize_control(self):
        """Kill any running hardware effect and claim exclusive sysfs control."""
        for path in self._led_paths:
            try:
                enabled_path = os.path.join(path, "enabled")
                effect_path = os.path.join(path, "effect")
                intensity_path = os.path.join(path, "multi_intensity")

                # 1. Make sure the LED engine is on
                try:
                    with open(enabled_path, "w") as f:
                        f.write("1")
                except OSError:
                    pass

                # 2. Stop any firmware effect
                with open(effect_path, "w") as f:
                    f.write("off")

                # 3. Zero out color
                with open(intensity_path, "w") as f:
                    f.write("0 0 0")

                # 4. Now set to manual for software control
                with open(effect_path, "w") as f:
                    f.write("manual")
            except OSError:
                pass

    @property
    def available(self):
        return len(self._led_paths) > 0

    @property
    def led_path(self):
        if self._led_paths:
            return self._led_paths[0]
        return None

    def supports_per_zone(self):
        return True

    def _order(self, color):
        r, g, b = color
        r = _clamp8(round(r * self._color_correction[0]))
        g = _clamp8(round(g * self._color_correction[1]))
        b = _clamp8(round(b * self._color_correction[2]))
        if self._color_order == "bgr":
            return b, g, r
        return r, g, b

    def _fit(self, zone_colors):
        colors = list(zone_colors) or [(0, 0, 0)]
        if len(colors) < self._zones:
            colors += [colors[-1]] * (self._zones - len(colors))
        return colors[:self._zones]

    def apply_zones(self, zone_colors, brightness, power):
        self.last_error = None
        if not self._led_paths:
            self.last_error = "no valve-leds found"
            return False

        level = round((max(0, min(100, brightness)) / 100) * self._max_brightness) if power else 0
        colors = self._fit(zone_colors)

        try:
            for i, path in enumerate(self._led_paths):
                if i >= len(colors):
                    break
                r, g, b = self._order(colors[i])
                enabled_path = os.path.join(path, "enabled")
                effect_path = os.path.join(path, "effect")
                intensity_path = os.path.join(path, "multi_intensity")
                brightness_path = os.path.join(path, "brightness")

                # Write order matters: enabled -> effect -> color -> brightness
                # The last write is the most "fresh" when Steam tries to override
                try:
                    with open(enabled_path, "w") as f:
                        f.write("1")
                except OSError:
                    pass

                try:
                    with open(effect_path, "w") as f:
                        f.write("manual")
                except OSError:
                    pass

                with open(intensity_path, "w") as f:
                    f.write(f"{r} {g} {b}")

                with open(brightness_path, "w") as f:
                    f.write(str(level))

            return True
        except OSError as error:
            self.last_error = str(error)
            return False

    def apply_solid(self, color, brightness, power):
        return self.apply_zones([color] * self._zones, brightness, power)

    def apply_hardware_effect(self, effect_id, color, speed, power):
        if not self._led_paths:
            return False

        # Map decky-colores effect IDs to valve-leds effect names
        effect_map = {
            "breathing": "breath",
            "rainbow": "rainbow",
            "wave": "patrol",
            "cycle": "rainbow",
            "spiral": "demo",
        }
        valve_effect = effect_map.get(effect_id, "manual")

        try:
            for path in self._led_paths:
                effect_path = os.path.join(path, "effect")
                with open(effect_path, "w") as f:
                    f.write(valve_effect)

                # Set base color for single-color effects
                if valve_effect in ("breath", "patrol"):
                    r, g, b = self._order(color)
                    intensity_path = os.path.join(path, "multi_intensity")
                    with open(intensity_path, "w") as f:
                        f.write(f"{r} {g} {b}")

            return True
        except OSError as error:
            self.last_error = str(error)
            return False

    def reconnect(self):
        self._discover_leds()
        return self.available

    def set_to_rainbow(self):
        """Restore the default Steam rainbow effect."""
        for path in self._led_paths:
            try:
                effect_path = os.path.join(path, "effect")
                with open(effect_path, "w") as f:
                    f.write("rainbow")
            except OSError:
                pass
