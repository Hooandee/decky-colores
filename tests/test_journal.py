import asyncio
import json
import logging
import os
import sys
import time

import journal
from journal import (
    Journal,
    JournalHandler,
    LoopWatchdog,
    collect,
    last_record,
    queue_logger_handlers,
    read_records,
    summarize,
    trace_calls,
)

DAY = 86_400


def _lines(directory):
    return list(read_records(directory))


def _run(j, *writes):
    j.start()
    for args, kwargs in writes:
        j.write(*args, **kwargs)
    j.stop()


def test_writes_one_json_line_per_record_in_a_daily_file(tmp_path):
    now = 1_790_000_000.0
    j = Journal(str(tmp_path), clock=lambda: now)
    _run(j, (("INFO", "log", "hello"), {"at": now - 5}), (("WARNING", "log", "careful"), {}))
    records = _lines(str(tmp_path))
    assert [(r["l"], r["m"]) for r in records] == [("I", "hello"), ("W", "careful")]
    assert all(os.path.basename(p).startswith("colores-") for p in j.files())


def test_full_queue_drops_instead_of_blocking(tmp_path):
    j = Journal(str(tmp_path), queue_size=2)
    started = time.monotonic()
    for index in range(50):
        j.write("INFO", "log", f"line {index}")
    assert time.monotonic() - started < 0.5
    assert j.dropped == 48


def test_old_days_and_excess_size_are_deleted(tmp_path):
    now = 1_790_000_000.0
    for days_ago in (10, 3, 2):
        stamp = time.strftime("%Y%m%d", time.localtime(now - days_ago * DAY))
        (tmp_path / f"colores-{stamp}.jsonl").write_text("x" * 600 + "\n")
    (tmp_path / "unrelated.jsonl").write_text("keep")
    j = Journal(str(tmp_path), retention_days=7, max_total_bytes=1000, clock=lambda: now)
    _run(j, (("INFO", "log", "today"), {"at": now}))
    names = sorted(os.path.basename(p) for p in j.files())
    today = time.strftime("%Y%m%d", time.localtime(now))
    two_days = time.strftime("%Y%m%d", time.localtime(now - 2 * DAY))
    assert names == [f"colores-{two_days}.jsonl", f"colores-{today}.jsonl"]
    assert (tmp_path / "unrelated.jsonl").exists()


def test_a_full_day_file_continues_in_the_next_segment(tmp_path):
    now = 1_790_000_000.0
    j = Journal(str(tmp_path), max_file_bytes=120, clock=lambda: now)
    _run(j, *[(("INFO", "log", f"line {i}"), {"at": now}) for i in range(6)])
    assert [r["m"] for r in _lines(str(tmp_path))] == [f"line {i}" for i in range(6)]
    assert len(j.files()) > 1


def test_repeated_calls_of_one_action_collapse_into_the_last(tmp_path):
    j = Journal(str(tmp_path), coalesce_s=1.5, clock=lambda: 200.0)
    _run(
        j,
        (("INFO", "rpc", "set_brightness"), {"at": 100.0, "a": "[10]"}),
        (("INFO", "rpc", "set_brightness"), {"at": 100.5, "a": "[40]"}),
        (("INFO", "rpc", "set_brightness"), {"at": 101.0, "a": "[80]"}),
        (("INFO", "rpc", "set_mode"), {"at": 101.2, "a": '["effect"]'}),
        (("INFO", "rpc", "set_brightness"), {"at": 110.0, "a": "[20]"}),
    )
    calls = [(r["m"], r["a"], r.get("n", 1)) for r in _lines(str(tmp_path))]
    assert calls == [("set_brightness", "[80]", 3), ("set_mode", '["effect"]', 1), ("set_brightness", "[20]", 1)]


def test_a_failed_call_never_folds_into_a_successful_one(tmp_path):
    j = Journal(str(tmp_path), coalesce_s=1.5, clock=lambda: 200.0)
    _run(
        j,
        (("INFO", "rpc", "reconnect"), {"at": 100.0, "a": "[]"}),
        (("WARNING", "rpc", "reconnect"), {"at": 100.0, "a": "[]", "r": {"ok": False}}),
        (("INFO", "rpc", "reconnect"), {"at": 100.0, "a": "[]"}),
    )
    assert [r.get("r") for r in _lines(str(tmp_path))] == [None, {"ok": False}, None]


def test_no_window_means_no_folding_even_within_one_millisecond(tmp_path):
    j = Journal(str(tmp_path), coalesce_s=0, clock=lambda: 200.0)
    _run(j, *[(("INFO", "rpc", "set_brightness"), {"at": 100.0, "a": f"[{i}]"}) for i in range(3)])
    assert [r["a"] for r in _lines(str(tmp_path))] == ["[0]", "[1]", "[2]"]


