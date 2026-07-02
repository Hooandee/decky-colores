import asyncio
import logging
import math

logger = logging.getLogger("colores.effects")

FRAME_INTERVAL = 1.0 / 30.0

# Battery mode: fixed color bands by charge level (blue full -> red empty). All
# zones show the same band color; it is a status indicator, not a spatial effect.
# Each entry is (min_level_inclusive, rgb); scanned high to low. Keep in sync with
# the frontend BATTERY_BANDS (src/palette.ts) used for the legend and preview.
BATTERY_BANDS = (
    (81, (0, 120, 255)),
    (61, (0, 200, 60)),
    (41, (255, 200, 0)),
    (21, (255, 110, 0)),
    (0, (255, 30, 20)),
)
# Per-frame easing factor toward the target band color: gives a smooth ~1.3s
# crossfade when crossing a band boundary and makes the exact threshold jitter-free
# (implicit hysteresis), so we never need explicit threshold bookkeeping.
BATTERY_EASE = 0.12
# Below this per-channel delta the color has settled: stop the 30fps loop and idle
# on a coarse tick (still polling state) so a static indicator costs ~no CPU.
BATTERY_CONVERGE_EPS = 1.0
BATTERY_IDLE_INTERVAL = 0.5
# Calm breathing while charging (~2s period), well below the effect breathing range.
BATTERY_BREATHE_SPEED = 22


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


def breathe_factor(t, speed):
    return 0.575 + 0.425 * math.sin(2 * math.pi * _freq(speed) * t)


def frame_breathing(base, t, speed):
    factor = breathe_factor(t, speed)
    return [(clamp8(c[0] * factor), clamp8(c[1] * factor), clamp8(c[2] * factor)) for c in base]


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


def frame_spiral(palette, t, speed):
    # Software spiral for devices that CAN paint multiple zones (e.g. the Ally):
    # rotate the per-zone palette around the ring as a seamless loop, interpolating
    # between zones so the motion is smooth. `palette` is built once per run (it
    # never changes), so the hot path is just the rotation. On single-color
    # firmware devices (Legion Go) the spiral is rendered natively, not here.
    zones = len(palette)
    if zones == 0:
        return []
    shift = ((_freq(speed) * t) % 1.0) * zones
    result = []
    for i in range(zones):
        src = (i + shift) % zones
        lo = int(math.floor(src)) % zones
        hi = (lo + 1) % zones
        frac = src - math.floor(src)
        a, b = palette[lo], palette[hi]
        result.append(
            (clamp8(_lerp(a[0], b[0], frac)), clamp8(_lerp(a[1], b[1], frac)), clamp8(_lerp(a[2], b[2], frac)))
        )
    return result


def frame_gradient_sweep(stops, zones, t, speed):
    # Temporal crossfade through the whole palette for devices that cannot render
    # a spatial gradient (single-color zones, e.g. Legion rings). All zones share
    # the same color. A cosine ease maps the looping phase to a 0->1->0 sweep that
    # accelerates and decelerates smoothly at the palette ends, so the motion feels
    # graceful and the loop is seamless (no abrupt reversal).
    if zones <= 0:
        return []
    phase = (_freq(speed) * t) % 1.0
    pos = (1.0 - math.cos(2.0 * math.pi * phase)) / 2.0
    color = _sample_stops(stops, pos)
    return [color for _ in range(zones)]


def battery_band_color(level):
    for threshold, color in BATTERY_BANDS:
        if level >= threshold:
            return color
    return BATTERY_BANDS[-1][1]


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

    def start_battery(self, state_fn):
        # state_fn() returns the LIVE {level, charging, breathe} each frame, so the
        # loop reacts to charge/plug changes without a restart. Fixed signature: a
        # re-apply (e.g. a brightness change) is a no-op while it runs, which keeps
        # the easing/breathing from resetting.
        sig = ("__battery__",)
        if self.running and sig == self._sig:
            return
        self.stop()
        self._sig = sig
        loop = asyncio.get_event_loop()
        self._task = loop.create_task(self._run_battery(state_fn))

    def stop(self):
        if self._task is not None:
            self._task.cancel()
            self._task = None

    def _palette(self, params):
        if params.get("use_gradient") and params.get("stops"):
            return interpolate_gradient(params["stops"], self._zones)
        return [params.get("color", (255, 255, 255))] * self._zones

    def _compute(self, effect_id, t, speed, params, prepared=None):
        if effect_id == "breathing":
            return frame_breathing(self._palette(params), t, speed)
        if effect_id == "rainbow":
            return frame_rainbow(self._zones, t, speed)
        if effect_id == "wave":
            return frame_wave(params.get("stops", [(255, 0, 0), (0, 0, 255)]), self._zones, t, speed)
        if effect_id == "spiral":
            return frame_spiral(prepared or [], t, speed)
        if effect_id == "cycle":
            return frame_cycle(self._zones, t, speed)
        if effect_id == "gradient_sweep":
            return frame_gradient_sweep(params.get("stops", [(255, 255, 255)]), self._zones, t, speed)
        return [(0, 0, 0) for _ in range(self._zones)]

    async def _run_battery(self, state_fn):
        loop = asyncio.get_event_loop()
        start = loop.time()
        displayed = None
        while True:
            try:
                st = state_fn() or {}
                level = st.get("level", 100)
                target = battery_band_color(level)
                if displayed is None:
                    displayed = target  # snap to the band on entry, no fade from black
                breathing = bool(st.get("charging")) and bool(st.get("breathe")) and level < 100

                # Settled and static: hold the band color on a coarse tick (~no CPU).
                if not breathing and max(abs(displayed[i] - target[i]) for i in range(3)) <= BATTERY_CONVERGE_EPS:
                    displayed = target
                    self._apply_zones([target] * self._zones)
                    await asyncio.sleep(BATTERY_IDLE_INTERVAL)
                    continue

                # Crossfading and/or breathing: ease toward the band at 30fps.
                displayed = tuple(_lerp(displayed[i], target[i], BATTERY_EASE) for i in range(3))
                factor = breathe_factor(loop.time() - start, BATTERY_BREATHE_SPEED) if breathing else 1.0
                frame = tuple(clamp8(c * factor) for c in displayed)
                self._apply_zones([frame] * self._zones)
                await asyncio.sleep(FRAME_INTERVAL)
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("battery frame failed")
                await asyncio.sleep(BATTERY_IDLE_INTERVAL)

    async def _run(self, effect_id, speed, params):
        loop = asyncio.get_event_loop()
        start = loop.time()
        # Per-run constants computed once (the spiral palette never changes while
        # the effect runs, so we keep it off the ~30fps hot path).
        prepared = None
        if effect_id == "spiral":
            prepared = interpolate_gradient(
                params.get("stops", [(255, 0, 0), (0, 0, 255)]), self._zones
            )
        while True:
            try:
                t = loop.time() - start
                frame = self._compute(effect_id, t, speed, params, prepared)
                self._apply_zones(frame)
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("effect frame failed")
            await asyncio.sleep(FRAME_INTERVAL)
