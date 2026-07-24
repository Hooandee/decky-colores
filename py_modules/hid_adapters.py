import os
import sys

from led_device import LedDevice, _clamp8, _clamp_pct, apply_gain

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
    from asus_ally_hid import (
        AsusAllyTransport,
        brightness_cmd,
        zone_cmd,
        init_cmds,
        set_apply_cmds,
        pct_to_level,
        speed_to_code,
        mode_code,
        ZONE_CODES,
        MODE_SOLID,
    )
    from oxp_hid import (
        OxpHidTransport,
        brightness_cmd as oxp_brightness_cmd,
        solid_cmd as oxp_solid_cmd,
        LEVEL_HIGH as OXP_LEVEL_HIGH,
    )

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

# Match the Aura N-KEY interface by VID + usage only. The original Ally is PID 0x1ABE,
# but the Ally X / Xbox Ally enumerate the same interface under different PIDs; an empty
# pid list means "any PID", so one driver covers the whole ROG Ally line.
ASUS_ALLY_IDS = {
    "vid": [0x0B05],
    "pid": [],
    "usage_page": [0xFF31],
    "usage": [0x0080],
}

# OneXPlayer XFLY RGB interface (OneXFly F1 series, Apex). Match VID + usage; the RGB
# interface shares vid:pid 1A2C:B001 with a second HID interface, disambiguated by usage.
OXP_IDS = {
    "vid": [0x1A2C],
    "pid": [0xB001],
    "usage_page": [0xFF01],
    "usage": [0x0001],
}


