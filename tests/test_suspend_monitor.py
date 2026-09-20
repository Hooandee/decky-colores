import asyncio
import json
import os

from suspend_monitor import (
    INHIBITOR_GUARD,
    SuspendMonitor,
    _SystemdConnection,
    _system_env,
)


class FakeLogindConnection:
    def __init__(self):
        self.callback = None
        self.ready = asyncio.Event()
        self.disconnected = asyncio.Event()
        self.inhibitors = iter((41, 42, 43))
        self.acquire_count = 0

    async def connect(self, callback):
        self.callback = callback
        return self

    async def acquire_inhibitor(self):
        self.acquire_count += 1
        self.ready.set()
        result = next(self.inhibitors)
        if isinstance(result, Exception):
            raise result
        return result

    async def wait_closed(self):
        await self.disconnected.wait()

    def disconnect(self):
        self.disconnected.set()

    def emit(self, sleeping):
        self.callback(sleeping)


async def _until(predicate):
    for _ in range(20):
        if predicate():
            return
        await asyncio.sleep(0)
    raise AssertionError("condition not reached")


class FakeStream:
    def __init__(self, lines):
        self.lines = list(lines)

    async def readline(self):
        return self.lines.pop(0) if self.lines else b""

    async def read(self):
        return b""

    async def readexactly(self, size):
        value = self.lines.pop(0) if self.lines else b""
        if len(value) < size:
            raise asyncio.IncompleteReadError(value, size)
        return value[:size]


class FakeMonitorProcess:
    def __init__(self, lines):
        self.stdout = FakeStream(lines)
        self.stderr = FakeStream([])
        self.returncode = 0

    async def wait(self):
        return self.returncode

    def terminate(self):
        self.returncode = -15


def test_systemd_connection_parses_prepare_for_sleep_json():
    async def drive():
        observed = []
        message = {
            "type": "signal",
            "path": "/org/freedesktop/login1",
            "interface": "org.freedesktop.login1.Manager",
            "member": "PrepareForSleep",
            "payload": {"type": "b", "data": [True]},
        }
        process = FakeMonitorProcess([(json.dumps(message) + "\n").encode()])
        connection = _SystemdConnection(process, observed.append)

        await connection.wait_closed()

        assert observed == [True]

    asyncio.run(drive())


def test_system_tools_do_not_inherit_decky_library_overrides(monkeypatch):
    monkeypatch.setenv("LD_LIBRARY_PATH", "/tmp/decky-runtime")
    monkeypatch.setenv("LD_PRELOAD", "/tmp/decky-preload.so")

    environment = _system_env()

    assert "LD_LIBRARY_PATH" not in environment
    assert "LD_PRELOAD" not in environment


def test_inhibitor_waits_for_command_started_after_lock_is_acquired():
    async def drive():
        read_started = asyncio.Event()
        release_ready = asyncio.Event()

        class DeferredReadyStream(FakeStream):
            async def readexactly(self, size):
                read_started.set()
                await release_ready.wait()
                return b"ready"[:size]

        inhibitor = FakeMonitorProcess([])
        inhibitor.stdout = DeferredReadyStream([])
        inhibitor.returncode = None
        options = {}
        command = []

        async def spawn(*args, **kwargs):
            command.extend(args)
            options.update(kwargs)
            return inhibitor

        connection = _SystemdConnection(
            FakeMonitorProcess([]),
            lambda sleeping: None,
            spawn=spawn,
        )

        acquire = asyncio.create_task(connection.acquire_inhibitor())
        await read_started.wait()

        assert not acquire.done()

        release_ready.set()
        acquired = await acquire

        assert acquired is inhibitor
        assert command[-1] == str(os.getpid())
        assert options["stdout"] == asyncio.subprocess.PIPE

    asyncio.run(drive())


def test_inhibitor_guard_exits_when_plugin_process_disappears():
    async def drive():
        owner = await asyncio.create_subprocess_exec("sleep", "60")
        guard = await asyncio.create_subprocess_exec(
            "sh",
            "-c",
            INHIBITOR_GUARD,
            "sh",
            str(owner.pid),
            stdout=asyncio.subprocess.PIPE,
        )

        assert await guard.stdout.readexactly(5) == b"ready"

        owner.terminate()
        await owner.wait()
        assert await asyncio.wait_for(guard.wait(), timeout=2) == 0

    asyncio.run(drive())


