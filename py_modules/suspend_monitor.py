import asyncio
import json
import logging
import os


LOGIND_PATH = "/org/freedesktop/login1"
LOGIND_INTERFACE = "org.freedesktop.login1.Manager"
RETRY_INTERVAL = 5.0
INHIBITOR_READY_TIMEOUT = 2.0
INHIBITOR_READY = b"ready"
INHIBITOR_GUARD = (
    'printf ready; while kill -0 "$1" 2>/dev/null; do sleep 1; done'
)
BUSCTL_COMMAND = (
    "busctl",
    "monitor",
    "--system",
    "--match=type=signal,sender=org.freedesktop.login1,"
    "path=/org/freedesktop/login1,"
    "interface=org.freedesktop.login1.Manager,member=PrepareForSleep",
    "--json=short",
)
INHIBIT_COMMAND = (
    "systemd-inhibit",
    "--what=sleep",
    "--who=Colores",
    "--why=Stop lighting before sleep",
    "--mode=delay",
    "sh",
    "-c",
    INHIBITOR_GUARD,
    "sh",
)

_spawn = asyncio.create_subprocess_exec


def _system_env():
    environment = os.environ.copy()
    environment.pop("LD_LIBRARY_PATH", None)
    environment.pop("LD_PRELOAD", None)
    return environment


class _SystemdConnection:
    def __init__(self, process, callback, spawn=None):
        self._process = process
        self._callback = callback
        self._spawn = spawn or _spawn
        self._stopping = False

    async def acquire_inhibitor(self):
        process = await self._spawn(
            *INHIBIT_COMMAND,
            str(os.getpid()),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            env=_system_env(),
        )
        try:
            ready = await asyncio.wait_for(
                process.stdout.readexactly(len(INHIBITOR_READY)),
                timeout=INHIBITOR_READY_TIMEOUT,
            )
        except (TimeoutError, asyncio.IncompleteReadError):
            ready = b""
        if ready != INHIBITOR_READY:
            if process.returncode is None:
                process.terminate()
            await process.wait()
            error = (await process.stderr.read()).decode(errors="replace").strip()
            raise RuntimeError(error or "systemd-inhibit did not acquire lock")
        return process

    async def wait_closed(self):
        while True:
            line = await self._process.stdout.readline()
            if not line:
                break
            try:
                message = json.loads(line)
            except (TypeError, ValueError):
                continue
            if (
                message.get("type") == "signal"
                and message.get("path") == LOGIND_PATH
                and message.get("interface") == LOGIND_INTERFACE
                and message.get("member") == "PrepareForSleep"
            ):
                values = message.get("payload", {}).get("data", [])
                if len(values) == 1 and isinstance(values[0], bool):
                    self._callback(values[0])
        code = await self._process.wait()
        if code and not self._stopping:
            error = (await self._process.stderr.read()).decode(errors="replace").strip()
            raise RuntimeError(error or f"busctl monitor exited with status {code}")

    def disconnect(self):
        self._stopping = True
        if self._process.returncode is not None:
            return
        try:
            self._process.terminate()
        except ProcessLookupError:
            pass
        asyncio.create_task(self._process.wait())


async def _connect_systemd(callback):
    process = await _spawn(
        *BUSCTL_COMMAND,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
        env=_system_env(),
    )
    await asyncio.sleep(0)
    if process.returncode is not None:
        error = (await process.stderr.read()).decode(errors="replace").strip()
        raise RuntimeError(error or "busctl monitor exited during startup")
    return _SystemdConnection(process, callback)


def _release_inhibitor(handle):
    if isinstance(handle, int):
        os.close(handle)
        return
    if handle.returncode is None:
        try:
            handle.terminate()
        except ProcessLookupError:
            pass
    asyncio.create_task(handle.wait())


