import asyncio
import colorsys
from dataclasses import dataclass
import hashlib
import json
import logging
import os
from pathlib import Path
import platform
import subprocess

from run_as_user import user_env, user_cred

logger = logging.getLogger("colores.ambilight")

GAMESCOPE_NODE = "gamescope"
CAP_W = 32
CAP_H = 18
ODIN2_CAP_W = 64
ODIN2_CAP_H = 36

# Seconds between reconnect attempts when the gamescope source is missing or the
# stream drops. On a cold boot the user's PipeWire/gamescope session isn't ready when
# the (root) plugin loads, so the capture must keep retrying instead of giving up —
# otherwise ambient mode stays dark until the user manually re-selects it.
RETRY_INTERVAL = 3.0

_NATIVE_ENV_KEYS = (
    "LD_LIBRARY_PATH",
    "LD_PRELOAD",
    "GST_PLUGIN_PATH",
    "GST_PLUGIN_SYSTEM_PATH",
)
_VENDOR_ROOT = Path(__file__).resolve().parent / "vendor" / "arm64"
_VENDOR_PW_DUMP = _VENDOR_ROOT / "bin" / "pw-dump"
_VENDOR_GST_DIR = _VENDOR_ROOT / "lib64" / "gstreamer-1.0"
_VENDOR_HASHES = {
    _VENDOR_PW_DUMP: "3a2b9a13842e0338b3551b5a9dbe4a89a544f6e7df8e242d971097a0a6bdda63",
    _VENDOR_GST_DIR / "libgstpipewire.so": "b1227d19e9bcb40941d9fcfebed662e08db6234c16aa1ed1ab74c74e26d5220b",
}

_FULL_REGION = [0.0, 0.0, 1.0, 1.0]


@dataclass(frozen=True)
class CaptureBackend:
    name: str
    pw_dump: str
    gst_launch: str
    gst_plugin_path: str | None = None


def _native_bin(name):
    host_bin = Path("/run/host/usr/bin")
    if host_bin.is_dir():
        host_path = host_bin / name
        if host_path.is_file() and os.access(host_path, os.X_OK):
            return str(host_path)
        return None
    local_path = Path("/usr/bin") / name
    if local_path.is_file() and os.access(local_path, os.X_OK):
        return str(local_path)
    return None


def _native_env(runtime_dir=None, gst_plugin_path=None):
    env = user_env(runtime_dir) if runtime_dir else os.environ.copy()
    for key in _NATIVE_ENV_KEYS:
        env.pop(key, None)
    if gst_plugin_path:
        env["GST_PLUGIN_PATH"] = str(gst_plugin_path)
    return env


def _host_architecture():
    uname = _native_bin("uname")
    if uname:
        try:
            result = subprocess.run(
                [uname, "-m"],
                capture_output=True,
                text=True,
                timeout=2,
                check=False,
                env=_native_env(),
            )
            if result.returncode == 0 and result.stdout.strip():
                return result.stdout.strip().lower()
        except (OSError, subprocess.TimeoutExpired):
            pass
    return platform.machine().lower()


def _valid_vendor_files():
    for path, expected in _VENDOR_HASHES.items():
        try:
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
        except OSError:
            return False
        if digest != expected:
            logger.error("ambilight fallback checksum mismatch: %s", path)
            return False
    return os.access(_VENDOR_PW_DUMP, os.X_OK)


