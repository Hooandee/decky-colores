import os
import sys

from led_device import LedDevice, _clamp8, _clamp_pct

_HUESYNC_DIR = os.path.join(os.path.dirname(__file__), "huesync")
if _HUESYNC_DIR not in sys.path:
    sys.path.insert(0, _HUESYNC_DIR)

HID_AVAILABLE = False
_IMPORT_ERROR = None

try:
    from utils import Color, RGBMode
    from msi_led_device_hid import (
        MSILEDDeviceHID,
        MSIRGBConfig,
        MSIKeyFrame,
        MSIEffect,
        build_solid_color,
        normalize_speed,
        RGB_ZONES_PER_FRAME,
    )
    from legion_go_tablet_hid import (
        LegionGoTabletHID,
        rgb_set_profile as legion_rgb_set_profile,
        rgb_load_profile as legion_rgb_load_profile,
        rgb_enable as legion_rgb_enable,
    )
    from legion_led_device_hid import LegionGoLEDDeviceHID
    from hhd_legino_go_s_hid import rgb_enable, rgb_multi_load_settings

    HID_AVAILABLE = True
except Exception as error:  # pragma: no cover - exercised only without libhidapi
    _IMPORT_ERROR = error


MSI_IDS = {
    "vid": [0x0DB0],
    "pid": [0x1901, 0x1902],
    "usage_page": [0xFFA0, 0xFFF0],
    "usage": [0x0001, 0x0040],
}

LEGION_TABLET_IDS = {
    "vid": [0x17EF],
    "pid": [0x6182, 0x6183, 0x6184, 0x6185, 0x61EB, 0x61EC, 0x61ED, 0x61EE],
    "usage_page": [0xFFA0],
    "usage": [0x0001],
}

LEGION_GO_S_IDS = {
    "vid": [0x1A86],
    "pid": [0xE310, 0xE311],
    "usage_page": [0xFFA0],
    "usage": [0x0001],
    "interface": 3,
}


def _effect_mode(effect_id):
    if not HID_AVAILABLE:
        return None
    mapping = {
        "breathing": RGBMode.Pulse,
        "rainbow": RGBMode.Rainbow,
        "wave": RGBMode.Spiral,
        "cycle": RGBMode.Rainbow,
    }
    return mapping.get(effect_id, RGBMode.Solid)


def _msi_speed(speed):
    return max(0, min(20, round((max(0, min(100, int(speed))) / 100) * 20)))


def _legion_speed(speed):
    pct = max(0, min(100, int(speed)))
    if pct < 34:
        return "low"
    if pct < 67:
        return "medium"
    return "high"


class _BaseHidDevice(LedDevice):
    def __init__(self, transport):
        self._transport = transport

    @property
    def available(self):
        try:
            return bool(self._transport.is_ready())
        except Exception:
            return False


class MsiHidDevice(_BaseHidDevice):
    @classmethod
    def create(cls):
        if not HID_AVAILABLE:
            return None
        return cls(
            MSILEDDeviceHID(
                MSI_IDS["vid"],
                MSI_IDS["pid"],
                MSI_IDS["usage_page"],
                MSI_IDS["usage"],
            )
        )

    def supports_per_zone(self):
        return True

    def _solid_config(self, color, brightness):
        return build_solid_color(
            Color(*[_clamp8(c) for c in color]), brightness=_clamp_pct(brightness), speed=17
        )

    def _zone_config(self, zone_colors, brightness):
        colors = [tuple(_clamp8(c) for c in zc) for zc in zone_colors] or [(0, 0, 0)]
        if len(colors) < RGB_ZONES_PER_FRAME:
            colors = colors + [colors[-1]] * (RGB_ZONES_PER_FRAME - len(colors))
        zones = [Color(r, g, b) for (r, g, b) in colors[:RGB_ZONES_PER_FRAME]]
        return MSIRGBConfig(
            speed=normalize_speed(17),
            brightness=_clamp_pct(brightness),
            effect=MSIEffect.UNKNOWN_09,
            keyframes=[MSIKeyFrame(rgb_zones=zones)],
        )

    def apply_zones(self, zone_colors, brightness, power):
        if not power:
            return self.apply_solid((0, 0, 0), 0, False)
        try:
            return bool(self._transport.send_rgb_config(self._zone_config(zone_colors, brightness)))
        except Exception:
            return False

    def apply_solid(self, color, brightness, power):
        try:
            if not power:
                return bool(self._transport.set_led_color(Color(0, 0, 0), RGBMode.Disabled))
            return bool(self._transport.send_rgb_config(self._solid_config(color, brightness)))
        except Exception:
            return False

    def apply_hardware_effect(self, effect_id, color, speed, power):
        try:
            if not power:
                return bool(self._transport.set_led_color(Color(0, 0, 0), RGBMode.Disabled))
            return bool(
                self._transport.set_led_color(
                    Color(*[_clamp8(c) for c in color]),
                    _effect_mode(effect_id),
                    brightness=100,
                    speed=_msi_speed(speed),
                )
            )
        except Exception:
            return False


