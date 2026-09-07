import asyncio
import json
import logging

from run_as_user import user_env, user_cred

logger = logging.getLogger("colores.ambilight")

GAMESCOPE_NODE = "gamescope"
CAP_W = 32
CAP_H = 18

# Seconds between reconnect attempts when the gamescope source is missing or the
# stream drops. Fast retry quickly checks on cold boot / game startup, then backs off.
RETRY_INTERVAL = 2.0
FAST_RETRY_INTERVAL = 0.25
MAX_FAST_RETRIES = 12

_FULL_REGION = [0.0, 0.0, 1.0, 1.0]


def subdivide(region, count):
    x0, y0, x1, y1 = region
    if count <= 1:
        return [tuple(region)]
    width = (x1 - x0) / count
    return [(x0 + i * width, y0, x0 + (i + 1) * width, y1) for i in range(count)]


def avg_region(frame, width, height, region):
    x0, y0, x1, y1 = region
    cx0 = max(0, int(x0 * width))
    cx1 = min(width, max(cx0 + 1, int(x1 * width)))
    cy0 = max(0, int(y0 * height))
    cy1 = min(height, max(cy0 + 1, int(y1 * height)))
    r_sum = g_sum = b_sum = total_weight = 0
    fallback_r = fallback_g = fallback_b = fallback_n = 0
    for y in range(cy0, cy1):
        base = y * width * 3
        for x in range(cx0, cx1):
            i = base + x * 3
            r, g, b = frame[i], frame[i + 1], frame[i + 2]
            fallback_r += r
            fallback_g += g
            fallback_b += b
            fallback_n += 1
            # Filter out near-black / letterbox bar pixels unless scene is entirely dark
            if r + g + b < 12:
                continue
            # Weight saturated colors quadratically to prevent vibrant game lights from diluting
            sat = max(r, g, b) - min(r, g, b)
            weight = 1 + (sat * sat) // 256
            r_sum += r * weight
            g_sum += g * weight
            b_sum += b * weight
            total_weight += weight
    if total_weight > 0:
        return (r_sum // total_weight, g_sum // total_weight, b_sum // total_weight)
    if fallback_n > 0:
        return (fallback_r // fallback_n, fallback_g // fallback_n, fallback_b // fallback_n)
    return (0, 0, 0)


def dominant_colors_region(frame, width, height, region, count=1):
    count = max(1, count)
    x0, y0, x1, y1 = region
    cx0 = max(0, int(x0 * width))
    cx1 = min(width, max(cx0 + 1, int(x1 * width)))
    cy0 = max(0, int(y0 * height))
    cy1 = min(height, max(cy0 + 1, int(y1 * height)))

    all_pixels = []
    filtered_pixels = []

    for y in range(cy0, cy1):
        base = y * width * 3
        for x in range(cx0, cx1):
            i = base + x * 3
            r, g, b = frame[i], frame[i + 1], frame[i + 2]

            sat = max(r, g, b) - min(r, g, b)
            # Quadratic saturation boost to prevent grey muddle and prioritize vivid gaming elements
            weight = 1 + (sat * sat) // 256

            pixel = (r, g, b, weight)
            all_pixels.append(pixel)

            # Filter out extreme darks (e.g. letterbox bars) unless scene is entirely dark
            if r + g + b < 12:
                continue
            # Filter out extreme washed-out whites unless dominant
            if r > 245 and g > 245 and b > 245 and sat < 15:
                continue

            filtered_pixels.append(pixel)

    candidates = filtered_pixels if filtered_pixels else all_pixels
    if not candidates:
        return [(0, 0, 0)] * count

    # Median Cut quantization to find up to `count` distinct dominant color clusters
    boxes = [candidates]
    while len(boxes) < count:
        best_box_idx = -1
        best_span = -1
        best_channel = 0

        for idx, box in enumerate(boxes):
            if len(box) <= 1:
                continue
            min_r = min(p[0] for p in box)
            max_r = max(p[0] for p in box)
            min_g = min(p[1] for p in box)
            max_g = max(p[1] for p in box)
            min_b = min(p[2] for p in box)
            max_b = max(p[2] for p in box)

            spans = (max_r - min_r, max_g - min_g, max_b - min_b)
            max_s = max(spans)
            if max_s > best_span:
                best_span = max_s
                best_box_idx = idx
                best_channel = spans.index(max_s)

        if best_box_idx == -1 or best_span <= 0:
            break

        box_to_split = boxes[best_box_idx]
        box_to_split.sort(key=lambda p: p[best_channel])
        target_mid = len(box_to_split) // 2

        # Find split index at a value transition closest to target_mid
        best_split = target_mid
        min_dist = float("inf")
        for i in range(1, len(box_to_split)):
            if box_to_split[i - 1][best_channel] != box_to_split[i][best_channel]:
                dist = abs(i - target_mid)
                if dist < min_dist:
                    min_dist = dist
                    best_split = i

        boxes[best_box_idx] = box_to_split[:best_split]
        boxes.append(box_to_split[best_split:])

    # Compute weighted average for each box
    extracted = []
    for box in boxes:
        if not box:
            continue
        total_w = sum(p[3] for p in box)
        if total_w > 0:
            avg_r = sum(p[0] * p[3] for p in box) // total_w
            avg_g = sum(p[1] * p[3] for p in box) // total_w
            avg_b = sum(p[2] * p[3] for p in box) // total_w
            extracted.append(((avg_r, avg_g, avg_b), total_w))
        else:
            avg_r = sum(p[0] for p in box) // len(box)
            avg_g = sum(p[1] for p in box) // len(box)
            avg_b = sum(p[2] for p in box) // len(box)
            extracted.append(((avg_r, avg_g, avg_b), 1))

    # Sort boxes by total weight so primary dominant color comes first
    extracted.sort(key=lambda item: item[1], reverse=True)
    colors = [color for color, _ in extracted]

    if not colors:
        return [(0, 0, 0)] * count

    # If fewer clusters found than requested `count`, pad by cycling the dominant colors
    if len(colors) < count:
        base_colors = list(colors)
        while len(colors) < count:
            colors.append(base_colors[len(colors) % len(base_colors)])

    return colors[:count]


def boost_saturation(color, factor):
    r, g, b = color
    gray = r * 0.299 + g * 0.587 + b * 0.114
    return tuple(int(max(0, min(255, gray + (c - gray) * factor))) for c in (r, g, b))


def lerp(current, target, alpha):
    return tuple(int(c + (t - c) * alpha) for c, t in zip(current, target))


def alpha_for(smoothing):
    s = max(0, min(100, smoothing))
    return max(0.04, 1.0 - s / 100.0)


def adaptive_alpha(base_alpha, current, target):
    diff = sum(abs(t - c) for c, t in zip(current, target)) / 3.0
    if diff > 15:
        return min(1.0, base_alpha * (1.0 + (diff - 15) / 60.0))
    return base_alpha


def _gst_command(node, width, height, fps=None):
    if fps:
        caps = f"video/x-raw,format=RGB,width={width},height={height},framerate={int(fps)}/1"
        return [
            "gst-launch-1.0", "-q", "pipewiresrc", f"path={int(node)}",
            "!", "queue", "leaky=downstream", "max-size-buffers=2",
            "!", "videoconvert", "!", "videorate", "!", "videoscale", "!", caps,
            "!", "fdsink", "fd=1",
        ]
    caps = f"video/x-raw,format=RGB,width={width},height={height}"
    return [
        "gst-launch-1.0", "-q", "pipewiresrc", f"path={int(node)}",
        "!", "queue", "leaky=downstream", "max-size-buffers=2",
        "!", "videoconvert", "!", "videoscale", "!", caps, "!", "fdsink", "fd=1",
    ]


class Ambilight:
    def __init__(self, apply_zones, zones, runtime_dir, uid=None, gid=None, layout=None, max_fps=None):
        self._apply = apply_zones
        self._zones = max(1, zones)
        self._runtime_dir = runtime_dir
        self._uid = uid
        self._gid = gid
        self._max_fps = max_fps
        self._layout = layout or [{"name": "Lights", "region": _FULL_REGION, "zones": list(range(self._zones))}]
        self._task = None
        self._proc = None
        self._options = {}
        self.status = "idle"
        self._current = [(0, 0, 0)] * self._zones
        self._targets = [(0, 0, 0)] * self._zones

    @property
    def running(self):
        return self._task is not None and not self._task.done()

    def _fallback(self):
        # Shown when there's no game source to sample (e.g. the Steam home screen, or a
        # cold boot before the session is up) so the LEDs hold the user's last solid color
        # instead of going dark. Routes through _apply, so brightness/power still apply.
        color = self._options.get("fallback") or (0, 0, 0)
        return [tuple(color)] * self._zones

    def _env(self):
        return user_env(self._runtime_dir)

    def _cred(self):
        return user_cred(self._uid, self._gid)

    def _capture_interval(self):
        fps = max(1, int(self._options.get("fps", 10)))
        if self._max_fps is not None:
            fps = min(fps, max(1, int(self._max_fps)))
        return 1.0 / fps

    async def _find_node(self):
        # Async so the retry loop never blocks the event loop while waiting on pw-dump
        # (it runs every RETRY_INTERVAL while the source is missing).
        proc = None
        try:
            proc = await asyncio.create_subprocess_exec(
                "pw-dump",
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.DEVNULL,
                env=self._env(),
                **self._cred(),
            )
            out, _ = await asyncio.wait_for(proc.communicate(), timeout=5)
            data = json.loads(out)
        except (OSError, ValueError, asyncio.TimeoutError) as error:
            logger.warning("pw-dump failed: %s", error)
            if proc is not None:
                try:
                    proc.kill()
                except ProcessLookupError:
                    pass
            return None
        for obj in data:
            props = (obj.get("info") or {}).get("props") or {}
            if props.get("node.name") == GAMESCOPE_NODE and "Video" in str(
                props.get("media.class", "")
            ):
                return obj.get("id")
        return None

    def start(self, options):
        self._options = options or {}
        if self.running:
            return
        self.stop()
        self._task = asyncio.get_event_loop().create_task(self._run())

    def stop(self):
        self.status = "idle"
        if self._task is not None:
            self._task.cancel()
            self._task = None
        self._kill()

    def _kill(self):
        if self._proc is not None:
            try:
                self._proc.kill()
            except ProcessLookupError:
                pass
            self._proc = None

    async def _run(self):
        # Outer reconnect loop: keep trying to find the gamescope source and capture it
        # until stop() cancels us. Fast polling checks frequently when a game is booting,
        # then backs off if idle.
        frame_bytes = CAP_W * CAP_H * 3
        consecutive_misses = 0
        while True:
            node = await self._find_node()
            if node is None:
                consecutive_misses += 1
                retry_interval = FAST_RETRY_INTERVAL if consecutive_misses <= MAX_FAST_RETRIES else RETRY_INTERVAL
                logger.warning(
                    "gamescope PipeWire node not found (attempt %d); retrying in %.2fs",
                    consecutive_misses,
                    retry_interval,
                )
                self.status = "no_source"
                self._apply(self._fallback())
                await asyncio.sleep(retry_interval)
                continue

            consecutive_misses = 0
            interval = self._capture_interval()
            fps = max(1, int(1.0 / interval))
            command = _gst_command(node, CAP_W, CAP_H, fps=fps)
            proc = None
            logger.info("ambilight start: node=%s fps=%.0f", node, 1.0 / interval)
            try:
                proc = await asyncio.create_subprocess_exec(
                    *command,
                    stdout=asyncio.subprocess.PIPE,
                    stderr=asyncio.subprocess.PIPE,
                    env=self._env(),
                    **self._cred(),
                )
                self._proc = proc
                self.status = "running"
                while True:
                    frame = await proc.stdout.readexactly(frame_bytes)
                    self._update_targets(frame)
                    self._tick()
                    await asyncio.sleep(interval)
            except asyncio.IncompleteReadError:
                self.status = "no_source"
                await self._log_exit(proc)
                self._apply(self._fallback())
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("ambilight loop failed")
            finally:
                if proc is not None:
                    try:
                        proc.kill()
                    except ProcessLookupError:
                        pass
                if self._proc is proc:
                    self._proc = None
            consecutive_misses = 0
            await asyncio.sleep(FAST_RETRY_INTERVAL)

    async def _log_exit(self, proc):
        if proc is None:
            return
        err = b""
        try:
            err = await proc.stderr.read()
        except (OSError, ValueError):
            pass
        logger.warning(
            "ambilight stream ended (rc=%s): %s",
            proc.returncode,
            err.decode(errors="replace")[:300],
        )

    def _update_targets(self, frame):
        sat = float(self._options.get("saturation", 1.4))
        algo = self._options.get("algorithm", "dominant")
        if self._options.get("global_color"):
            target = boost_saturation(avg_region(frame, CAP_W, CAP_H, _FULL_REGION), sat)
            self._targets = [target] * self._zones
            return
        bottom_edge = self._options.get("sampling") == "bottom_edge"
        for group in self._layout:
            indices = group["zones"]
            region = group["region"]
            if bottom_edge:
                x0, y0, x1, y1 = region
                region = (x0, y1 - (y1 - y0) * 0.28, x1, y1)
            if algo == "average":
                for sub, zone in zip(subdivide(region, len(indices)), indices):
                    if 0 <= zone < self._zones:
                        self._targets[zone] = boost_saturation(avg_region(frame, CAP_W, CAP_H, sub), sat)
            elif group.get("kind") == "bar":
                for sub, zone in zip(subdivide(region, len(indices)), indices):
                    if 0 <= zone < self._zones:
                        colors = dominant_colors_region(frame, CAP_W, CAP_H, sub, count=1)
                        self._targets[zone] = boost_saturation(colors[0], sat)
            else:
                colors = dominant_colors_region(frame, CAP_W, CAP_H, region, count=len(indices))
                for zone, color in zip(indices, colors):
                    if 0 <= zone < self._zones:
                        self._targets[zone] = boost_saturation(color, sat)

    def _tick(self):
        base_alpha = alpha_for(self._options.get("smoothing", 75))
        self._current = [
            lerp(c, t, adaptive_alpha(base_alpha, c, t))
            for c, t in zip(self._current, self._targets)
        ]
        self._apply(list(self._current))