def test_delay_inhibitor_is_released_only_after_prepare_finishes():
    async def drive():
        connection = FakeLogindConnection()
        release_prepare = asyncio.Event()
        events = []

        async def prepare():
            events.append("prepare-start")
            await release_prepare.wait()
            events.append("prepare-end")

        monitor = SuspendMonitor(
            prepare,
            connect=connection.connect,
            close_fd=lambda fd: events.append(("close", fd)),
        )
        monitor.start()
        await connection.ready.wait()

        connection.emit(True)
        await _until(lambda: "prepare-start" in events)
        assert events == ["prepare-start"]

        release_prepare.set()
        await _until(lambda: ("close", 41) in events)
        assert events == ["prepare-start", "prepare-end", ("close", 41)]
        await monitor.stop_and_wait()

    asyncio.run(drive())


def test_resume_rearms_delay_inhibitor_for_the_next_suspend():
    async def drive():
        connection = FakeLogindConnection()
        closed = []
        monitor = SuspendMonitor(
            lambda: asyncio.sleep(0),
            connect=connection.connect,
            close_fd=closed.append,
        )
        monitor.start()
        await connection.ready.wait()

        connection.emit(True)
        await _until(lambda: closed == [41])
        connection.emit(False)
        await _until(lambda: connection.acquire_count == 2)

        assert closed == [41]
        await monitor.stop_and_wait()
        assert closed == [41, 42]

    asyncio.run(drive())


def test_resume_signal_rearms_before_restoring_lighting():
    async def drive():
        connection = FakeLogindConnection()
        events = []

        async def resume_suspend():
            events.append(("resume", connection.acquire_count))

        monitor = SuspendMonitor(
            lambda: asyncio.sleep(0),
            resume_suspend=resume_suspend,
            connect=connection.connect,
            close_fd=lambda fd: None,
        )
        monitor.start()
        await connection.ready.wait()

        connection.emit(True)
        await _until(lambda: monitor._inhibitor_fd is None)
        connection.emit(False)
        await _until(lambda: bool(events))

        assert events == [("resume", 2)]
        await monitor.stop_and_wait()

    asyncio.run(drive())


def test_stop_releases_inhibitor_and_disconnects():
    async def drive():
        connection = FakeLogindConnection()
        closed = []
        monitor = SuspendMonitor(
            lambda: asyncio.sleep(0),
            connect=connection.connect,
            close_fd=closed.append,
        )
        monitor.start()
        await connection.ready.wait()

        await monitor.stop_and_wait()

        assert closed == [41]
        assert connection.disconnected.is_set()

    asyncio.run(drive())


def test_diagnostics_reports_monitor_connection_and_inhibitor_state():
    async def drive():
        connection = FakeLogindConnection()
        monitor = SuspendMonitor(
            lambda: asyncio.sleep(0),
            connect=connection.connect,
            close_fd=lambda fd: None,
        )

        assert monitor.diagnostics() == {
            "running": False,
            "connected": False,
            "inhibitor_armed": False,
            "sleeping": False,
            "last_error": None,
        }

        monitor.start()
        await connection.ready.wait()

        assert monitor.diagnostics() == {
            "running": True,
            "connected": True,
            "inhibitor_armed": True,
            "sleeping": False,
            "last_error": None,
        }
        await monitor.stop_and_wait()

    asyncio.run(drive())


def test_failed_rearm_disconnects_so_the_monitor_can_recover():
    async def drive():
        connection = FakeLogindConnection()
        connection.inhibitors = iter((41, RuntimeError("inhibit failed"), 43))
        monitor = SuspendMonitor(
            lambda: asyncio.sleep(0),
            connect=connection.connect,
            close_fd=lambda fd: None,
        )
        monitor.start()
        await connection.ready.wait()

        connection.emit(True)
        await _until(lambda: monitor._inhibitor_fd is None)
        connection.emit(False)
        await _until(lambda: connection.acquire_count == 2)

        assert connection.disconnected.is_set()
        await monitor.stop_and_wait()

    asyncio.run(drive())
