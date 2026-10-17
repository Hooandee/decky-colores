import math
import os

BACKLIGHT_ROOT = "/sys/class/backlight"
FLOOR = 0.10


def _read_int(path):
    try:
        with open(path, "r") as handle:
            return int(handle.read().strip())
    except (OSError, ValueError):
        return None


def has_backlight(root=BACKLIGHT_ROOT):
    try:
        names = os.listdir(root)
    except OSError:
        return False
    return any(
        (_read_int(os.path.join(root, name, "max_brightness")) or 0) > 0 for name in names
    )


def read_backlight(root=BACKLIGHT_ROOT):
    # The brightest lit panel is the one being adjusted: dual-screen handhelds dim the other.
    try:
        names = sorted(os.listdir(root))
    except OSError:
        return None
    best = None
    for name in names:
        node = os.path.join(root, name)
        bl_power = _read_int(os.path.join(node, "bl_power"))
        if bl_power not in (None, 0):
            continue
        maximum = _read_int(os.path.join(node, "max_brightness"))
        value = _read_int(os.path.join(node, "brightness"))
        if not maximum or maximum <= 0 or value is None or value <= 0:
            continue
        fraction = min(1.0, value / maximum)
        if best is None or fraction > best:
            best = fraction
    return best


def perceived_level(luminance):
    # CIE L*: backlight values are close to linear light, sliders are perceptual.
    y = max(0.0, min(1.0, luminance))
    if y <= 216 / 24389:
        return y * 24389 / 27 / 100
    return (116 * y ** (1 / 3) - 16) / 100


def led_brightness(user_brightness, factor):
    user = max(0, min(100, int(user_brightness)))
    if user == 0:
        return 0
    scaled = math.floor(user * max(FLOOR, min(1.0, factor)) + 0.5)
    return max(1, scaled)


class FactorRamp:
    def __init__(self, duration):
        self._duration = duration
        self._start_value = 1.0
        self._target = 1.0
        self._start_at = 0.0

    @property
    def target(self):
        return self._target

    def snap(self, value):
        self._start_value = value
        self._target = value
        self._start_at = 0.0

    def set_target(self, target, now):
        self._start_value = self.value(now)
        self._target = target
        self._start_at = now

    def value(self, now):
        progress = self._progress(now)
        if progress >= 1.0:
            return self._target
        eased = 1 - (1 - progress) ** 3
        return self._start_value + (self._target - self._start_value) * eased

    def settled(self, now):
        return self._start_value == self._target or self._progress(now) >= 1.0

    def _progress(self, now):
        if self._duration <= 0:
            return 1.0
        return max(0.0, (now - self._start_at) / self._duration)
