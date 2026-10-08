"""Sample read/write sectors on physical, nonrotating Linux block devices."""

from pathlib import Path


class SsdActivity:
    def __init__(self, sys_block="/sys/block", diskstats="/proc/diskstats"):
        self.sys_block = Path(sys_block)
        self.diskstats = Path(diskstats)
        self.previous = None

    def sample(self):
        """Return (read sectors, written sectors) since the previous sample."""
        names = set()
        for device in self.sys_block.iterdir():
            if device.name.startswith(("loop", "ram", "zram", "dm-", "md")):
                continue
            # Virtual devices can count the same physical IO twice.
            if "/virtual/" in str(device.resolve()):
                continue
            try:
                if (device / "queue/rotational").read_text().strip() == "0":
                    names.add(device.name)
            except OSError:
                continue
        current = {}
        for line in self.diskstats.read_text().splitlines():
            fields = line.split()
            if len(fields) >= 10 and fields[2] in names:
                current[fields[2]] = (int(fields[5]), int(fields[9]))
        previous, self.previous = self.previous, current
        if previous is None:
            return 0, 0
        return tuple(
            sum(max(0, values[i] - previous[name][i]) for name, values in current.items()
                if name in previous)
            for i in (0, 1)
        )


def active(read_sectors, write_sectors, direction, sensitivity):
    """A higher sensitivity needs fewer 512-byte sectors per sample."""
    threshold = max(1, round(256 ** ((100 - sensitivity) / 100)))
    amount = (read_sectors if direction != "write" else 0) + (
        write_sectors if direction != "read" else 0
    )
    return amount >= threshold


class ActivityPulse:
    """Turn qualifying samples into short flashes with a bounded write rate."""

    def __init__(self, on_seconds=0.10, gap_seconds=0.05):
        self.on_seconds = on_seconds
        self.gap_seconds = gap_seconds
        self.lit = False
        self.until = 0.0
        self.next_on = 0.0
        self.pending = False

    def update(self, activity: bool, now: float) -> bool:
        # Remember one burst during a flash or dark gap. Sustained IO makes
        # discrete flashes instead of leaving the LED permanently lit.
        self.pending |= activity
        if self.lit and now >= self.until:
            self.lit = False
            self.next_on = now + self.gap_seconds
        elif not self.lit and self.pending and now >= self.next_on:
            self.pending = False
            self.lit = True
            self.until = now + self.on_seconds
        return self.lit
