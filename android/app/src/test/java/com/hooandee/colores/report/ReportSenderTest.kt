package com.hooandee.colores.report

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSenderTest {
    private val bundle = JSONObject().put("schema", 1).put("app", "colores")
    private val pendingBundle = JSONObject().put("schema", 1).put("app", "colores").put("text", "pending")
    private val now = 10L * 24 * 60 * 60 * 1000

    @Test
    fun `successful send returns code without saving a local copy`() {
        var saved = false
        val sender =
            ReportSender(
                post = { HttpResponse(200, """{"ok":true,"code":"COL-900"}""") },
                save = { saved = true; "/tmp/report.json" },
                clear = {},
            )

        assertEquals(ReportResult.Success("COL-900", null), sender.submit(bundle))
        assertFalse(saved)
    }

    @Test
    fun `failed send preserves redacted bundle locally`() {
        var savedBundle: JSONObject? = null
        val sender =
            ReportSender(
                post = { throw IllegalStateException("offline") },
                save = { savedBundle = it; "/private/report-offline.json" },
                clear = {},
            )

        val result = sender.submit(bundle)

        assertEquals(bundle.toString(), savedBundle.toString())
        assertTrue(result is ReportResult.Failure)
        assertEquals("/private/report-offline.json", (result as ReportResult.Failure).savedPath)
    }

    @Test
    fun `next successful send also delivers the pending copy and clears it`() {
        val posted = mutableListOf<String>()
        var pending: PendingReport? = PendingReport(pendingBundle, now - 1_000)
        val sender =
            ReportSender(
                post = { posted += it.toString(); HttpResponse(200, """{"ok":true,"code":"COL-${posted.size}"}""") },
                save = { null },
                clear = { pending = null },
                load = { pending },
                nowEpochMs = { now },
            )

        assertEquals(ReportResult.Success("COL-1", null), sender.submit(bundle))
        assertEquals(2, posted.size)
        assertNull(pending)
    }

    @Test
    fun `opening reports resends a pending copy once and keeps it when still offline`() {
        var attempts = 0
        var cleared = false
        val sender =
            ReportSender(
                post = { attempts += 1; throw IllegalStateException("offline") },
                save = { null },
                clear = { cleared = true },
                load = { PendingReport(pendingBundle, now - 1_000) },
                nowEpochMs = { now },
            )

        assertTrue(sender.resendPending() is ReportResult.Failure)
        assertEquals(1, attempts)
        assertFalse(cleared)
    }

    @Test
    fun `pending copy older than seven days is discarded without sending`() {
        var attempts = 0
        var cleared = false
        val sender =
            ReportSender(
                post = { attempts += 1; HttpResponse(200, """{"ok":true,"code":"COL-1"}""") },
                save = { null },
                clear = { cleared = true },
                load = { PendingReport(pendingBundle, now - PENDING_MAX_AGE_MS - 1) },
                nowEpochMs = { now },
            )

        assertNull(sender.resendPending())
        assertEquals(0, attempts)
        assertTrue(cleared)
    }

    @Test
    fun `retrying the same saved report does not send it twice`() {
        var attempts = 0
        var pending: PendingReport? = PendingReport(bundle, now - 1_000)
        val sender =
            ReportSender(
                post = { attempts += 1; HttpResponse(200, """{"ok":true,"code":"COL-1"}""") },
                save = { null },
                clear = { pending = null },
                load = { pending },
                nowEpochMs = { now },
            )

        sender.submit(bundle)

        assertEquals(1, attempts)
        assertNull(pending)
    }

    @Test
    fun `stored copies from earlier releases still load`() {
        val legacy = decodePendingReport(bundle.toString(), fallbackSavedAtEpochMs = 42)
        val current = decodePendingReport(encodePendingReport(bundle, 7), fallbackSavedAtEpochMs = 42)

        assertEquals(bundle.toString(), legacy?.bundle.toString())
        assertEquals(42L, legacy?.savedAtEpochMs)
        assertEquals(bundle.toString(), current?.bundle.toString())
        assertEquals(7L, current?.savedAtEpochMs)
    }
}
