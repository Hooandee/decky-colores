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

# Our flat zone indices 0..3 map to the four Aura zone codes (2 per stick ring).
ZONE_CODES = (0x01, 0x02, 0x03, 0x04)

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


def set_apply_cmds():
    return [buf([RGB_ID, 0xB5]), buf([RGB_ID, 0xB4])]


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
