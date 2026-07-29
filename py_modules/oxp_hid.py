# OneXPlayer HID V2 ("XFLY") RGB protocol. Wire format documented by HHD and HueSync
# (BSD-3); original implementation, no third-party code copied.

import logging


logger = logging.getLogger(__name__)

CMD_ID = 0x07

LEVEL_HIGH = 0x04
WRITE_DELAY = 0.05
STATE_CHANGE_DELAY = 0.3


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
        self.write_delay = WRITE_DELAY
        self._last_write_at = None
        self.last_error = None
        self._reported_missing = False

    def is_ready(self):
        if self.hid_device:
            return True
        import lib_hid as hid

        try:
            devices = hid.enumerate()
        except Exception as exc:
            self.last_error = f"enumeration failed: {type(exc).__name__}"
            logger.warning("OneXPlayer HID enumeration failed: %s", type(exc).__name__)
            return False
        for device in devices:
            if device["vendor_id"] not in self._vid:
                continue
            if self._pid and device["product_id"] not in self._pid:
                continue
            if device["usage_page"] in self._usage_page and device["usage"] in self._usage:
                try:
                    self.hid_device = hid.Device(path=device["path"])
                except Exception as exc:
                    self.last_error = f"open failed: {type(exc).__name__}"
                    logger.warning(
                        "OneXPlayer HID open failed vid=%04x pid=%04x usage_page=%04x usage=%04x interface=%s error=%s",
                        device["vendor_id"],
                        device["product_id"],
                        device["usage_page"],
                        device["usage"],
                        device.get("interface_number"),
                        type(exc).__name__,
                    )
                    return False
                self.last_error = None
                self._reported_missing = False
                logger.info(
                    "OneXPlayer HID opened vid=%04x pid=%04x usage_page=%04x usage=%04x interface=%s",
                    device["vendor_id"],
                    device["product_id"],
                    device["usage_page"],
                    device["usage"],
                    device.get("interface_number"),
                )
                return True
        self.last_error = "matching HID interface not found"
        if not self._reported_missing:
            pid = ",".join(f"{value:04x}" for value in self._pid) or "*"
            logger.warning(
                "OneXPlayer HID interface unavailable vid=1a2c pid=%s usage_page=ff01 usage=0001",
                pid,
            )
            self._reported_missing = True
        return False