def test_identical_lines_in_a_row_become_one_with_a_count(tmp_path):
    j = Journal(str(tmp_path), clock=lambda: 200.0)
    _run(
        j,
        (("WARNING", "log", "gamescope PipeWire node not found; retrying"), {"at": 100.0}),
        (("WARNING", "log", "gamescope PipeWire node not found; retrying"), {"at": 100.1}),
        (("WARNING", "log", "gamescope PipeWire node not found; retrying"), {"at": 100.2}),
        (("INFO", "log", "other"), {"at": 101.0}),
        (("WARNING", "log", "gamescope PipeWire node not found; retrying"), {"at": 300.0}),
    )
    records = sorted((r["t"], r["m"][:9], r.get("n", 1)) for r in _lines(str(tmp_path)))
    assert records == [
        (100.0, "gamescope", 1),
        (100.1, "gamescope", 2),
        (101.0, "other", 1),
        (300.0, "gamescope", 1),
    ]


def test_a_loop_of_alternating_lines_is_folded_with_counts(tmp_path):
    j = Journal(str(tmp_path), clock=lambda: 1000.0)
    cycle = ["hid write failed", "reconnect", "apply"]
    writes = [(("WARNING", "log", cycle[step % 3]), {"at": 900.0 + step}) for step in range(30)]
    _run(j, *writes)
    records = _lines(str(tmp_path))
    assert len(records) == 6
    assert sum(r.get("n", 1) for r in records) == 30


def test_a_long_loop_reports_its_count_every_minute(tmp_path):
    now = [0.0]
    j = Journal(str(tmp_path), clock=lambda: now[0])
    j.start()
    for step in range(300):
        now[0] = 1000.0 + step
        j.write("WARNING", "log", ("write failed", "apply")[step % 2], at=now[0])
    j.stop()
    records = _lines(str(tmp_path))
    assert sum(r.get("n", 1) for r in records) == 300
    assert 8 <= len(records) <= 14


def test_session_and_context_records_are_never_folded(tmp_path):
    j = Journal(str(tmp_path), clock=lambda: 200.0)
    _run(j, *[(("INFO", "session", "start"), {"at": 100.0 + i}) for i in range(3)])
    assert len(_lines(str(tmp_path))) == 3


def test_fields_named_like_the_record_never_break_a_write(tmp_path):
    j = Journal(str(tmp_path))
    j.start()
    j.write("WARNING", "state", "ambilight", status="no_source", m="ignored", t="x")
    j.stop()
    record = _lines(str(tmp_path))[0]
    assert (record["s"], record["m"], record["status"]) == ("state", "ambilight", "no_source")
    assert j.write_failures == 0


def test_collect_sends_the_last_day_whole_and_only_key_records_before(tmp_path):
    now = 1_790_000_000.0
    j = Journal(str(tmp_path), clock=lambda: now)
    _run(
        j,
        (("INFO", "log", "frame stats"), {"at": now - 3 * DAY}),
        (("INFO", "log", "Colores v1.0 on rog_ally"), {"at": now - 3 * DAY + 1}),
        (("WARNING", "log", "hid write failed"), {"at": now - 2 * DAY}),
        (("INFO", "rpc", "set_mode"), {"at": now - 2 * DAY + 5, "a": '["effect"]'}),
        (("INFO", "state", "mode"), {"at": now - 2 * DAY + 6, "mode": "effect"}),
        (("INFO", "log", "frame stats"), {"at": now - 60}),
    )
    bundle = collect(str(tmp_path), now=now)
    assert [r["m"] for r in bundle["older"]] == [
        "Colores v1.0 on rog_ally", "hid write failed", "set_mode", "mode",
    ]
    assert [r["m"] for r in bundle["recent"]] == ["frame stats"]
    assert bundle["schema"] == 1 and bundle["files"]


def test_collect_keeps_the_newest_records_within_budget(tmp_path):
    now = 1_790_000_000.0
    j = Journal(str(tmp_path), clock=lambda: now)
    _run(j, *[(("INFO", "log", f"line {i:03d}"), {"at": now - 100 + i}) for i in range(100)])
    bundle = collect(str(tmp_path), now=now, recent_bytes=200)
    assert bundle["recent"][-1]["m"] == "line 099"
    assert bundle["recent_omitted"] == 100 - len(bundle["recent"])


def test_collect_of_a_missing_directory_is_empty(tmp_path):
    bundle = collect(str(tmp_path / "missing"))
    assert bundle["recent"] == [] and bundle["older"] == [] and bundle["summary"] == []


