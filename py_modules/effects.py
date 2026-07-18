import asyncio
import logging
import math
from functools import lru_cache

logger = logging.getLogger("colores.effects")

FRAME_INTERVAL = 1.0 / 30.0

# Status-indicator color bands (battery %, APU temperature C). Each entry is
# (min_value_inclusive, rgb), scanned high to low. Keep in sync with the frontend
# copies in src/palette.ts (those drive the legend/preview; the LED render is here).
BATTERY_BANDS = (
    (81, (0, 120, 255)),
    (61, (0, 200, 60)),
    (41, (255, 200, 0)),
    (21, (255, 110, 0)),
    (0, (255, 30, 20)),
)
TEMPERATURE_BANDS = (
    (90, (255, 30, 20)),
    (80, (255, 110, 0)),
    (68, (255, 200, 0)),
    (55, (0, 200, 60)),
    (0, (0, 120, 255)),
)
TEMPERATURE_CRITICAL = 90

INDICATOR_EASE = 0.12
INDICATOR_CONVERGE_EPS = 1.0
INDICATOR_IDLE_INTERVAL = 0.5
INDICATOR_BREATHE_SPEED = 22


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


@lru_cache(maxsize=None)
def _hash01(a, b):
    x = math.sin(a * 127.1 + b * 311.7) * 43758.5453
    return x - math.floor(x)


def frame_comet(base, t, speed):
    zones = len(base)
    if zones <= 0:
        return []
    span = zones - 1 if zones > 1 else 1
    phase = (_freq(speed) * t) % 2.0
    pos = phase * span if phase <= 1.0 else (2.0 - phase) * span
    tail = max(1.5, zones * 0.18)
    result = []
    for i in range(zones):
        b = max(0.0, 1.0 - abs(i - pos) / tail)
        b *= b
        c = base[i]
        result.append((clamp8(c[0] * b), clamp8(c[1] * b), clamp8(c[2] * b)))
    return result


def frame_sparkle(base, t, speed):
    zones = len(base)
    if zones <= 0:
        return []
    f = _freq(speed)
    floor = 0.10
    result = []
    for i in range(zones):
        ph = (f * 0.8 * t + _hash01(i, 1.0)) % 1.0
        tw = max(0.0, 1.0 - abs(ph - 0.5) * 2.0) ** 3
        factor = floor + (1.0 - floor) * tw
        c = base[i]
        result.append((clamp8(c[0] * factor), clamp8(c[1] * factor), clamp8(c[2] * factor)))
    return result


def frame_ripple(base, t, speed):
    zones = len(base)
    if zones <= 0:
        return []
    f = _freq(speed)
    waves = 2.0
    result = []
    for i in range(zones):
        phase = 2 * math.pi * (f * t - (i / zones) * waves)
        b = 0.35 + 0.65 * (0.5 + 0.5 * math.sin(phase))
        c = base[i]
        result.append((clamp8(c[0] * b), clamp8(c[1] * b), clamp8(c[2] * b)))
    return result


def frame_aurora(zones, t, speed):
    if zones <= 0:
        return []
    f = _freq(speed)
    result = []
    for i in range(zones):
        h = 150.0 + 80.0 * math.sin(2 * math.pi * (f * 0.25 * t + i / zones)) \
            + 40.0 * math.sin(2 * math.pi * (f * 0.15 * t + i * 2.0 / zones))
        v = 0.6 + 0.4 * (0.5 + 0.5 * math.sin(2 * math.pi * (f * 0.3 * t + i / zones * 1.5)))
        result.append(hsv_to_rgb(h % 360.0, 0.85, v))
    return result


METER_RAMP = [(0, 230, 90), (255, 200, 0), (255, 40, 0)]


def frame_meter(value01, zones):
    if zones <= 0:
        return []
    lit = max(0.0, min(1.0, value01)) * zones
    result = []
    for i in range(zones):
        fill = max(0.0, min(1.0, lit - i))
        color = _sample_stops(METER_RAMP, i / (zones - 1) if zones > 1 else 0.0)
        result.append((clamp8(color[0] * fill), clamp8(color[1] * fill), clamp8(color[2] * fill)))
    return result


CLOCK_KEYS = [
    (0.0, (12, 22, 64)),
    (6.0, (255, 120, 40)),
    (9.0, (170, 205, 255)),
    (14.0, (255, 248, 230)),
    (18.0, (255, 105, 40)),
    (21.0, (40, 28, 92)),
    (24.0, (12, 22, 64)),
]


def clock_color(hour):
    h = hour % 24.0
    for (h0, c0), (h1, c1) in zip(CLOCK_KEYS, CLOCK_KEYS[1:]):
        if h0 <= h <= h1:
            f = (h - h0) / (h1 - h0) if h1 > h0 else 0.0
            return tuple(clamp8(c0[j] + (c1[j] - c0[j]) * f) for j in range(3))
    return CLOCK_KEYS[0][1]


