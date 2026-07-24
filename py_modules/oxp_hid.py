# OneXPlayer HID V2 ("XFLY") RGB protocol. Wire format documented by HHD and HueSync
# (BSD-3); original implementation, no third-party code copied.

CMD_ID = 0x07

LEVEL_HIGH = 0x04


def _clamp8(v):
    return max(0, min(255, int(v)))


def buf(payload):
    data = bytes([CMD_ID, 0xFF, *payload])
    return data + bytes(64 - len(data))


def brightness_cmd(enabled, code):
    return buf([0xFD, 1 if enabled else 0, 0x05, code])


def solid_cmd(r, g, b):
    triple = [_clamp8(r), _clamp8(g), _clamp8(b)]
    return buf([0xFE] + triple * 20 + [0x00])


class OxpHidTransport:
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