def test_summary_condenses_each_session():
    records = [
        {"t": 10, "s": "session", "m": "start", "version": "0.24.0"},
        {"t": 11, "s": "context", "m": "snapshot", "rivals": [{"name": "hhd"}]},
        {"t": 12, "s": "state", "m": "device", "name": "rog_ally", "driver": "AllyHid", "zones": 4, "available": True},
        {"t": 13, "s": "state", "m": "mode", "mode": "solid"},
        {"t": 14, "s": "state", "m": "mode", "mode": "ambient"},
        {"t": 15, "s": "rpc", "m": "set_mode", "n": 3},
        {"t": 15.5, "s": "rpc", "m": "set_current_app", "auto": True},
        {"t": 16, "l": "W", "s": "hw", "m": "write_failed", "target": "hid", "error": "EIO"},
        {"t": 17, "l": "W", "s": "hw", "m": "write_failed", "target": "hid", "error": "EIO", "n": 4, "last": 30},
        {"t": 18, "l": "W", "s": "loop", "m": "blocked"},
        {"t": 40, "s": "session", "m": "stop"},
        {"t": 50, "s": "session", "m": "start", "version": "0.24.0"},
    ]
    first, second = summarize(records)
    assert first["device"] == {"name": "rog_ally", "driver": "AllyHid", "zones": 4, "available": True}
    assert first["modes"] == ["solid", "ambient"] and first["actions"] == 3
    assert first["rivals"] == ["hhd"] and first["write_failures"] == 5 and first["loop_blocks"] == 1
    assert first["problems"][0] == {"level": "W", "message": "write_failed hid EIO", "count": 5}
    assert first["stopped"] is True and first["state_changes"] == {"device": 1, "mode": 2}
    assert first["last_line"] == 40
    assert second["start"] == 50 and "stopped" not in second and second["rivals"] == ["hhd"]


def test_problems_differing_only_in_numbers_are_one_entry():
    records = [
        {"t": 1, "s": "session", "m": "start"},
        {"t": 4, "l": "W", "s": "log", "m": "could not restore lighting after 12.5s suspend"},
        {"t": 5, "l": "W", "s": "log", "m": "could not restore lighting after 340.1s suspend"},
    ]
    problems = summarize(records)[0]["problems"]
    assert [(p["message"], p["count"]) for p in problems] == [
        ("could not restore lighting after #s suspend", 2),
    ]


def test_handler_copies_log_records_with_tracebacks(tmp_path):
    j = Journal(str(tmp_path))
    j.start()
    handler = JournalHandler(j)
    handler.emit(logging.makeLogRecord({"name": "root", "levelname": "INFO", "msg": "loaded %s", "args": ("v1",)}))
    try:
        raise ValueError("boom")
    except ValueError:
        handler.emit(logging.makeLogRecord({
            "name": "colores.ambilight", "levelname": "ERROR", "msg": "ambilight loop failed", "exc_info": sys.exc_info(),
        }))
    j.stop()
    records = _lines(str(tmp_path))
    assert (records[0]["s"], records[0]["m"], records[0].get("lg")) == ("log", "loaded v1", None)
    assert records[1]["l"] == "E" and records[1]["lg"] == "colores.ambilight"
    assert "ValueError: boom" in records[1]["m"]


def test_queued_handlers_keep_writing_and_are_restored(tmp_path):
    logger = logging.getLogger("colores-test-queue")
    logger.propagate = False
    logger.setLevel(logging.INFO)
    target = logging.FileHandler(str(tmp_path / "decky.log"))
    logger.addHandler(target)
    restore = queue_logger_handlers(logger)
    assert target not in logger.handlers
    logger.info("through the queue")
    restore()
    assert logger.handlers == [target]
    target.close()
    logger.removeHandler(target)
    assert "through the queue" in (tmp_path / "decky.log").read_text()


def _traced_journal(tmp_path, **options):
    j = Journal(str(tmp_path), coalesce_s=0, **options)
    j.start()
    journal.active = j
    return j


def _stop(j):
    journal.active = None
    j.stop()


def test_traced_calls_record_arguments_and_failures(tmp_path):
    class Plugin:
        async def set_solid(self, r, g, b):
            return None

        async def reconnect(self):
            return False

        async def install_update(self):
            return {"ok": False, "error": "network"}

        async def submit_report(self, categories, text):
            return {"ok": True}

        async def get_state(self):
            return {}

        async def check_update(self):
            return {}

        async def set_brightness(self, value):
            return await self.set_solid(value, value, value)

        async def explode(self):
            raise RuntimeError("no")

    trace_calls(Plugin, hidden_arguments=frozenset({"submit_report"}))
    j = _traced_journal(tmp_path)
    plugin = Plugin()
    try:
        asyncio.run(plugin.set_solid(255, 0, 0))
        asyncio.run(plugin.reconnect())
        asyncio.run(plugin.install_update())
        asyncio.run(plugin.submit_report(["color"], "private words"))
        asyncio.run(plugin.get_state())
        asyncio.run(plugin.check_update())
        asyncio.run(plugin.set_brightness(5))
        try:
            asyncio.run(plugin.explode())
        except RuntimeError:
            pass
    finally:
        _stop(j)
    records = _lines(str(tmp_path))
    assert [(r["m"], r.get("a"), r.get("r")) for r in records] == [
        ("set_solid", "[255,0,0]", None),
        ("reconnect", "[]", {"ok": False}),
        ("install_update", "[]", {"ok": False, "error": '"network"'}),
        ("submit_report", None, None),
        ("set_brightness", "[5]", None),
        ("explode", "[]", records[-1]["r"]),
    ]
    assert "private words" not in json.dumps(records)