def frame_vu(level, zones):
    if zones <= 0:
        return []
    level = max(0.0, min(1.0, level))
    center = (zones - 1) / 2.0
    reach = level * (zones / 2.0)
    result = []
    for i in range(zones):
        d = abs(i - center)
        fill = max(0.0, min(1.0, reach - d))
        ramp = _sample_stops(METER_RAMP, d / center if center else 0.0)
        result.append((clamp8(ramp[0] * fill), clamp8(ramp[1] * fill), clamp8(ramp[2] * fill)))
    return result


def battery_band_color(level):
    for threshold, color in BATTERY_BANDS:
        if level >= threshold:
            return color
    return BATTERY_BANDS[-1][1]


def temperature_band_color(temp):
    for threshold, color in TEMPERATURE_BANDS:
        if temp >= threshold:
            return color
    return TEMPERATURE_BANDS[-1][1]


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
        self._start_indicator(
            "__battery__",
            state_fn,
            lambda st: battery_band_color(st.get("level", 100)),
            lambda st: bool(st.get("charging")) and bool(st.get("breathe")) and st.get("level", 100) < 100,
            "battery",
        )

    def start_temperature(self, state_fn):
        self._start_indicator(
            "__temperature__",
            state_fn,
            lambda st: None if st.get("temp") is None else temperature_band_color(st["temp"]),
            lambda st: bool(st.get("breathe")) and st.get("temp") is not None and st["temp"] >= TEMPERATURE_CRITICAL,
            "temperature",
        )

    def start_clock(self, state_fn):
        self._start_indicator(
            "__clock__",
            state_fn,
            lambda st: clock_color(st.get("hour", 12)),
            lambda st: False,
            "clock",
        )

    def start_performance(self, state_fn):
        sig = ("__performance__",)
        if self.running and sig == self._sig:
            return
        self.stop()
        self._sig = sig
        loop = asyncio.get_event_loop()
        self._task = loop.create_task(self._run_performance(state_fn))

    def _start_indicator(self, sig_key, state_fn, target_fn, breathe_fn, label):
        # state_fn() returns the LIVE state each frame, so the loop reacts without a
        # restart. Fixed per-mode signature: a re-apply (e.g. a brightness change) is
        # a no-op while it runs, keeping the easing/breathing from resetting.
        sig = (sig_key,)
        if self.running and sig == self._sig:
            return
        self.stop()
        self._sig = sig
        loop = asyncio.get_event_loop()
        self._task = loop.create_task(self._run_indicator(state_fn, target_fn, breathe_fn, label))

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
        if effect_id == "comet":
            return frame_comet(prepared if prepared is not None else self._palette(params), t, speed)
        if effect_id == "sparkle":
            return frame_sparkle(prepared if prepared is not None else self._palette(params), t, speed)
        if effect_id == "ripple":
            return frame_ripple(prepared if prepared is not None else self._palette(params), t, speed)
        if effect_id == "aurora":
            return frame_aurora(self._zones, t, speed)
        return [(0, 0, 0) for _ in range(self._zones)]

    async def _run_indicator(self, state_fn, target_fn, breathe_fn, label):
        # Shared render loop for the status indicators (battery %, APU temperature):
        # snap to the band on entry, ease toward it at 30fps while crossfading or
        # breathing, and hold on a coarse tick once settled so a static color costs
        # ~no CPU. target_fn(st) -> rgb, or None to hold the last frame (unreadable).
        loop = asyncio.get_event_loop()
        start = loop.time()
        displayed = None
        while True:
            try:
                st = state_fn() or {}
                target = target_fn(st)
                if target is None:
                    await asyncio.sleep(INDICATOR_IDLE_INTERVAL)
                    continue
                if displayed is None:
                    displayed = target
                breathing = breathe_fn(st)

                if not breathing and max(abs(displayed[i] - target[i]) for i in range(3)) <= INDICATOR_CONVERGE_EPS:
                    displayed = target
                    self._apply_zones([target] * self._zones)
                    await asyncio.sleep(INDICATOR_IDLE_INTERVAL)
                    continue

                displayed = tuple(_lerp(displayed[i], target[i], INDICATOR_EASE) for i in range(3))
                factor = breathe_factor(loop.time() - start, INDICATOR_BREATHE_SPEED) if breathing else 1.0
                frame = tuple(clamp8(c * factor) for c in displayed)
                self._apply_zones([frame] * self._zones)
                await asyncio.sleep(FRAME_INTERVAL)
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("%s frame failed", label)
                await asyncio.sleep(INDICATOR_IDLE_INTERVAL)

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
        elif effect_id in ("comet", "sparkle", "ripple"):
            prepared = self._palette(params)
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

    async def _run_performance(self, state_fn):
        displayed = 0.0
        while True:
            try:
                value = state_fn().get("value")
                target = 0.0 if value is None else max(0.0, min(1.0, value / 100.0))
                displayed += (target - displayed) * 0.25
                self._apply_zones(frame_meter(displayed, self._zones))
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("performance frame failed")
            await asyncio.sleep(FRAME_INTERVAL)