def _effect_mode(effect_id):
    if not HID_AVAILABLE:
        return None
    mapping = {
        "breathing": RGBMode.Pulse,
        "rainbow": RGBMode.Rainbow,
        "wave": RGBMode.Spiral,
        "spiral": RGBMode.Spiral,
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
    _color_correction = (1.0, 1.0, 1.0)

    def __init__(self, transport):
        self._transport = transport

    def set_color_correction(self, gains):
        self._color_correction = tuple(gains)

    def invalidate(self):
        if hasattr(self._transport, "prev_mode"):
            self._transport.prev_mode = None

    def _correct(self, color):
        return apply_gain(color, self._color_correction)

    def _write(self, reps):
        device = self._transport.hid_device
        if device is None:
            if not self._transport.is_ready():
                return False
            device = self._transport.hid_device
        for rep in reps:
            device.write(rep)
        return True

    @property
    def available(self):
        try:
            return bool(self._transport.is_ready())
        except Exception:
            return False

    def reconnect(self):
        device = getattr(self._transport, "hid_device", None)
        if device is not None:
            try:
                device.close()
            except Exception:
                pass
        self._transport.hid_device = None
        if hasattr(self._transport, "prev_mode"):
            self._transport.prev_mode = None
        return self.available

    def _heal(self, action):
        try:
            if action():
                return True
        except Exception:
            pass
        if not self.reconnect():
            return False
        try:
            return bool(action())
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
        return self._heal(
            lambda: bool(self._transport.send_rgb_config(self._zone_config(zone_colors, brightness)))
        )

    def apply_solid(self, color, brightness, power):
        if not power:
            return self._heal(lambda: bool(self._transport.set_led_color(Color(0, 0, 0), RGBMode.Disabled)))
        return self._heal(
            lambda: bool(self._transport.send_rgb_config(self._solid_config(color, brightness)))
        )

    def apply_hardware_effect(self, effect_id, color, speed, power):
        if not power:
            return self._heal(lambda: bool(self._transport.set_led_color(Color(0, 0, 0), RGBMode.Disabled)))
        return self._heal(
            lambda: bool(
                self._transport.set_led_color(
                    Color(*[_clamp8(c) for c in color]),
                    _effect_mode(effect_id),
                    brightness=100,
                    speed=_msi_speed(speed),
                )
            )
        )


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

    def apply_zones(self, zone_colors, brightness, power):
        colors = list(zone_colors) or [(0, 0, 0)]
        left = colors[0]
        right = colors[1] if len(colors) > 1 else colors[0]
        if not power:
            return self._heal(
                lambda: self._write(
                    [legion_rgb_enable("left", False), legion_rgb_enable("right", False)]
                )
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
        return self._heal(lambda: self._write(reps))

    def apply_solid(self, color, brightness, power):
        if not power:
            return self._heal(lambda: bool(self._transport.set_led_color(RGBMode.Disabled, Color(0, 0, 0))))
        return self._heal(
            lambda: bool(
                self._transport.set_led_color(
                    RGBMode.Solid,
                    Color(*[_clamp8(c) for c in color]),
                    brightness=_clamp_pct(brightness),
                )
            )
        )

    def apply_hardware_effect(self, effect_id, color, speed, power):
        if not power:
            return self._heal(lambda: bool(self._transport.set_led_color(RGBMode.Disabled, Color(0, 0, 0))))
        return self._heal(
            lambda: bool(
                self._transport.set_led_color(
                    _effect_mode(effect_id),
                    Color(*[_clamp8(c) for c in color]),
                    brightness=100,
                    speed=_legion_speed(speed),
                )
            )
        )


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

    def apply_solid(self, color, brightness, power):
        r, g, b = (_clamp8(c) for c in color)
        if not power or (r == 0 and g == 0 and b == 0):
            self._transport.prev_mode = None
            return self._heal(lambda: self._write([rgb_enable(False)]))

        def _do():
            init = self._transport.prev_mode != "solid"
            reps = rgb_multi_load_settings(
                "solid", 0x03, r, g, b, brightness=_clamp_pct(brightness) / 100.0,
                speed=1, init=init,
            )
            ok = self._write(reps)
            if ok:
                self._transport.prev_mode = "solid"
            return ok

        return self._heal(_do)

    def apply_hardware_effect(self, effect_id, color, speed, power):
        if not power:
            return self._heal(lambda: bool(self._transport.set_led_color(Color(0, 0, 0), RGBMode.Disabled)))
        return self._heal(
            lambda: bool(
                self._transport.set_led_color(
                    Color(*[_clamp8(c) for c in color]),
                    _effect_mode(effect_id),
                )
            )
        )


class AsusAllyHidDevice(_BaseHidDevice):
    # Green is toned down to match the Ally calibration; the gain comes from the
    # profile's color_correction via set_color_correction (see _build_hid_context).

    @classmethod
    def create(cls):
        if not HID_AVAILABLE:
            return None
        return cls(
            AsusAllyTransport(
                ASUS_ALLY_IDS["vid"],
                ASUS_ALLY_IDS["pid"],
                ASUS_ALLY_IDS["usage_page"],
                ASUS_ALLY_IDS["usage"],
            )
        )

    def supports_per_zone(self):
        return True

    def supports_hardware_effects(self):
        return True

    def _fit(self, zone_colors):
        colors = [tuple(c) for c in zone_colors] or [(0, 0, 0)]
        if len(colors) < 4:
            colors += [colors[-1]] * (4 - len(colors))
        return [self._correct(c) for c in colors[:4]]

    def _off(self):
        def _do():
            reps = [brightness_cmd(0), zone_cmd(0x00, MODE_SOLID, 0, 0, 0), *set_apply_cmds()]
            ok = self._write(reps)
            if ok:
                self._transport.prev_mode = None
            return ok

        return self._heal(_do)

    def apply_zones(self, zone_colors, brightness, power):
        if not power:
            return self._off()

        def _do():
            new_mode = self._transport.prev_mode != "solid"
            reps = list(init_cmds()) if new_mode else []
            reps.append(brightness_cmd(pct_to_level(brightness)))
            for code, (r, g, b) in zip(ZONE_CODES, self._fit(zone_colors)):
                reps.append(zone_cmd(code, MODE_SOLID, r, g, b))
            if new_mode:
                reps.extend(set_apply_cmds())
            ok = self._write(reps)
            if ok:
                self._transport.prev_mode = "solid"
            return ok

        return self._heal(_do)

    def apply_solid(self, color, brightness, power):
        return self.apply_zones([tuple(color)] * 4, brightness, power)

    def apply_hardware_effect(self, effect_id, color, speed, power):
        if not power:
            return self._off()
        code = mode_code(effect_id)
        speed_byte = speed_to_code(speed)
        r, g, b = self._correct(color)

        def _do():
            reps = list(init_cmds())
            reps.append(brightness_cmd(3))
            for zone in ZONE_CODES:
                reps.append(zone_cmd(zone, code, r, g, b, speed=speed_byte))
            reps.extend(set_apply_cmds())
            ok = self._write(reps)
            if ok:
                self._transport.prev_mode = effect_id
            return ok

        return self._heal(_do)


class OxpHidDevice(_BaseHidDevice):
    # HID V2 (XFLY) is single-zone with 3 coarse hardware brightness levels, so we hold
    # hardware brightness at high and scale color in software (like the Ally driver).
    # Live color updates send only the solid command; the enable command is sent on
    # transitions so the 30fps effect path is one 64-byte write per frame.

    @classmethod
    def create(cls):
        if not HID_AVAILABLE:
            return None
        return cls(
            OxpHidTransport(
                OXP_IDS["vid"],
                OXP_IDS["pid"],
                OXP_IDS["usage_page"],
                OXP_IDS["usage"],
            )
        )

    def apply_solid(self, color, brightness, power):
        if not power:
            def _off():
                ok = self._write([oxp_brightness_cmd(False, OXP_LEVEL_HIGH)])
                if ok:
                    self._transport.prev_mode = None
                return ok

            return self._heal(_off)

        r, g, b = self._correct(color)
        scale = _clamp_pct(brightness) / 100.0
        scaled = (_clamp8(r * scale), _clamp8(g * scale), _clamp8(b * scale))

        def _do():
            reps = []
            if self._transport.prev_mode != "solid":
                reps.append(oxp_brightness_cmd(True, OXP_LEVEL_HIGH))
            reps.append(oxp_solid_cmd(*scaled))
            ok = self._write(reps)
            if ok:
                self._transport.prev_mode = "solid"
            return ok

        return self._heal(_do)

    def apply_zones(self, zone_colors, brightness, power):
        colors = list(zone_colors) or [(0, 0, 0)]
        return self.apply_solid(colors[0], brightness, power)


HID_DRIVERS = {
    "hid_msi": MsiHidDevice,
    "hid_legion_tablet": LegionTabletHidDevice,
    "hid_legion_go_s": LegionGoSHidDevice,
    "hid_asus_ally": AsusAllyHidDevice,
    "hid_oxp_v2": OxpHidDevice,
}


def build_hid_device(driver):
    factory = HID_DRIVERS.get(driver)
    if factory is None or not HID_AVAILABLE:
        return None
    try:
        return factory.create()
    except Exception:
        return None
