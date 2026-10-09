# Decky prunes plugin logs on every load, so the diary lives in the runtime dir.
from __future__ import annotations

import asyncio
import contextlib
import contextvars
import errno
import functools
import inspect
import json
import logging
import logging.handlers
import os
import queue
import re
import sys
import threading
import time
import traceback
from typing import Any, Callable, Iterator

_FILE_PREFIX = "colores-"
_FILE_SUFFIX = ".jsonl"
_FOLD_WINDOW = 6
_NEVER_FOLDED = ("session", "context", "loop")
_LEVELS = {"DEBUG": "D", "INFO": "I", "WARNING": "W", "ERROR": "E", "CRITICAL": "C"}
_MAX_MESSAGE = 4000
_MAX_ARGS = 400
_DAY_S = 86_400
_UNTRACED_PREFIXES = ("_", "get_", "list_", "check_")

active: "Journal | None" = None
_inside_call: contextvars.ContextVar[bool] = contextvars.ContextVar(
    "colores_journal_inside_call", default=False
)


def _day(at: float) -> str:
    return time.strftime("%Y%m%d", time.localtime(at))


def _file_order(name: str) -> tuple[str, int]:
    stem = name[len(_FILE_PREFIX):-len(_FILE_SUFFIX)]
    day, _, segment = stem.partition(".")
    return day, int(segment) if segment.isdigit() else 0


def _files(directory: str) -> list[str]:
    try:
        names = [
            name for name in os.listdir(directory)
            if name.startswith(_FILE_PREFIX) and name.endswith(_FILE_SUFFIX)
        ]
    except OSError:
        return []
    return [os.path.join(directory, name) for name in sorted(names, key=_file_order)]


