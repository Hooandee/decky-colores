package com.hooandee.colores.report

import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalTest {
    private val dir: File = Files.createTempDirectory("journal").toFile()
    private var now = DAY * 20_000 + 12 * HOUR

    private fun journal(limits: JournalLimits = JournalLimits()) =
        Journal(dir, limits, clock = { now }, zone = ZoneOffset.UTC, async = false)

    private fun files() = dir.listFiles().orEmpty().map { it.name }.sorted()

    @Test
    fun rotatesSegmentsBySize() {
        val journal = journal(JournalLimits(segmentBytes = 400, linesPerMinute = 10_000))
        repeat(20) { journal.action("tap", mapOf("i" to it)) }

        assertTrue(files().size > 1)
        assertTrue(dir.listFiles()!!.all { it.length() < 400 + 200 })
    }

    @Test
    fun deletesSegmentsOlderThanRetention() {
        File(dir, "colores-19700101.jsonl").writeText("{}\n")
        val old = journal()
        old.action("old")
        now += 8 * DAY
        journal().action("new")

        assertEquals(1, files().size)
        assertFalse(files().contains("colores-19700101.jsonl"))
    }

    @Test
    fun keepsTotalSizeUnderCap() {
        val journal = journal(JournalLimits(segmentBytes = 300, totalBytes = 1_000, linesPerMinute = 10_000))
        repeat(200) { journal.action("tap", mapOf("i" to it)) }

        assertTrue(dir.listFiles()!!.sumOf { it.length() } <= 1_000 + 300)
    }

    @Test
    fun foldsRepeatedLogLinesInsideWindow() {
        val journal = journal()
        repeat(50) { journal.log("E", "tag", "same failure") }
        now += 61_000
        journal.log("E", "tag", "same failure")

        val lines = dir.listFiles()!!.flatMap { it.readLines() }
        assertEquals(4, lines.size)
        assertTrue(lines.last().contains("\"n\":48"))
    }

    @Test
    fun writesStateOnlyOnChange() {
        val journal = journal()
        journal.state("mode", "COLOR")
        journal.state("mode", "COLOR")
        journal.state("mode", "AMBIENT")

        assertEquals(2, dir.listFiles()!!.sumOf { it.readLines().size })
    }

    @Test
    fun throttlesBurstsAboveRate() {
        val journal = journal(JournalLimits(linesPerMinute = 10))
        repeat(30) { journal.action("tap", mapOf("i" to it)) }

        assertEquals(10, dir.listFiles()!!.sumOf { it.readLines().size })
        assertEquals(20L, journal.stats().getLong("throttled"))
    }

    @Test
    fun exportsRecentDayInFullAndOnlyKeyLinesForOlderDays() {
        val journal = journal(JournalLimits(linesPerMinute = 10_000))
        journal.session(mapOf("v" to "0.1.0"))
        journal.log("I", "tag", "old info")
        journal.log("E", "tag", "old error")
        now += 2 * DAY
        journal.session(mapOf("v" to "0.2.0"))
        journal.log("I", "tag", "recent info")
        journal.hardware("write_failed", "sysfs", mapOf("path" to "/sys/class/leds/x/brightness"))

        val export = journal.export()
        val older = export.getJSONArray("older").toString()
        val recent = export.getJSONArray("recent").toString()
        assertFalse(older.contains("old info"))
        assertTrue(older.contains("old error"))
        assertTrue(recent.contains("recent info"))
        assertTrue(recent.contains("/sys/class/leds/x/brightness"))
        val sessions = export.getJSONArray("summary")
        assertEquals(2, sessions.length())
        assertEquals(1, sessions.getJSONObject(0).getInt("errors"))
        assertEquals(1, sessions.getJSONObject(1).getInt("hw_failed"))
    }

    @Test
    fun exportKeepsNewestLinesWithinBudget() {
        val journal = journal(JournalLimits(recentBytes = 300, linesPerMinute = 10_000))
        repeat(40) { journal.action("tap", mapOf("i" to it)) }

        val recent = journal.export().getJSONArray("recent")
        assertTrue(recent.length() in 1 until 40)
        assertEquals(39, recent.getJSONObject(recent.length() - 1).getInt("i"))
    }

    @Test
    fun crashIsWrittenSynchronouslyEvenWhenAsync() {
        val journal = Journal(dir, clock = { now }, zone = ZoneOffset.UTC, async = true)
        journal.crash("main", IllegalStateException("boom"))

        val text = dir.listFiles()!!.joinToString { it.readText() }
        assertTrue(text.contains("\"s\":\"crash\""))
        assertTrue(text.contains("boom"))
    }

    @Test
    fun asyncWriterPersistsQueuedLines() {
        val journal = Journal(dir, clock = { now }, zone = ZoneOffset.UTC, async = true)
        journal.session(mapOf("v" to "0.1.0"))
        journal.action("tap")

        val deadline = System.currentTimeMillis() + 2_000
        while (dir.listFiles()!!.sumOf { it.readLines().size } < 2 && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertEquals(2, dir.listFiles()!!.sumOf { it.readLines().size })
    }

    @Test
    fun exportRedactsPersonalData() {
        val journal = journal()
        journal.log("W", "tag", "contact someone@example.com")

        assertFalse(journal.export().toString().contains("someone@example.com"))
    }

    @Test
    fun writeHealthJournalsOnlyTransitions() {
        val journal = journal()
        ColoresLog.journal = journal
        try {
            val health = WriteHealth("sysfs")
            repeat(5) { health.failed("EACCES") }
            repeat(5) { health.succeeded() }

            val lines = dir.listFiles()!!.flatMap { it.readLines() }
            assertEquals(2, lines.size)
            assertTrue(lines[0].contains("write_failed"))
            assertTrue(lines[1].contains("write_recovered") && lines[1].contains("\"failures\":5"))
        } finally {
            ColoresLog.journal = null
        }
    }

    private companion object {
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR
    }
}