class _LegionHidDevice(_BaseHidDevice):
    def supports_per_zone(self):
        return False

    def supports_hardware_effects(self):
        return True

    def apply_zones(self, zone_colors, brightness, power):
        colors = list(zone_colors) or [(0, 0, 0)]
        return self.apply_solid(colors[0], brightness, power)


class LegionTabletHidDevice(_LegionHidDevice):
    @classmethod
    def create(cls):
        if not HID_AVAILABLE:
            return None
        return cls(
            LegionGoTabletHID(
                LEGION_TABLET_IDS["vid"],
                LEGION_TABLET_IDS["pid"],
                LEGION_TABLET_IDS["usage_page"],
                LEGION_TABLET_IDS["usage"],
            )
        )

    def _write(self, reps):
        if not self._transport.is_ready():
            return False
        device = self._transport.hid_device
        if device is None:
            return False
        for rep in reps:
            device.write(rep)
        return True

    def apply_zones(self, zone_colors, brightness, power):
        colors = list(zone_colors) or [(0, 0, 0)]
        left = colors[0]
        right = colors[1] if len(colors) > 1 else colors[0]
        try:
            if not power:
                return self._write(
                    [legion_rgb_enable("left", False), legion_rgb_enable("right", False)]
                )
            level = _clamp_pct(brightness) / 100.0
            lr, lg, lb = (_clamp8(c) for c in left)
            rr, rg, rb = (_clamp8(c) for c in right)
            reps = [
                legion_rgb_set_profile("left", 3, "solid", lr, lg, lb, brightness=level),
                legion_rgb_set_profile("right", 3, "solid", rr, rg, rb, brightness=level),
                legion_rgb_load_profile("left", 3),
                legion_rgb_load_profile("right", 3),
                legion_rgb_enable("left", True),
                legion_rgb_enable("right", True),
            ]
            return self._write(reps)
        except Exception:
            return False

    def apply_solid(self, color, brightness, power):
        try:
            if not power:
                return bool(self._transport.set_led_color(RGBMode.Disabled, Color(0, 0, 0)))
            return bool(
                self._transport.set_led_color(
                    RGBMode.Solid,
                    Color(*[_clamp8(c) for c in color]),
                    brightness=_clamp_pct(brightness),
                )
            )
        except Exception:
            return False

    def apply_hardware_effect(self, effect_id, color, speed, power):
        try:
            if not power:
                return bool(self._transport.set_led_color(RGBMode.Disabled, Color(0, 0, 0)))
            return bool(
                self._transport.set_led_color(
                    _effect_mode(effect_id),
                    Color(*[_clamp8(c) for c in color]),
                    brightness=100,
                    speed=_legion_speed(speed),
                )
            )
        except Exception:
            return False


class LegionGoSHidDevice(_LegionHidDevice):
    @classmethod
    def create(cls):
        if not HID_AVAILABLE:
            return None
        return cls(
            LegionGoLEDDeviceHID(
                LEGION_GO_S_IDS["vid"],
                LEGION_GO_S_IDS["pid"],
                LEGION_GO_S_IDS["usage_page"],
                LEGION_GO_S_IDS["usage"],
                interface=LEGION_GO_S_IDS["interface"],
            )
        )

    def _write(self, reps):
        if not self._transport.is_ready():
            return False
        device = self._transport.hid_device
        if device is None:
            return False
        for rep in reps:
            device.write(rep)
        return True

    def apply_solid(self, color, brightness, power):
        try:
            r, g, b = (_clamp8(c) for c in color)
            if not power or (r == 0 and g == 0 and b == 0):
                self._transport.prev_mode = None
                return self._write([rgb_enable(False)])
            init = self._transport.prev_mode != "solid"
            reps = rgb_multi_load_settings(
                "solid", 0x03, r, g, b, brightness=_clamp_pct(brightness) / 100.0,
                speed=1, init=init,
            )
            ok = self._write(reps)
            if ok:
                self._transport.prev_mode = "solid"
            return ok
        except Exception:
            return False

    def apply_hardware_effect(self, effect_id, color, speed, power):
        try:
            if not power:
                return bool(self._transport.set_led_color(Color(0, 0, 0), RGBMode.Disabled))
            return bool(
                self._transport.set_led_color(
                    Color(*[_clamp8(c) for c in color]),
                    _effect_mode(effect_id),
                )
            )
        except Exception:
            return False


HID_DRIVERS = {
    "hid_msi": MsiHidDevice,
    "hid_legion_tablet": LegionTabletHidDevice,
    "hid_legion_go_s": LegionGoSHidDevice,
}


def build_hid_device(driver):
    factory = HID_DRIVERS.get(driver)
    if factory is None or not HID_AVAILABLE:
        return None
    try:
        return factory.create()
    except Exception:
        return None
