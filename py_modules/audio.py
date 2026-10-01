import array
import asyncio
import logging
import math
import os
from pathlib import Path

from effects import frame_vu
from run_as_user import user_env, user_cred

logger = logging.getLogger("colores.audio")

_spawn = asyncio.create_subprocess_exec

RATE = 16000
CHUNK = 1024  # samples per frame (~64ms at 16kHz)
FULL_SCALE = 8000.0  # RMS mapped to level 1.0
DYNAMIC_RANGE_DB = 40.0
RETRY_INTERVAL = 3.0
MONITOR_POLL_INTERVAL = 1.0
CAPTURE_LATENCY_MS = round(CHUNK * 1000 / RATE)


def _native_bin(name):
    host_bin = Path("/run/host/usr/bin")
    if host_bin.is_dir():
        path = host_bin / name
        return str(path) if path.is_file() and os.access(path, os.X_OK) else None
    path = Path("/usr/bin") / name
    return str(path) if path.is_file() and os.access(path, os.X_OK) else None


def _native_env(runtime_dir):
    env = user_env(runtime_dir)
    for key in ("LD_LIBRARY_PATH", "LD_PRELOAD"):
        env.pop(key, None)
    return env


def _capture_command(parec, device):
    return [
        parec,
        "--format=s16le",
        f"--rate={RATE}",
        "--channels=1",
        f"--device={device}",
        f"--latency-msec={CAPTURE_LATENCY_MS}",
        f"--process-time-msec={CAPTURE_LATENCY_MS}",
        "--raw",
    ]


def _level_from_pcm(data):
    samples = array.array("h")
    samples.frombytes(data[: len(data) // 2 * 2])
    if not samples:
        return 0.0
    rms = math.sqrt(sum(s * s for s in samples) / len(samples))
    if rms <= 0:
        return 0.0
    level = 1.0 + 20.0 * math.log10(rms / FULL_SCALE) / DYNAMIC_RANGE_DB
    return max(0.0, min(1.0, level))


class AudioReactive:
    def __init__(self, apply_zones, zones, runtime_dir, uid=None, gid=None):
        self._apply = apply_zones
        self._zones = max(1, zones)
        self._runtime_dir = runtime_dir
        self._uid = uid
        self._gid = gid
        self._task = None
        self._proc = None
        self._level = 0.0
        self.status = "idle"
        self._parec = _native_bin("parec")
        self._pactl = _native_bin("pactl")

    @property
    def running(self):
        return self._task is not None and not self._task.done()

    @property
    def level(self):
        return self._level

    def _env(self):
        return _native_env(self._runtime_dir)

    def _cred(self):
        return user_cred(self._uid, self._gid)

    def start(self, options=None):
        if self.running:
            return
        self.stop()
        logger.info("audio worker starting")
        self._task = asyncio.get_event_loop().create_task(self._run())

    def stop(self):
        self.status = "idle"
        if self._task is not None:
            logger.info("audio worker stop requested")
            self._task.cancel()
            self._task = None
        self._kill()
        self._level = 0.0

    async def stop_and_wait(self):
        task = self._task
        proc = self._proc
        if task is not None:
            logger.info("audio worker stopping")
        self.stop()
        pending = [task] if task is not None else []
        if proc is not None:
            pending.append(proc.wait())
        if pending:
            await asyncio.gather(*pending, return_exceptions=True)
        if task is not None or proc is not None:
            logger.info("audio worker stopped; capture process reaped")

    def _kill(self):
        if self._proc is not None:
            try:
                self._proc.kill()
            except ProcessLookupError:
                pass
            self._proc = None

    def _ease(self, target):
        alpha = 0.6 if target > self._level else 0.2
        self._level += (target - self._level) * alpha
        return self._level

    async def _default_monitor(self):
        proc = None
        if self._pactl is None:
            return None
        try:
            proc = await _spawn(
                self._pactl,
                "get-default-sink",
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                env=self._env(),
                **self._cred(),
            )
            out, _ = await asyncio.wait_for(proc.communicate(), timeout=2)
            sink = out.decode(errors="replace").strip()
            if proc.returncode == 0 and sink:
                return f"{sink}.monitor"
        except (OSError, asyncio.TimeoutError):
            if proc is not None:
                try:
                    proc.kill()
                except ProcessLookupError:
                    pass
                await asyncio.gather(proc.wait(), return_exceptions=True)
        except asyncio.CancelledError:
            if proc is not None:
                try:
                    proc.kill()
                except ProcessLookupError:
                    pass
                await asyncio.gather(proc.wait(), return_exceptions=True)
            raise
        return None

    async def _run(self):
        while True:
            if self._parec is None:
                self.status = "unavailable"
                await asyncio.sleep(RETRY_INTERVAL)
                continue
            proc = None
            route_changed = False
            device = await self._default_monitor() or "@DEFAULT_MONITOR@"
            try:
                proc = await _spawn(
                    *_capture_command(self._parec, device),
                    stdout=asyncio.subprocess.PIPE,
                    stderr=asyncio.subprocess.PIPE,
                    env=self._env(),
                    **self._cred(),
                )
                self._proc = proc
                self.status = "running"
                loop = asyncio.get_running_loop()
                next_monitor_check = loop.time() + MONITOR_POLL_INTERVAL
                while True:
                    data = await proc.stdout.readexactly(CHUNK * 2)
                    self._apply(frame_vu(self._ease(_level_from_pcm(data)), self._zones))
                    if loop.time() >= next_monitor_check:
                        current = await self._default_monitor()
                        next_monitor_check = loop.time() + MONITOR_POLL_INTERVAL
                        if current and current != device:
                            logger.info("audio output changed: %s -> %s", device, current)
                            self.status = "reconnecting"
                            route_changed = True
                            break
            except asyncio.IncompleteReadError:
                self.status = "no_source"
                await self._log_exit(proc)
            except asyncio.CancelledError:
                raise
            except Exception:
                self.status = "no_source"
                logger.exception("audio loop failed")
            finally:
                if proc is not None:
                    try:
                        proc.kill()
                    except ProcessLookupError:
                        pass
                    await asyncio.gather(proc.wait(), return_exceptions=True)
                if self._proc is proc:
                    self._proc = None
            if not route_changed:
                await asyncio.sleep(RETRY_INTERVAL)

    async def _log_exit(self, proc):
        if proc is None:
            return
        error = b""
        try:
            error = await proc.stderr.read()
        except (OSError, ValueError):
            pass
        logger.warning(
            "audio capture ended (rc=%s): %s",
            proc.returncode,
            error.decode(errors="replace")[:300],
        )
