import asyncio
import logging
import math

logger = logging.getLogger("colores.effects")

FRAME_INTERVAL = 1.0 / 30.0


def clamp8(x):
    return max(0, min(255, int(round(x))))


def hsv_to_rgb(h, s, v):
    h = h % 360.0
    c = v * s
    x = c * (1 - abs((h / 60.0) % 2 - 1))
    m = v - c
    if h < 60:
        r, g, b = c, x, 0
    elif h < 120:
        r, g, b = x, c, 0
    elif h < 180:
        r, g, b = 0, c, x
    elif h < 240:
        r, g, b = 0, x, c
    elif h < 300:
        r, g, b = x, 0, c
    else:
        r, g, b = c, 0, x
    return (clamp8((r + m) * 255), clamp8((g + m) * 255), clamp8((b + m) * 255))


def _lerp(a, b, f):
    return a + (b - a) * f


def _sample_stops(stops, pos):
    if len(stops) == 1:
        return stops[0]
    pos = max(0.0, min(1.0, pos))
    scaled = pos * (len(stops) - 1)
    i = int(math.floor(scaled))
    if i >= len(stops) - 1:
        return stops[-1]
    f = scaled - i
    a = stops[i]
    b = stops[i + 1]
    return (
        clamp8(_lerp(a[0], b[0], f)),
        clamp8(_lerp(a[1], b[1], f)),
        clamp8(_lerp(a[2], b[2], f)),
    )


def interpolate_gradient(stops, zones):
    if zones <= 0:
        return []
    if len(stops) == 1:
        return [tuple(stops[0]) for _ in range(zones)]
    if zones == 1:
        return [tuple(stops[0])]
    return [_sample_stops(stops, i / (zones - 1)) for i in range(zones)]


def _freq(speed):
    return 0.1 + (max(0.0, min(100.0, speed)) / 100.0) * 1.9


def frame_breathing(color, zones, t, speed):
    phase = math.sin(2 * math.pi * _freq(speed) * t)
    factor = 0.575 + 0.425 * phase
    scaled = (clamp8(color[0] * factor), clamp8(color[1] * factor), clamp8(color[2] * factor))
    return [scaled for _ in range(zones)]


def frame_rainbow(zones, t, speed):
    hue = (60.0 * _freq(speed) * t) % 360.0
    color = hsv_to_rgb(hue, 1.0, 1.0)
    return [color for _ in range(zones)]


def frame_wave(stops, zones, t, speed):
    if zones <= 0:
        return []
    offset = (_freq(speed) * t) % 1.0
    result = []
    for i in range(zones):
        pos = ((i / zones) + offset) % 1.0
        result.append(_sample_stops(stops, pos))
    return result


def frame_cycle(zones, t, speed):
    if zones <= 0:
        return []
    base = (60.0 * _freq(speed) * t) % 360.0
    return [hsv_to_rgb((base + (360.0 * i / zones)) % 360.0, 1.0, 1.0) for i in range(zones)]


class EffectEngine:
    def __init__(self, apply_zones, zones):
        self._apply_zones = apply_zones
        self._zones = zones
        self._task = None
        self._sig = None

    @property
    def running(self):
        return self._task is not None and not self._task.done()

    def set_static(self, zone_colors):
        self.stop()
        self._apply_zones(zone_colors)

    def start_effect(self, effect_id, speed, params):
        params = params or {}
        sig = (effect_id, speed, repr(params))
        if self.running and sig == self._sig:
            return
        self.stop()
        self._sig = sig
        loop = asyncio.get_event_loop()
        self._task = loop.create_task(self._run(effect_id, speed, params))

    def stop(self):
        if self._task is not None:
            self._task.cancel()
            self._task = None

    def _compute(self, effect_id, t, speed, params):
        if effect_id == "breathing":
            return frame_breathing(params.get("color", (255, 255, 255)), self._zones, t, speed)
        if effect_id == "rainbow":
            return frame_rainbow(self._zones, t, speed)
        if effect_id == "wave":
            return frame_wave(params.get("stops", [(255, 0, 0), (0, 0, 255)]), self._zones, t, speed)
        if effect_id == "cycle":
            return frame_cycle(self._zones, t, speed)
        return [(0, 0, 0) for _ in range(self._zones)]

    async def _run(self, effect_id, speed, params):
        loop = asyncio.get_event_loop()
        start = loop.time()
        while True:
            try:
                t = loop.time() - start
                frame = self._compute(effect_id, t, speed, params)
                self._apply_zones(frame)
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("effect frame failed")
            await asyncio.sleep(FRAME_INTERVAL)
