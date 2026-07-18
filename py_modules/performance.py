import os

DRM_ROOT = "/sys/class/drm"
STAT_PATH = "/proc/stat"


def _read(path):
    try:
        with open(path) as handle:
            return handle.read()
    except OSError:
        return None


def gpu_busy_percent(drm_root=DRM_ROOT):
    try:
        cards = sorted(n for n in os.listdir(drm_root) if n.startswith("card") and n[4:].isdigit())
    except OSError:
        return None
    for card in cards:
        raw = _read(os.path.join(drm_root, card, "device", "gpu_busy_percent"))
        if raw and raw.strip().isdigit():
            return max(0, min(100, int(raw.strip())))
    return None


class CpuSampler:
    def __init__(self, stat_path=STAT_PATH):
        self._stat_path = stat_path
        self._last = None

    def percent(self):
        raw = _read(self._stat_path)
        if not raw:
            return None
        parts = raw.split("\n", 1)[0].split()
        if len(parts) < 6 or parts[0] != "cpu":
            return None
        vals = [int(x) for x in parts[1:] if x.lstrip("-").isdigit()]
        if len(vals) < 5:
            return None
        idle = vals[3] + vals[4]
        total = sum(vals)
        previous = self._last
        self._last = (idle, total)
        if previous is None:
            return None
        idle_delta = idle - previous[0]
        total_delta = total - previous[1]
        if total_delta <= 0:
            return None
        return max(0, min(100, round(100 * (total_delta - idle_delta) / total_delta)))


def performance_available(drm_root=DRM_ROOT, stat_path=STAT_PATH):
    return gpu_busy_percent(drm_root) is not None or _read(stat_path) is not None