class Journal:
    def __init__(
        self,
        directory: str,
        *,
        retention_days: int = 7,
        max_total_bytes: int = 16 * 1024 * 1024,
        max_file_bytes: int = 4 * 1024 * 1024,
        queue_size: int = 4096,
        coalesce_s: float = 1.5,
        repeat_s: float = 60.0,
        clock: Callable[[], float] = time.time,
    ) -> None:
        self.directory = directory
        self._retention_s = retention_days * _DAY_S
        self._max_total_bytes = max_total_bytes
        self._max_file_bytes = max_file_bytes
        self._coalesce_s = coalesce_s
        self._repeat_s = repeat_s
        self._clock = clock
        self._queue: queue.Queue = queue.Queue(maxsize=queue_size)
        self._thread: threading.Thread | None = None
        self._stopping = threading.Event()
        self._file = None
        self._file_name: str | None = None
        self._file_day: str | None = None
        self._file_segment = 0
        self._pending: dict | None = None
        self._recent: dict[str, float] = {}
        self._folded: dict[str, dict] = {}
        self.dropped = 0
        self.write_failures = 0

    def start(self) -> None:
        if self._thread is not None:
            return
        self._stopping.clear()
        self._thread = threading.Thread(target=self._run, name="colores-journal", daemon=True)
        self._thread.start()

    def stop(self, timeout: float = 2.0) -> None:
        thread = self._thread
        if thread is None:
            return
        try:
            self._queue.put_nowait(None)
        except queue.Full:
            self._stopping.set()
        thread.join(timeout)
        self._thread = None

    def write(self, level: str, source: str, message: str, /, *, at: float | None = None, **fields: Any) -> None:
        try:
            record = {
                "t": round(self._clock() if at is None else at, 3),
                "l": _LEVELS[level],
                "s": source,
                "m": message if len(message) <= _MAX_MESSAGE else message[:_MAX_MESSAGE] + "…",
            }
            record.update({key: value for key, value in fields.items() if key not in record})
            self._queue.put_nowait(record)
        except queue.Full:
            self.dropped += 1
        except Exception:  # noqa: BLE001
            self.write_failures += 1

    def files(self) -> list[str]:
        return _files(self.directory)

    def _run(self) -> None:
        try:
            os.makedirs(self.directory, exist_ok=True)
        except OSError:
            self.write_failures += 1
        self._enforce_limits()
        while True:
            try:
                record = self._queue.get(timeout=self._coalesce_s or 1.0)
            except queue.Empty:
                self._flush_stale()
                self._sync()
                if self._stopping.is_set():
                    break
                continue
            if record is None:
                break
            self._accept(record)
            while True:
                try:
                    record = self._queue.get_nowait()
                except queue.Empty:
                    break
                if record is None:
                    self._stopping.set()
                    break
                self._accept(record)
            self._sync()
            if self._stopping.is_set():
                break
        self._flush_pending()
        self._close_file()

    def _accept(self, record: dict) -> None:
        if record.get("s") == "rpc":
            self._accept_call(record)
            return
        self._flush_call()
        self._flush_folded(now=record["t"])
        if record.get("s") in _NEVER_FOLDED:
            self._append(record)
            return
        key = json.dumps({k: v for k, v in record.items() if k != "t"}, sort_keys=True, default=str)
        written_at = self._recent.get(key)
        if written_at is not None and record["t"] - written_at <= self._repeat_s:
            folded = self._folded.get(key)
            if folded is None:
                self._folded[key] = {**record, "n": 1, "last": record["t"]}
            else:
                folded["n"] += 1
                folded["last"] = record["t"]
            return
        self._append(record)
        self._remember(key, record["t"])

    def _remember(self, key: str, at: float) -> None:
        self._recent.pop(key, None)
        self._recent[key] = at
        while len(self._recent) > _FOLD_WINDOW:
            self._recent.pop(next(iter(self._recent)))

    def _accept_call(self, record: dict) -> None:
        pending = self._pending
        if (
            pending is not None
            and self._coalesce_s > 0
            and pending["m"] == record["m"]
            and "r" not in pending
            and "r" not in record
            and record["t"] - pending.get("last", pending["t"]) <= self._coalesce_s
        ):
            pending["a"] = record.get("a")
            pending["last"] = record["t"]
            pending["n"] = pending.get("n", 1) + 1
            return
        self._flush_pending()
        self._pending = dict(record)

    def _flush_call(self) -> None:
        if self._pending is not None:
            record, self._pending = self._pending, None
            self._append(record)

    def _flush_folded(self, *, now: float | None = None, every: bool = False) -> None:
        now = self._clock() if now is None else now
        for key, folded in list(self._folded.items()):
            if every or now - folded["t"] >= self._repeat_s:
                del self._folded[key]
                self._append(folded)
                self._remember(key, folded["last"])

    def _flush_pending(self) -> None:
        self._flush_call()
        self._flush_folded(every=True)

    def _flush_stale(self) -> None:
        now = self._clock()
        if self._pending is not None and now - self._pending.get("last", self._pending["t"]) > self._coalesce_s:
            self._flush_call()
        self._flush_folded()

    def _append(self, record: dict) -> None:
        try:
            line = json.dumps(record, ensure_ascii=False, separators=(",", ":"), default=str) + "\n"
            day = _day(record["t"])
            if self._file is None or self._file_day != day:
                self._open(day, 0)
            while self._file.tell() >= self._max_file_bytes:
                self._open(day, self._file_segment + 1)
            self._file.write(line)
        except Exception:  # noqa: BLE001
            self.write_failures += 1

    def _open(self, day: str, segment: int) -> None:
        self._close_file()
        suffix = f".{segment}" if segment else ""
        name = f"{_FILE_PREFIX}{day}{suffix}{_FILE_SUFFIX}"
        self._file = open(os.path.join(self.directory, name), "a", encoding="utf-8")
        self._file_name = name
        self._file_day = day
        self._file_segment = segment
        self._enforce_limits()

    def _sync(self) -> None:
        try:
            if self._file is not None:
                self._file.flush()
        except Exception:  # noqa: BLE001
            self.write_failures += 1

    def _close_file(self) -> None:
        try:
            if self._file is not None:
                self._file.close()
        except Exception:  # noqa: BLE001
            pass
        self._file = None
        self._file_name = None

    def _enforce_limits(self) -> None:
        cutoff = _day(self._clock() - self._retention_s)
        sized: list[tuple[str, int]] = []
        for path in self.files():
            day = os.path.basename(path)[len(_FILE_PREFIX):len(_FILE_PREFIX) + 8]
            try:
                if day < cutoff:
                    os.unlink(path)
                    continue
                sized.append((path, os.path.getsize(path)))
            except OSError:
                continue
        total = sum(size for _, size in sized)
        current = os.path.join(self.directory, self._file_name) if self._file_name else None
        for path, size in sized:
            if total <= self._max_total_bytes:
                break
            if path == current:
                continue
            try:
                os.unlink(path)
                total -= size
            except OSError:
                continue


