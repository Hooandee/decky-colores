# Aura RGB protocol for the ASUS ROG Ally line "N-KEY Device" (VID 0x0B05, Aura usage
# page 0xFF31 / usage 0x0080; PID varies: 0x1ABE original Ally, others on Ally X/Xbox Ally).
# The byte layout is an interface fact of the device, documented by asusctl/HHD; this
# is an original implementation (no third-party code copied). Report ids: 0x5D for RGB
# commands, 0x5A for brightness. All output reports are 64 bytes.

RGB_ID = 0x5D
DRIVER_ID = 0x5A

MODE_SOLID = 0x00
MODE_BREATHING = 0x01
MODE_COLORCYCLE = 0x02
MODE_RAINBOW = 0x03

ZONE_ALL = 0x00

# Flat zone index i maps to Aura zone code i + 1. Four zones by default; the
# Xbox Ally X20 (RC74XA) exposes eight: 1-4 left ring, 5-8 right ring, clockwise
# from the bottom-left.
def zone_codes(count):
    return tuple(range(0x01, max(1, int(count)) + 1))


_MODE_BY_EFFECT = {
    "solid": MODE_SOLID,
    "breathing": MODE_BREATHING,
    "rainbow": MODE_COLORCYCLE,
    "cycle": MODE_COLORCYCLE,
    "wave": MODE_RAINBOW,
    "spiral": MODE_RAINBOW,
}


def buf(values):
    data = bytes(values)
    return data + bytes(64 - len(data))


def _clamp8(v):
    return max(0, min(255, int(v)))


def pct_to_level(pct):
    pct = max(0, min(100, int(pct)))
    if pct <= 0:
        return 0
    if pct <= 33:
        return 1
    if pct <= 66:
        return 2
    return 3


def speed_to_code(pct):
    pct = max(0, min(100, int(pct)))
    if pct < 34:
        return 0xE1
    if pct < 67:
        return 0xEB
    return 0xF5


def mode_code(effect_id):
    return _MODE_BY_EFFECT.get(effect_id, MODE_SOLID)


def brightness_cmd(level):
    return buf([DRIVER_ID, 0xBA, 0xC5, 0xC4, max(0, min(3, int(level)))])


POWER_AWAKE = 0x02
POWER_SLEEP_CHARGING = 0x04
# The MCU keeps the Xbox button dark, whatever its level, until this bit is set.
POWER_XBOX_LED_AWAKE = 0x10


def power_mask_cmd(sleep_charging=False, xbox_led=False):
    mask = POWER_AWAKE
    if sleep_charging:
        mask |= POWER_SLEEP_CHARGING
    if xbox_led:
        mask |= POWER_XBOX_LED_AWAKE
    return buf([DRIVER_ID, 0xD1, 0x09, 0x01, mask])


def sleep_charging_power_cmd(enabled, xbox_led=False):
    return power_mask_cmd(sleep_charging=enabled, xbox_led=xbox_led)


def xbox_led_brightness_cmd(level):
    return buf([DRIVER_ID, 0xD1, 0x20, 0x01, max(0, min(3, int(level)))])


def xbox_led_support_query():
    return buf([DRIVER_ID, 0xD1, 0x1B, 0x03])


def xbox_led_supported(reply):
    # The RC74XA answers ff 3f 00; bit 3 of the second byte flags the Xbox button LED.
    data = bytes(reply or b"")
    return len(data) > 5 and data[:4] == bytes([DRIVER_ID, 0xD1, 0x1B, 0x03]) and bool(data[5] & 0x08)


def zone_cmd(zone, mode, r, g, b, speed=0x00, direction=0x00, r2=0, g2=0, b2=0):
    return buf(
        [
            RGB_ID,
            0xB3,
            zone,
            mode,
            _clamp8(r),
            _clamp8(g),
            _clamp8(b),
            speed if mode != MODE_SOLID else 0x00,
            direction,
            0x00,
            _clamp8(r2),
            _clamp8(g2),
            _clamp8(b2),
        ]
    )


def init_cmds():
    # ASUS "keyboard" handshake required before the firmware accepts RGB writes.
    # 1 (report id) + 14 ("ASUS Tech.Inc.") + 49 padding = 64 bytes.
    return [bytes([RGB_ID]) + b"ASUS Tech.Inc." + bytes(49)]


def latch_cmd():
    return buf([RGB_ID, 0xB5])


def set_apply_cmds():
    return [latch_cmd(), buf([RGB_ID, 0xB4])]


class AsusAllyTransport:
    def __init__(self, vid, pid, usage_page, usage):
        self._vid = vid
        self._pid = pid
        self._usage_page = usage_page
        self._usage = usage
        self.hid_device = None
        self.prev_mode = None

    def is_ready(self):
        if self.hid_device:
            return True
        import lib_hid as hid

        for device in hid.enumerate():
            if device["vendor_id"] not in self._vid:
                continue
            if self._pid and device["product_id"] not in self._pid:
                continue
            if device["usage_page"] in self._usage_page and device["usage"] in self._usage:
                self.hid_device = hid.Device(path=device["path"])
                return True
        return False

    def awake_restore_cmds(self, sleep_charging=False, xbox_led_level=0):
        if not self.is_ready():
            return []
        reps = [power_mask_cmd(sleep_charging, xbox_led=xbox_led_level > 0)]
        if xbox_led_level > 0:
            reps.append(xbox_led_brightness_cmd(xbox_led_level))
        return reps
