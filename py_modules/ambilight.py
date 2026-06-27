import asyncio
import json
import logging
import os
import subprocess

logger = logging.getLogger("colores.ambilight")

GAMESCOPE_NODE = "gamescope"
CAP_W = 32
CAP_H = 18

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


def _gst_command(node, width, height):
    caps = f"video/x-raw,format=RGB,width={width},height={height}"
    return [
        "gst-launch-1.0", "-q", "pipewiresrc", f"path={int(node)}",
        "!", "queue", "leaky=downstream", "max-size-buffers=2",
        "!", "videoconvert", "!", "videoscale", "!", caps, "!", "fdsink", "fd=1",
    ]


class Ambilight:
    def __init__(self, apply_zones, zones, runtime_dir, uid=None, gid=None, layout=None):
        self._apply = apply_zones
        self._zones = max(1, zones)
        self._runtime_dir = runtime_dir
        self._uid = uid
        self._gid = gid
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

        interval = 1.0 / max(1, int(self._options.get("fps", 10)))
        command = _gst_command(node, CAP_W, CAP_H)
        frame_bytes = CAP_W * CAP_H * 3
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
        for group in self._layout:
            indices = group["zones"]
            for sub, zone in zip(subdivide(group["region"], len(indices)), indices):
                if 0 <= zone < self._zones:
                    self._targets[zone] = boost_saturation(avg_region(frame, CAP_W, CAP_H, sub), sat)

    def _tick(self):
        alpha = alpha_for(self._options.get("smoothing", 75))
        self._current = [lerp(c, t, alpha) for c, t in zip(self._current, self._targets)]
        self._apply(list(self._current))