def read_records(directory: str) -> Iterator[dict]:
    for path in _files(directory):
        try:
            with open(path, encoding="utf-8", errors="replace") as handle:
                for line in handle:
                    try:
                        record = json.loads(line)
                    except ValueError:
                        continue
                    if isinstance(record, dict):
                        yield record
        except OSError:
            continue


def last_record(directory: str, source: str, *, max_bytes: int = 1_000_000) -> dict | None:
    for path in reversed(_files(directory)[-2:]):
        try:
            with open(path, "rb") as handle:
                handle.seek(0, os.SEEK_END)
                size = handle.tell()
                handle.seek(max(0, size - max_bytes))
                lines = handle.read().decode("utf-8", "replace").splitlines()
        except OSError:
            continue
        for line in reversed(lines):
            try:
                record = json.loads(line)
            except ValueError:
                continue
            if isinstance(record, dict) and record.get("s") == source:
                return record
    return None


_KEY_SOURCES = ("rpc", "session", "context", "state", "hw", "loop")
_KEY_MESSAGES = ("Colores v", "suspend", "restored lighting")


def _is_key_record(record: dict) -> bool:
    if record.get("l") in ("W", "E", "C") or record.get("s") in _KEY_SOURCES:
        return True
    message = record.get("m")
    return isinstance(message, str) and any(key in message for key in _KEY_MESSAGES)


def collect(
    directory: str,
    *,
    now: float | None = None,
    recent_s: float = _DAY_S,
    recent_bytes: int = 700_000,
    older_bytes: int = 500_000,
) -> dict:
    now = time.time() if now is None else now
    recent: list[dict] = []
    older: list[dict] = []
    for record in read_records(directory):
        at = record.get("t")
        if not isinstance(at, (int, float)):
            continue
        if at >= now - recent_s:
            recent.append(record)
        elif _is_key_record(record):
            older.append(record)

    def newest_within(records: list[dict], budget: int) -> tuple[list[dict], int]:
        kept: list[dict] = []
        for record in reversed(records):
            size = len(json.dumps(record, ensure_ascii=False, separators=(",", ":"), default=str)) + 1
            if size > budget:
                break
            budget -= size
            kept.append(record)
        kept.reverse()
        return kept, len(records) - len(kept)

    recent_kept, recent_cut = newest_within(recent, recent_bytes)
    older_kept, older_cut = newest_within(older, older_bytes)
    files = []
    for path in _files(directory):
        try:
            files.append({"name": os.path.basename(path), "bytes": os.path.getsize(path)})
        except OSError:
            continue
    return {
        "schema": 1,
        "summary": summarize(recent + older),
        "files": files,
        "recent": recent_kept,
        "recent_omitted": recent_cut,
        "older": older_kept,
        "older_omitted": older_cut,
    }


def _count(record: dict) -> int:
    count = record.get("n", 1)
    return count if isinstance(count, int) and count > 0 else 1


_NUMBER = re.compile(r"\d+(?:\.\d+)?")


def _problem_key(record: dict) -> str:
    message = str(record.get("m", ""))
    if record.get("s") == "hw":
        message = f"{message} {record.get('target', '')} {record.get('error', '')}"
    return _NUMBER.sub("#", message)[:120]