def test_a_raising_call_records_where_it_failed(tmp_path):
    class Plugin:
        async def set_mode(self, mode):
            raise ValueError("bad mode")

    trace_calls(Plugin)
    j = _traced_journal(tmp_path)
    try:
        asyncio.run(Plugin().set_mode("x"))
    except ValueError:
        pass
    finally:
        _stop(j)
    failure = _lines(str(tmp_path))[0]["r"]
    assert failure["raised"] == "ValueError" and failure["message"] == "bad mode"
    assert "test_journal.py" in failure["where"] and "set_mode" in failure["where"]


def test_automatic_and_internal_calls_are_told_apart(tmp_path):
    class Plugin:
        async def prepare_suspend(self):
            return None

        async def reconnect(self):
            return True

        async def _resume(self):
            with journal.internal():
                return await self.reconnect()

    trace_calls(Plugin, automatic=frozenset({"prepare_suspend"}))
    j = _traced_journal(tmp_path)
    try:
        asyncio.run(Plugin().prepare_suspend())
        asyncio.run(Plugin()._resume())
        asyncio.run(Plugin().reconnect())
    finally:
        _stop(j)
    assert [(r["m"], r.get("auto")) for r in _lines(str(tmp_path))] == [
        ("prepare_suspend", True),
        ("reconnect", None),
    ]


def test_long_arguments_keep_what_changed():
    from journal import _summary

    changes = {
        "mode": "gradient",
        "gradient": [[i, i, i] for i in range(40)],
        "effect": {"id": "wave", "speed": 50, "use_gradient": True},
    }
    text = _summary(["global", None, changes])
    assert len(text) <= 400
    assert '"mode":"gradient"' in text and '"wave"' in text


def test_watchdog_reports_a_stuck_loop_once_and_its_recovery(tmp_path):
    now = [100.0]
    j = Journal(str(tmp_path), clock=lambda: 1_790_000_000.0)
    j.start()
    dog = LoopWatchdog(j, limit_s=3.0, clock=lambda: now[0])
    now[0] = 101.0
    dog.check()
    now[0] = 104.5
    dog.check()
    now[0] = 106.0
    dog.check()
    dog._last_beat = 106.0
    dog.check()
    j.stop()
    assert [(r["s"], r["m"]) for r in _lines(str(tmp_path))] == [("loop", "blocked"), ("loop", "recovered")]


def test_last_record_finds_the_newest_line_of_a_source(tmp_path):
    now = 1_790_000_000.0
    j = Journal(str(tmp_path), clock=lambda: now)
    _run(
        j,
        (("INFO", "context", "snapshot"), {"at": now - 10, "services": ["hhd"]}),
        (("INFO", "log", "x"), {"at": now - 5}),
        (("INFO", "context", "snapshot"), {"at": now - 2, "services": []}),
    )
    assert last_record(str(tmp_path), "context")["services"] == []
    assert last_record(str(tmp_path), "session") is None


def test_a_failed_led_write_keeps_its_errno_and_driver(tmp_path):
    j = _traced_journal(tmp_path)
    try:
        journal.write_failed("/sys/class/leds/x/multi_intensity", [(255, 0, 0)] * 4,
                             "[Errno 5] Input/output error", by="LedDevice")
        journal.write_failed("hid", "solid", "no led path", by="AllyHid")
        journal.write_failed("node", 1, "[Errno 2] No such file or directory", by="LedDevice")
    finally:
        _stop(j)
    first, second, third = _lines(str(tmp_path))
    assert (first["s"], first["m"], first["error"], first["by"]) == ("hw", "write_failed", "EIO", "LedDevice")
    assert first["detail"] == "[Errno 5] Input/output error" and len(first["value"]) <= 40
    assert (second["error"], "detail" in second) == ("no led path", False)
    assert third["error"] == "ENOENT"


def test_write_failed_without_a_journal_is_silent():
    journal.active = None
    journal.write_failed("x", 1, "[Errno 5] EIO", by="LedDevice")