def _has_pipewiresrc(gst_inspect, plugin_path=None):
    try:
        result = subprocess.run(
            [gst_inspect, "pipewiresrc"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=5,
            check=False,
            env=_native_env(gst_plugin_path=plugin_path),
        )
        return result.returncode == 0
    except (OSError, subprocess.TimeoutExpired):
        return False


def resolve_capture_backend():
    """Select one coherent native backend, preferring the host over the bundle."""
    gst_launch = _native_bin("gst-launch-1.0")
    gst_inspect = _native_bin("gst-inspect-1.0")
    system_pw_dump = _native_bin("pw-dump")
    if not gst_launch or not gst_inspect:
        return None

    if system_pw_dump and _has_pipewiresrc(gst_inspect):
        return CaptureBackend("system", system_pw_dump, gst_launch)

    if (
        _host_architecture() in {"aarch64", "arm64"}
        and _valid_vendor_files()
        and _has_pipewiresrc(gst_inspect, _VENDOR_GST_DIR)
    ):
        return CaptureBackend(
            "bundled-arm64",
            str(_VENDOR_PW_DUMP),
            gst_launch,
            str(_VENDOR_GST_DIR),
        )
    return None


def capture_available():
    return resolve_capture_backend() is not None


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


def dominant_region(frame, width, height, region):
    """Pick the prevalent visible hue instead of washing mixed colors to gray."""
    x0, y0, x1, y1 = region
    cx0 = max(0, int(x0 * width))
    cx1 = min(width, max(cx0 + 1, int(x1 * width)))
    cy0 = max(0, int(y0 * height))
    cy1 = min(height, max(cy0 + 1, int(y1 * height)))
    buckets = [[] for _ in range(12)]
    pixel_count = max(1, (cx1 - cx0) * (cy1 - cy0))
    for y in range(cy0, cy1):
        base = y * width * 3
        for x in range(cx0, cx1):
            i = base + x * 3
            color = (frame[i], frame[i + 1], frame[i + 2])
            highest = max(color)
            chroma = highest - min(color)
            if highest < 24 or chroma < 32:
                continue
            hue, _, _ = colorsys.rgb_to_hsv(*(channel / 255 for channel in color))
            buckets[min(11, int(hue * 12))].append((color, chroma))

    winner = max(buckets, key=len)
    if len(winner) < max(3, (pixel_count + 19) // 20):
        return avg_region(frame, width, height, region)

    weight = sum(chroma for _, chroma in winner)
    return tuple(
        round(sum(color[channel] * chroma for color, chroma in winner) / weight)
        for channel in range(3)
    )


def boost_saturation(color, factor):
    r, g, b = color
    gray = r * 0.299 + g * 0.587 + b * 0.114
    return tuple(int(max(0, min(255, gray + (c - gray) * factor))) for c in (r, g, b))


def lerp(current, target, alpha):
    return tuple(int(c + (t - c) * alpha) for c, t in zip(current, target))


def alpha_for(smoothing):
    s = max(0, min(100, smoothing))
    return max(0.04, 1.0 - s / 100.0)


def adaptive_alpha(current, target, base_alpha):
    delta = max(abs(current[index] - target[index]) for index in range(3))
    return max(base_alpha, min(1.0, delta / 128.0))


def _gst_command(gst_launch, node, width, height):
    caps = f"video/x-raw,format=RGB,width={width},height={height}"
    return [
        gst_launch, "-q", "pipewiresrc", f"path={int(node)}",
        "!", "queue", "leaky=downstream", "max-size-buffers=2",
        "!", "videoconvert", "!", "videoscale", "!", caps, "!", "fdsink", "fd=1",
    ]


def _replace_with_latest(queue, item):
    if queue.full():
        queue.get_nowait()
    queue.put_nowait(item)


async def _read_latest_frames(reader, frame_bytes, queue):
    try:
        while True:
            frame = await reader.readexactly(frame_bytes)
            _replace_with_latest(queue, frame)
    except Exception as error:
        _replace_with_latest(queue, error)


class Ambilight:
    def __init__(self, apply_zones, zones, runtime_dir, uid=None, gid=None, layout=None, max_fps=None):
        self._apply = apply_zones
        self._zones = max(1, zones)
        self._runtime_dir = runtime_dir
        self._uid = uid
        self._gid = gid
        self._max_fps = max_fps
        self._layout = layout or [{"name": "Lights", "region": _FULL_REGION, "zones": list(range(self._zones))}]
        self._adaptive_zones = {
            zone
            for group in self._layout
            if group.get("kind") == "shared-edge"
            for zone in group["zones"]
        }
        self._capture_width = ODIN2_CAP_W if self._adaptive_zones else CAP_W
        self._capture_height = ODIN2_CAP_H if self._adaptive_zones else CAP_H
        self._task = None
        self._proc = None
        self._options = {}
        self.status = "idle"
        self._current = [(0, 0, 0)] * self._zones
        self._targets = [(0, 0, 0)] * self._zones
        self._backend = resolve_capture_backend()
        if self._backend:
            logger.info("ambilight capture backend: %s", self._backend.name)
        else:
            logger.warning("ambilight capture backend unavailable")

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
        plugin_path = self._backend.gst_plugin_path if self._backend else None
        return _native_env(self._runtime_dir, plugin_path)

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
        if self._backend is None:
            return None
        try:
            proc = await asyncio.create_subprocess_exec(
                self._backend.pw_dump,
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
                await asyncio.gather(proc.wait(), return_exceptions=True)
            return None
        except asyncio.CancelledError:
            if proc is not None:
                try:
                    proc.kill()
                except ProcessLookupError:
                    pass
                await asyncio.gather(proc.wait(), return_exceptions=True)
            raise
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
        logger.info("ambilight worker starting")
        self._task = asyncio.get_event_loop().create_task(self._run())

    def stop(self):
        self.status = "idle"
        if self._task is not None:
            logger.info("ambilight worker stop requested")
            self._task.cancel()
            self._task = None
        self._kill()

    async def stop_and_wait(self):
        task = self._task
        proc = self._proc
        if task is not None:
            logger.info("ambilight worker stopping")
        self.stop()
        pending = [task] if task is not None else []
        if proc is not None:
            pending.append(proc.wait())
        await asyncio.gather(*pending, return_exceptions=True)
        if task is not None or proc is not None:
            logger.info("ambilight worker stopped; capture process reaped")

    def _kill(self):
        if self._proc is not None:
            try:
                self._proc.kill()
            except ProcessLookupError:
                pass
            self._proc = None

    async def _run(self):
        # Outer reconnect loop: keep trying to find the gamescope source and capture it
        # until stop() cancels us. The source can be absent at boot (session not up yet)
        # or vanish (leaving Game Mode) and reappear — we recover from both automatically.
        frame_bytes = self._capture_width * self._capture_height * 3
        while True:
            if self._backend is None:
                self._backend = resolve_capture_backend()
                if self._backend is None:
                    self.status = "unavailable"
                    self._apply(self._fallback())
                    await asyncio.sleep(RETRY_INTERVAL)
                    continue
                logger.info("ambilight capture backend: %s", self._backend.name)
            node = await self._find_node()
            if node is None:
                logger.warning("gamescope PipeWire node not found; retrying")
                self.status = "no_source"
                self._apply(self._fallback())
                await asyncio.sleep(RETRY_INTERVAL)
                continue

            interval = self._capture_interval()
            command = _gst_command(
                self._backend.gst_launch,
                node,
                self._capture_width,
                self._capture_height,
            )
            proc = None
            reader_task = None
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
                frames = asyncio.Queue(maxsize=1)
                reader_task = asyncio.create_task(
                    _read_latest_frames(proc.stdout, frame_bytes, frames)
                )
                while True:
                    frame = await frames.get()
                    if isinstance(frame, Exception):
                        raise frame
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
                if reader_task is not None:
                    reader_task.cancel()
                    await asyncio.gather(reader_task, return_exceptions=True)
                if proc is not None:
                    try:
                        proc.kill()
                    except ProcessLookupError:
                        pass
                    await asyncio.gather(proc.wait(), return_exceptions=True)
                if self._proc is proc:
                    self._proc = None
            await asyncio.sleep(RETRY_INTERVAL)

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
        if self._options.get("global_color"):
            target = boost_saturation(
                avg_region(frame, self._capture_width, self._capture_height, _FULL_REGION),
                sat,
            )
            self._targets = [target] * self._zones
            return
        bottom_edge = self._options.get("sampling") == "bottom_edge"
        for group in self._layout:
            indices = group["zones"]
            region = group["region"]
            if group.get("kind") == "shared-full":
                target = boost_saturation(
                    avg_region(
                        frame,
                        self._capture_width,
                        self._capture_height,
                        region,
                    ),
                    sat,
                )
                for zone in indices:
                    if 0 <= zone < self._zones:
                        self._targets[zone] = target
                continue
            if group.get("kind") == "shared-edge":
                target = boost_saturation(
                    dominant_region(
                        frame,
                        self._capture_width,
                        self._capture_height,
                        region,
                    ),
                    sat,
                )
                for zone in indices:
                    if 0 <= zone < self._zones:
                        self._targets[zone] = target
                continue
            if bottom_edge:
                x0, y0, x1, y1 = region
                region = (x0, y1 - (y1 - y0) * 0.28, x1, y1)
            for sub, zone in zip(subdivide(region, len(indices)), indices):
                if 0 <= zone < self._zones:
                    self._targets[zone] = boost_saturation(
                        avg_region(
                            frame,
                            self._capture_width,
                            self._capture_height,
                            sub,
                        ),
                        sat,
                    )

    def _tick(self):
        base_alpha = alpha_for(self._options.get("smoothing", 75))
        self._current = [
            lerp(
                current,
                target,
                adaptive_alpha(current, target, base_alpha)
                if zone in self._adaptive_zones
                else base_alpha,
            )
            for zone, (current, target) in enumerate(zip(self._current, self._targets))
        ]
        self._apply(list(self._current))
