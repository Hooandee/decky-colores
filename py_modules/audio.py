import array
import asyncio
import logging
import math
import os

from effects import frame_vu

logger = logging.getLogger("colores.audio")

# asyncio.create_subprocess_exec is execFile-style (no shell); bound to a name so
# the args below are always a static list, never a shell string.
_spawn = asyncio.create_subprocess_exec

RATE = 16000
CHUNK = 1024  # samples per frame (~64ms at 16kHz)
FULL_SCALE = 8000.0  # RMS mapped to level 1.0
RETRY_INTERVAL = 3.0


def _level_from_pcm(data):
    samples = array.array("h")
    samples.frombytes(data[: len(data) // 2 * 2])
    if not samples:
        return 0.0
    rms = math.sqrt(sum(s * s for s in samples) / len(samples))
    return min(1.0, rms / FULL_SCALE)


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

    @property
    def running(self):
        return self._task is not None and not self._task.done()

    @property
    def level(self):
        return self._level

    def _env(self):
        env = dict(os.environ)
        if self._runtime_dir:
            env["XDG_RUNTIME_DIR"] = self._runtime_dir
        return env

    def _cred(self):
        if self._uid is None:
            return {}
        return {"user": self._uid, "group": self._gid}

    def start(self, options=None):
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
        self._level = 0.0

    def _kill(self):
        if self._proc is not None:
            try:
                self._proc.kill()
            except ProcessLookupError:
                pass
            self._proc = None

    def _ease(self, target):
        # Fast attack, slow release — reads like a music VU rather than a raw level.
        alpha = 0.6 if target > self._level else 0.2
        self._level += (target - self._level) * alpha
        return self._level

    async def _run(self):
        command = [
            "parec", "--format=s16le", f"--rate={RATE}", "--channels=1",
            "--device=@DEFAULT_MONITOR@", "--raw",
        ]
        while True:
            proc = None
            try:
                proc = await _spawn(
                    *command,
                    stdout=asyncio.subprocess.PIPE,
                    stderr=asyncio.subprocess.PIPE,
                    env=self._env(),
                    **self._cred(),
                )
                self._proc = proc
                self.status = "running"
                while True:
                    data = await proc.stdout.readexactly(CHUNK * 2)
                    self._apply(frame_vu(self._ease(_level_from_pcm(data)), self._zones))
            except asyncio.IncompleteReadError:
                self.status = "no_source"
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("audio loop failed")
            finally:
                if proc is not None:
                    try:
                        proc.kill()
                    except ProcessLookupError:
                        pass
                if self._proc is proc:
                    self._proc = None
            await asyncio.sleep(RETRY_INTERVAL)
