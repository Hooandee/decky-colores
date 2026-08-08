package com.hooandee.colores.report

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSenderTest {
    private val bundle = JSONObject().put("schema", 1).put("app", "colores")

    @Test
    fun `successful send returns code without saving a local copy`() {
        var saved = false
        val sender =
            ReportSender(
                post = { HttpResponse(200, """{"ok":true,"code":"COL-900"}""") },
                save = { saved = true; "/tmp/report.json" },
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
            )

        val result = sender.submit(bundle)

        assertEquals(bundle.toString(), savedBundle.toString())
        assertTrue(result is ReportResult.Failure)
        assertEquals("/private/report-offline.json", (result as ReportResult.Failure).savedPath)
    }
}