class SuspendMonitor:
    def __init__(
        self,
        prepare_suspend,
        *,
        resume_suspend=None,
        connect=None,
        close_fd=None,
        logger=None,
        retry_interval=RETRY_INTERVAL,
    ):
        self._prepare_suspend = prepare_suspend
        self._resume_suspend = resume_suspend
        self._connect = connect or _connect_systemd
        self._close_fd = close_fd or _release_inhibitor
        self._logger = logger or logging.getLogger(__name__)
        self._retry_interval = retry_interval
        self._runner = None
        self._connection = None
        self._inhibitor_fd = None
        self._transitions = set()
        self._transition_lock = asyncio.Lock()
        self._sleeping = False
        self._stopping = False
        self._last_error = None

    def start(self):
        if self._runner and not self._runner.done():
            return
        self._stopping = False
        self._runner = asyncio.create_task(self._run())

    def diagnostics(self):
        return {
            "running": bool(self._runner and not self._runner.done()),
            "connected": self._connection is not None,
            "inhibitor_armed": self._inhibitor_fd is not None,
            "sleeping": self._sleeping,
            "last_error": self._last_error,
        }

    async def stop_and_wait(self):
        self._stopping = True
        tasks = list(self._transitions)
        if self._runner:
            self._runner.cancel()
            tasks.append(self._runner)
        for task in self._transitions:
            task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)
        self._release_inhibitor()
        self._disconnect()

    async def _run(self):
        try:
            while not self._stopping:
                try:
                    self._connection = await self._connect(self._on_prepare_for_sleep)
                    self._sleeping = False
                    await self._arm_inhibitor()
                    self._last_error = None
                    self._logger.info("Colores: native suspend monitor ready")
                    await self._connection.wait_closed()
                except asyncio.CancelledError:
                    raise
                except Exception as error:
                    self._warn_once("Colores: native suspend monitor failed: %s", error)
                finally:
                    self._release_inhibitor()
                    self._disconnect()
                if not self._stopping:
                    await asyncio.sleep(self._retry_interval)
        except asyncio.CancelledError:
            raise

    def _on_prepare_for_sleep(self, sleeping):
        if self._stopping:
            return
        task = asyncio.create_task(self._handle_transition(bool(sleeping)))
        self._transitions.add(task)
        task.add_done_callback(self._transitions.discard)

    async def _handle_transition(self, sleeping):
        async with self._transition_lock:
            if sleeping:
                if self._sleeping:
                    return
                self._sleeping = True
                try:
                    await self._prepare_suspend()
                except asyncio.CancelledError:
                    raise
                except Exception as error:
                    self._logger.error(
                        "Colores: failed to prepare lighting for suspend: %s", error
                    )
                finally:
                    self._release_inhibitor()
                return
            self._sleeping = False
            if not self._stopping and self._connection and self._inhibitor_fd is None:
                try:
                    await self._arm_inhibitor()
                except asyncio.CancelledError:
                    raise
                except Exception as error:
                    self._logger.warning(
                        "Colores: could not re-arm suspend inhibitor: %s", error
                    )
                    self._disconnect()
            if self._resume_suspend:
                try:
                    await self._resume_suspend()
                except asyncio.CancelledError:
                    raise
                except Exception as error:
                    self._logger.warning(
                        "Colores: could not restore after suspend signal: %s", error
                    )

    async def _arm_inhibitor(self):
        self._inhibitor_fd = await self._connection.acquire_inhibitor()

    def _release_inhibitor(self):
        fd = self._inhibitor_fd
        self._inhibitor_fd = None
        if fd is None:
            return
        try:
            self._close_fd(fd)
        except OSError as error:
            self._logger.warning(
                "Colores: could not release suspend inhibitor: %s", error
            )

    def _disconnect(self):
        connection = self._connection
        self._connection = None
        if connection is not None:
            connection.disconnect()

    def _warn_once(self, message, error):
        value = str(error)
        if value == self._last_error:
            return
        self._last_error = value
        self._logger.warning(message, error)