def summarize(records: list[dict], *, top: int = 5) -> list[dict]:
    sessions: list[dict] = []
    current: dict | None = None
    rivals: list = []
    for record in sorted(records, key=lambda item: item.get("t", 0)):
        if current is None or (record.get("s") == "session" and record.get("m") == "start"):
            current = {
                "start": record.get("t"),
                "last_line": record.get("t"),
                "version": record.get("version"),
                "device": None,
                "modes": [],
                "rivals": list(rivals),
                "actions": 0,
                "write_failures": 0,
                "loop_blocks": 0,
                "state_changes": {},
                "_problems": {},
            }
            sessions.append(current)
        current["last_line"] = record.get("last", record.get("t"))
        source = record.get("s")
        if source == "session" and record.get("m") == "stop":
            current["stopped"] = True
        elif source == "rpc" and not record.get("auto"):
            current["actions"] += _count(record)
        elif source == "context":
            rivals = [rival.get("name") for rival in record.get("rivals") or []]
            current["rivals"] = list(rivals)
        elif source == "hw" and record.get("m") == "write_failed":
            current["write_failures"] += _count(record)
        elif source == "loop" and record.get("m") == "blocked":
            current["loop_blocks"] += 1
        elif source == "state":
            changes = current["state_changes"]
            changes[record.get("m")] = changes.get(record.get("m"), 0) + _count(record)
            if record.get("m") == "device":
                current["device"] = {
                    key: record.get(key) for key in ("name", "driver", "zones", "available")
                }
            mode = record.get("mode") if record.get("m") == "mode" else None
            if mode is not None and mode not in current["modes"]:
                current["modes"].append(mode)
        if record.get("l") in ("W", "E", "C"):
            key = (record.get("l"), _problem_key(record))
            current["_problems"][key] = current["_problems"].get(key, 0) + _count(record)
    for session in sessions:
        problems = session.pop("_problems")
        session["problems"] = [
            {"level": level, "message": message, "count": count}
            for (level, message), count in sorted(problems.items(), key=lambda item: -item[1])[:top]
        ]
        session["problem_kinds"] = len(problems)
    return sessions


