"""Power-button ring LED control for Lenovo Legion handhelds.

The power-button LED is not an RGB LED on the HID/sysfs path Colores uses for the
joystick rings. It is a single Embedded Controller (EC) bit. The firmware method
`\\_SB.GZFD.WMAF` (reverse-engineered from each device's DSDT) only writes that bit, and
on Bazzite's current kernel `acpi_call` is unavailable, so we write the EC directly via
the in-tree `ec_sys` debug module.

Config is a list of EC byte/bit entries to SET to turn the LED off (cleared = on):
  Go S / Go 2  -> [{"offset": 0x10, "mask": 0x40}]            (LPBL, awake + suspend)
  Legion Go    -> [{"offset": 0x52, "mask": 0x20},            (LEDP, awake)
                   {"offset": 0x58, "mask": 0x01}]            (LEDM, suspend)

Everything degrades gracefully: with no config or no reachable EC the controller is
simply unavailable and the UI hides the toggle.
"""
import os
import subprocess

EC_IO = "/sys/kernel/debug/ec/ec0/io"


class PowerLedController:
    def __init__(self, config, ec_io=EC_IO):
        self._config = list(config or [])
        self._ec_io = ec_io

    def available(self) -> bool:
        """Has a known config AND the EC is reachable (or the module can be loaded).

        Does NOT load ec_sys here, so merely detecting the capability never taints the
        kernel; the module is loaded lazily on the first write.
        """
        if not self._config:
            return False
        return self._writable() or self._module_present()

    def get(self):
        """True if the LED is off (all configured bits set), False if on, None if unknown."""
        if not self._config:
            return None
        if not (os.path.exists(self._ec_io) and os.access(self._ec_io, os.R_OK)):
            return None
        try:
            return all(bool(self._read_byte(b["offset"]) & b["mask"]) for b in self._config)
        except (OSError, IndexError, ValueError):
            return None

    def set(self, off: bool) -> bool:
        """Turn the LED off (off=True) or on (off=False). Returns success."""
        if not self._config or not self._ensure_loaded():
            return False
        try:
            for bit in self._config:
                current = self._read_byte(bit["offset"])
                updated = (current | bit["mask"]) if off else (current & ~bit["mask"])
                if updated != current:
                    self._write_byte(bit["offset"], updated)
            return True
        except (OSError, IndexError, ValueError):
            return False

    # --- EC access -------------------------------------------------------------

    def _writable(self) -> bool:
        return os.path.exists(self._ec_io) and os.access(self._ec_io, os.W_OK)

    def _module_present(self) -> bool:
        try:
            return subprocess.run(
                ["modprobe", "-n", "ec_sys"], capture_output=True
            ).returncode == 0
        except Exception:
            return False

    def _ensure_loaded(self) -> bool:
        if self._writable():
            return True
        try:
            subprocess.run(
                ["modprobe", "ec_sys", "write_support=1"],
                check=False,
                capture_output=True,
            )
        except Exception:
            return False
        return self._writable()

    def _read_byte(self, offset: int) -> int:
        with open(self._ec_io, "rb") as handle:
            handle.seek(offset)
            return handle.read(1)[0]

    def _write_byte(self, offset: int, value: int) -> None:
        with open(self._ec_io, "r+b") as handle:
            handle.seek(offset)
            handle.write(bytes([value & 0xFF]))
            handle.flush()
