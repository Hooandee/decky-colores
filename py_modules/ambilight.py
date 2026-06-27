import asyncio
import json
import logging
import os
import subprocess

logger = logging.getLogger("colores.ambilight")

GAMESCOPE_NODE = "gamescope"
CAP_W = 24
CAP_H = 14

LEFT_REGION = (0.0, 0.0, 0.30, 0.35)
RIGHT_REGION = (0.70, 0.33, 1.0, 0.67)


def avg_region(frame, width, height, region):
    x0, y0, x1, y1 = region
    cx0 = max(0, int(x0 * width))
    cx1 = min(width, max(cx0 + 1, int(x1 * width)))
    cy0 = max(0, int(y0 * height))
    cy1 = min(height, max(cy0 + 1, int(y1 * height)))
    r = g = b = n = 0
    for y in range(cy0, cy1):
        base = y * width * 3
        for x in range(cx0, cx1):
            i = base + x * 3
            r += frame[i]
            g += frame[i + 1]
            b += frame[i + 2]
            n += 1
    if n == 0:
        return (0, 0, 0)
    return (r // n, g // n, b // n)


def boost_saturation(color, factor):
    r, g, b = color
    gray = r * 0.299 + g * 0.587 + b * 0.114
    return tuple(int(max(0, min(255, gray + (c - gray) * factor))) for c in (r, g, b))


def lerp(current, target, alpha):
    return tuple(int(c + (t - c) * alpha) for c, t in zip(current, target))


def alpha_for(smoothing):
    s = max(0, min(100, smoothing))
    return max(0.04, 1.0 - s / 100.0)


def zone_colors(left, right, zones):
    half = zones // 2 or 1
    return [left] * half + [right] * (zones - half)


def _gst_command(node, width, height):
    caps = f"video/x-raw,format=RGB,width={width},height={height}"
    return [
        "gst-launch-1.0", "-q", "pipewiresrc", f"path={int(node)}",
        "!", "videoconvert", "!", "videoscale",
        "!", caps, "!", "fdsink", "fd=1",
    ]


class Ambilight:
    def __init__(self, apply_zones, zones, runtime_dir, uid=None, gid=None):
        self._apply = apply_zones
        self._zones = max(1, zones)
        self._runtime_dir = runtime_dir
        self._uid = uid
        self._gid = gid
        self._task = None
        self._proc = None
        self._options = {}
        self.status = "idle"
        self._left = (0, 0, 0)
        self._right = (0, 0, 0)
        self._target_left = (0, 0, 0)
        self._target_right = (0, 0, 0)

    @property
    def running(self):
        return self._task is not None and not self._task.done()

    def _env(self):
        env = dict(os.environ)
        if self._runtime_dir:
            env["XDG_RUNTIME_DIR"] = self._runtime_dir
        return env

    def _cred(self):
        if self._uid is None:
            return {}
        return {"user": self._uid, "group": self._gid}

    def _find_node(self):
        try:
            result = subprocess.run(
                ["pw-dump"], capture_output=True, text=True, env=self._env(), timeout=5,
                **self._cred(),
            )
            data = json.loads(result.stdout)
        except (OSError, ValueError, subprocess.SubprocessError) as error:
            logger.warning("pw-dump failed: %s", error)
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
        node = self._find_node()
        if node is None:
            logger.warning("gamescope PipeWire node not found; ambilight idle")
            self.status = "no_source"
            self._apply([(0, 0, 0)] * self._zones)
            return

        command = _gst_command(node, CAP_W, CAP_H)
        frame_bytes = CAP_W * CAP_H * 3
        interval = 1.0 / max(1, int(self._options.get("fps", 15)))
        loop = asyncio.get_event_loop()
        last = 0.0
        proc = None
        logger.info("ambilight start: node=%s interval=%.3fs", node, interval)
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
                now = loop.time()
                if now - last >= interval:
                    self._update_targets(frame)
                    self._tick()
                    last = now
        except asyncio.IncompleteReadError:
            self.status = "no_source"
            await self._log_exit(proc)
            self._apply([(0, 0, 0)] * self._zones)
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
        self._target_left = boost_saturation(avg_region(frame, CAP_W, CAP_H, LEFT_REGION), sat)
        self._target_right = boost_saturation(avg_region(frame, CAP_W, CAP_H, RIGHT_REGION), sat)

    def _tick(self):
        alpha = alpha_for(self._options.get("smoothing", 75))
        self._left = lerp(self._left, self._target_left, alpha)
        self._right = lerp(self._right, self._target_right, alpha)
        self._apply(zone_colors(self._left, self._right, self._zones))
