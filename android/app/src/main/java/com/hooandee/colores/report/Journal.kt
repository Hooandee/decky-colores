package com.hooandee.colores.report

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject

data class JournalLimits(
    val segmentBytes: Long = 512L * 1024,
    val totalBytes: Long = 8L * 1024 * 1024,
    val retentionDays: Long = 7,
    val queueCapacity: Int = 2048,
    val maxFieldChars: Int = 2048,
    val repeatWindowMs: Long = 60_000,
    val linesPerMinute: Int = 600,
    val recentBytes: Int = 400 * 1024,
    val olderBytes: Int = 300 * 1024,
)

class Journal(
    private val dir: File,
    private val limits: JournalLimits = JournalLimits(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val async: Boolean = true,
) {
    private val queue = LinkedBlockingQueue<JSONObject>(limits.queueCapacity)
    private val fileLock = Any()
    private val dropped = AtomicLong()
    private val throttled = AtomicLong()
    private val repeats = object : LinkedHashMap<String, Repeat>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Repeat>): Boolean = size > MAX_TRACKED_KEYS
    }
    private val states = HashMap<String, String>()
    private var minuteStart = 0L
    private var minuteLines = 0
    private var current: File? = null

    init {
        if (async) {
            Thread({
                prepare()
                drain()
            }, "colores-journal").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
                start()
            }
        } else {
            prepare()
        }
    }

    private fun prepare() {
        runCatching {
            dir.mkdirs()
            synchronized(fileLock) { prune(clock()) }
        }
    }

    fun session(fields: Map<String, Any?>) = enqueue("session", fields)

    fun log(
        level: String,
        tag: String,
        message: String,
        error: Throwable? = null,
    ) {
        val fields = mapOf("lvl" to level, "tag" to tag, "msg" to message, "err" to error?.describe())
        val folded = foldRepeat("log|$level|$tag|$message") ?: return
        enqueue("log", fields, folded)
    }

    fun action(
        name: String,
        fields: Map<String, Any?> = emptyMap(),
    ) = enqueue("action", fields + ("a" to name))

    fun state(
        key: String,
        value: Any?,
    ) {
        val rendered = value?.toString() ?: "null"
        synchronized(states) {
            if (states[key] == rendered) return
            states[key] = rendered
        }
        if (!enqueue("state", mapOf("k" to key, "v" to value))) synchronized(states) { states.remove(key) }
    }

    fun hardware(
        event: String,
        by: String,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        val folded = foldRepeat("hw|$event|$by|${fields["path"]}|${fields["err"]}") ?: return
        enqueue("hw", fields + mapOf("ev" to event, "by" to by), folded)
    }

    /** Written synchronously: the process is about to die and the writer thread may never run again. */
    fun crash(
        thread: String,
        error: Throwable,
    ) {
        val line =
            line(
                "crash",
                mapOf(
                    "thread" to thread,
                    "err" to error.describe(),
                    "stack" to error.stackTrace.take(MAX_STACK_FRAMES).joinToString("\n") { it.toString() },
                    "cause" to error.cause?.describe(),
                ),
            )
        runCatching { synchronized(fileLock) { append(line) } }
    }

    fun stats(): JSONObject {
        val files = segments()
        return JSONObject()
            .put("files", files.size)
            .put("bytes", files.sumOf { it.length() })
            .put("dropped", dropped.get())
            .put("throttled", throttled.get())
            .put("limits", JSONObject().put("segment", limits.segmentBytes).put("total", limits.totalBytes).put("days", limits.retentionDays))
    }

    fun export(): JSONObject {
        val now = clock()
        val lines =
            synchronized(fileLock) { segments().flatMap { file -> runCatching { file.readLines() }.getOrDefault(emptyList()) } }
                .mapNotNull { raw -> runCatching { JSONObject(raw) }.getOrNull() }
        val recentCutoff = now - DAY_MS
        val recent = lines.filter { it.optLong("t") >= recentCutoff }
        val older = lines.filter { it.optLong("t") < recentCutoff && it.isKeyLine() }
        return JSONObject()
            .put("summary", summarize(lines))
            .put("recent", tailWithin(recent, limits.recentBytes))
            .put("older", tailWithin(older, limits.olderBytes))
            .put("stats", stats())
    }

    private fun summarize(lines: List<JSONObject>): JSONArray {
        val sessions = JSONArray()
        var currentSession: JSONObject? = null
        for (line in lines) {
            when (line.optString("s")) {
                "session" -> {
                    currentSession =
                        JSONObject()
                            .put("t", line.optLong("t"))
                            .put("v", line.opt("v"))
                            .put("errors", 0)
                            .put("warnings", 0)
                            .put("hw_failed", 0)
                            .put("crashes", 0)
                    sessions.put(currentSession)
                }
                "log" ->
                    when (line.optString("lvl")) {
                        "E" -> currentSession?.increment("errors", line)
                        "W" -> currentSession?.increment("warnings", line)
                    }
                "hw" -> if (line.optString("ev") == "write_failed") currentSession?.increment("hw_failed", line)
                "crash" -> currentSession?.increment("crashes", line)
            }
        }
        return sessions
    }

    private fun JSONObject.increment(
        key: String,
        line: JSONObject,
    ) {
        put(key, optInt(key) + line.optInt("n", 1))
    }

    private fun tailWithin(
        lines: List<JSONObject>,
        budget: Int,
    ): JSONArray {
        val kept = ArrayDeque<String>()
        var used = 0
        for (line in lines.asReversed()) {
            val rendered = line.redacted().toString()
            if (used + rendered.length > budget) break
            used += rendered.length + 1
            kept.addFirst(rendered)
        }
        return JSONArray(kept.map(::JSONObject))
    }

    private fun JSONObject.redacted(): JSONObject {
        val copy = JSONObject(toString())
        keys().forEach { key -> (opt(key) as? String)?.let { copy.put(key, redactReportText(it)) } }
        return copy
    }

    private fun JSONObject.isKeyLine(): Boolean =
        when (optString("s")) {
            "session", "state", "hw", "crash", "action" -> true
            "log" -> optString("lvl") == "E" || optString("lvl") == "W"
            else -> false
        }

    private fun foldRepeat(key: String): Int? {
        val now = clock()
        synchronized(repeats) {
            val repeat = repeats[key]
            if (repeat == null || now - repeat.windowStart >= limits.repeatWindowMs) {
                repeats[key] = Repeat(now)
                return repeat?.suppressed ?: 0
            }
            if (++repeat.seen <= FOLD_AFTER) return 0
            repeat.suppressed++
            return null
        }
    }

    private fun enqueue(
        kind: String,
        fields: Map<String, Any?>,
        folded: Int = 0,
    ): Boolean =
        runCatching {
            val line = line(kind, if (folded > 0) fields + ("n" to folded + 1) else fields)
            if (!withinRate(line.optLong("t"))) {
                throttled.incrementAndGet()
                return@runCatching false
            }
            if (async) {
                queue.offer(line).also { if (!it) dropped.incrementAndGet() }
            } else {
                synchronized(fileLock) { append(line) }
                true
            }
        }.getOrDefault(false)

    private fun withinRate(now: Long): Boolean =
        synchronized(this) {
            if (now - minuteStart >= MINUTE_MS) {
                minuteStart = now
                minuteLines = 0
            }
            minuteLines++ < limits.linesPerMinute
        }

    private fun line(
        kind: String,
        fields: Map<String, Any?>,
    ): JSONObject {
        val json = JSONObject().put("t", clock()).put("s", kind)
        fields.forEach { (key, value) ->
            when (value) {
                null -> Unit
                is String -> json.put(key, value.take(limits.maxFieldChars))
                is Number, is Boolean -> json.put(key, value)
                else -> json.put(key, value.toString().take(limits.maxFieldChars))
            }
        }
        return json
    }

    private fun drain() {
        while (true) {
            val line = runCatching { queue.take() }.getOrNull() ?: continue
            runCatching { synchronized(fileLock) { append(line) } }
        }
    }

    private fun append(line: JSONObject) {
        val now = line.optLong("t", clock())
        val target = segmentFor(now)
        target.appendText(line.toString() + "\n")
    }

    private fun segmentFor(now: Long): File {
        val day = dayStamp(now)
        val active = current
        if (active != null && active.name.startsWith("$PREFIX$day") && active.length() < limits.segmentBytes) return active
        var index = 0
        var candidate = segmentFile(day, index)
        while (candidate.exists() && candidate.length() >= limits.segmentBytes) {
            index++
            candidate = segmentFile(day, index)
        }
        current = candidate
        prune(now)
        return candidate
    }

    private fun prune(now: Long) {
        val oldestKept = dayStamp(now - (limits.retentionDays - 1) * DAY_MS)
        val files = segments().toMutableList()
        files.filter { it.name.substring(PREFIX.length, PREFIX.length + 8) < oldestKept }.forEach {
            it.delete()
            files.remove(it)
        }
        var total = files.sumOf { it.length() }
        while (total > limits.totalBytes && files.size > 1) {
            val oldest = files.removeAt(0)
            if (oldest == current) break
            total -= oldest.length()
            oldest.delete()
        }
    }

    private fun segments(): List<File> =
        dir.listFiles { file -> SEGMENT.matches(file.name) }.orEmpty().sortedWith(compareBy({ it.name.substring(PREFIX.length, PREFIX.length + 8) }, { segmentIndex(it) }))

    private fun segmentIndex(file: File): Int = SEGMENT.matchEntire(file.name)?.groupValues?.get(2)?.toIntOrNull() ?: 0

    private fun segmentFile(
        day: String,
        index: Int,
    ): File = File(dir, if (index == 0) "$PREFIX$day.jsonl" else "$PREFIX$day.$index.jsonl")

    private fun dayStamp(epochMs: Long): String = DAY_FORMAT.format(Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate())

    private class Repeat(
        val windowStart: Long,
    ) {
        var seen = 1
        var suppressed = 0
    }

    companion object {
        private const val PREFIX = "colores-"
        private val SEGMENT = Regex("colores-(\\d{8})(?:\\.(\\d+))?\\.jsonl")
        private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val MINUTE_MS = 60_000L
        private const val MAX_TRACKED_KEYS = 256
        private const val MAX_STACK_FRAMES = 30
        private const val FOLD_AFTER = 3
    }
}

private fun Throwable.describe(): String = "${javaClass.simpleName}: ${message.orEmpty()}".take(500)