class LoopWatchdog:
    # Built on the loop's thread so a freeze before the first heartbeat still has a stack.
    def __init__(
        self,
        journal: Journal,
        *,
        limit_s: float = 3.0,
        beat_s: float = 1.0,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        self._journal = journal
        self._limit_s = limit_s
        self._beat_s = beat_s
        self._clock = clock
        self._last_beat = clock()
        self._loop_thread = threading.get_ident()
        self._stuck_since: float | None = None
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    async def beat(self) -> None:
        while not self._stop.is_set():
            self._last_beat = self._clock()
            await asyncio.sleep(self._beat_s)

    def start(self) -> None:
        if self._thread is None:
            self._stop.clear()
            self._thread = threading.Thread(target=self._watch, name="colores-loop-watchdog", daemon=True)
            self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        self._thread = None

    def check(self) -> None:
        now = self._clock()
        late = now - self._last_beat
        if late >= self._limit_s and self._stuck_since is None:
            self._stuck_since = self._last_beat
            self._journal.write("WARNING", "loop", "blocked", after_s=round(late, 1), stack=self._loop_stack())
        elif late < self._limit_s and self._stuck_since is not None:
            self._journal.write("WARNING", "loop", "recovered", stuck_s=round(self._last_beat - self._stuck_since, 1))
            self._stuck_since = None

    def _loop_stack(self) -> str:
        frame = sys._current_frames().get(self._loop_thread)
        if frame is None:
            return ""
        return "".join(traceback.format_stack(frame)[-12:])[-3000:]

    def _watch(self) -> None:
        while not self._stop.wait(self._beat_s):
            self.check()


class JournalHandler(logging.Handler):

    def __init__(self, journal: Journal) -> None:
        super().__init__(logging.INFO)
        self._journal = journal

    def emit(self, record: logging.LogRecord) -> None:
        try:
            message = record.getMessage()
            if record.exc_info:
                message = f"{message}\n{logging.Formatter().formatException(record.exc_info)}"
            fields = {} if record.name == "root" else {"lg": record.name}
            self._journal.write(record.levelname, "log", message, at=record.created, **fields)
        except Exception:  # noqa: BLE001
            pass


class _DroppingQueueHandler(logging.handlers.QueueHandler):
    def enqueue(self, record: logging.LogRecord) -> None:
        try:
            self.queue.put_nowait(record)
        except queue.Full:
            pass


def queue_logger_handlers(logger: logging.Logger, *, queue_size: int = 4096) -> Callable[[], None]:
    # Decky's file and stdout handlers write on the calling thread, the event loop included.
    handlers = [handler for handler in logger.handlers if not isinstance(handler, JournalHandler)]
    if not handlers:
        return lambda: None
    records: queue.Queue = queue.Queue(maxsize=queue_size)
    front = _DroppingQueueHandler(records)
    listener = logging.handlers.QueueListener(records, *handlers, respect_handler_level=True)
    listener.start()
    for handler in handlers:
        logger.removeHandler(handler)
    logger.addHandler(front)

    def restore() -> None:
        logger.removeHandler(front)
        listener.stop()
        for handler in handlers:
            logger.addHandler(handler)

    return restore


_ERRNO_TEXT = re.compile(r"\[Errno (\d+)\]")


def _error_code(error: Any) -> str:
    text = str(error)
    match = _ERRNO_TEXT.search(text)
    if match:
        return errno.errorcode.get(int(match.group(1)), match.group(1))
    return text[:80]


def write_failed(target: Any, value: Any, error: Any, *, by: str) -> None:
    diary = active
    if diary is None:
        return
    try:
        fields = {}
        if _error_code(error) != str(error):
            fields["detail"] = str(error)[:160]
        diary.write("WARNING", "hw", "write_failed", target=str(target), value=str(value)[:40],
                    error=_error_code(error), by=by, **fields)
    except Exception:  # noqa: BLE001
        pass


@contextlib.contextmanager
def internal() -> Iterator[None]:
    token = _inside_call.set(True)
    try:
        yield
    finally:
        _inside_call.reset(token)


def _condensed(value: Any, depth: int = 0) -> Any:
    if isinstance(value, dict):
        if depth >= 3:
            return sorted(value)[:12]
        return {key: _condensed(item, depth + 1) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        if value and all(isinstance(item, dict) for item in value):
            ids = [item.get("id") or item.get("name") or item.get("kind") for item in value]
            if all(ids):
                return ids
        return [_condensed(item, depth + 1) for item in value[:12]] + (["…"] if len(value) > 12 else [])
    if isinstance(value, str) and len(value) > 60:
        return value[:60] + "…"
    return value


def _summary(value: Any) -> str:
    try:
        text = json.dumps(value, ensure_ascii=False, separators=(",", ":"), default=str)
        if len(text) > _MAX_ARGS:
            text = json.dumps(_condensed(value), ensure_ascii=False, separators=(",", ":"), default=str)
    except Exception:  # noqa: BLE001
        text = repr(value)
    return text if len(text) <= _MAX_ARGS else text[:_MAX_ARGS] + "…"


def _where(error: BaseException) -> str:
    frames = traceback.extract_tb(error.__traceback__)[-2:]
    return " < ".join(f"{os.path.basename(frame.filename)}:{frame.lineno} {frame.name}" for frame in reversed(frames))


def _outcome(result: Any) -> dict | None:
    if result is False:
        return {"ok": False}
    if isinstance(result, dict) and result.get("ok") is False:
        detail = result.get("error") or result.get("detail") or result.get("reason")
        return {"ok": False, "error": _summary(detail)[:160]} if detail is not None else {"ok": False}
    return None


def trace_calls(
    cls: type,
    *,
    hidden_arguments: frozenset[str] = frozenset(),
    automatic: frozenset[str] = frozenset(),
) -> None:
    for name, function in list(vars(cls).items()):
        if name.startswith(_UNTRACED_PREFIXES) or not inspect.iscoroutinefunction(function):
            continue
        setattr(cls, name, _traced(name, function, name in hidden_arguments, name in automatic))


def _traced(name: str, function: Callable, hide_arguments: bool, automatic: bool) -> Callable:
    @functools.wraps(function)
    async def call(self, *args, **kwargs):
        journal = active
        if journal is None or _inside_call.get():
            return await function(self, *args, **kwargs)
        arguments = None if hide_arguments else _summary(list(args) + ([kwargs] if kwargs else []))
        marks = {"auto": True} if automatic else {}
        token = _inside_call.set(True)
        try:
            result = await function(self, *args, **kwargs)
        except Exception as error:
            journal.write("ERROR", "rpc", name, a=arguments, r={"raised": type(error).__name__, "where": _where(error), "message": str(error)[:160]}, **marks)
            raise
        finally:
            _inside_call.reset(token)
        outcome = _outcome(result)
        if outcome is None:
            journal.write("INFO", "rpc", name, a=arguments, **marks)
        else:
            journal.write("WARNING", "rpc", name, a=arguments, r=outcome, **marks)
        return result

    return call
