package com.hooandee.colores.report

import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportBundleTest {
    private val snapshot =
        AndroidReportSnapshot(
            appVersion = "0.1.0",
            manufacturer = "AYN",
            model = "Thor",
            androidRelease = "13",
            sdk = 33,
            deviceId = "ayn-thor",
            deviceName = "AYN Thor",
            driver = "htr3212",
            transport = "pserver",
            color = true,
            brightness = true,
            perZone = true,
            zones = 8,
            controlStatus = "enabled",
            mode = "EFFECT",
            brightnessValue = 72,
            power = true,
            configuredProfiles = 2,
            automationStatus = "active",
        )

    @Test
    fun `bundle contains Android diagnostics without raw identifiers`() {
        val bundle =
            buildReportBundle(
                snapshot =
                    snapshot.copy(
                        deviceId = "123e4567-e89b-12d3-a456-426614174000",
                        deviceName = "AYN Thor AA:BB:CC:DD:EE:FF",
                    ),
                categories = listOf("effects"),
                text = "Serial number: ABC123456789 and AA:BB:CC:DD:EE:FF",
            )
        val encoded = bundle.toString()

        assertEquals(1, bundle.getInt("schema"))
        assertEquals("colores", bundle.getString("app"))
        assertEquals("android", bundle.getJSONObject("environment").getString("platform"))
        assertEquals("0.1.0", bundle.getJSONObject("environment").getString("plugin_version"))
        assertEquals("htr3212", bundle.getJSONObject("capabilities").getString("driver"))
        assertEquals(2, bundle.getJSONObject("stores").getInt("profiles_configured"))
        assertFalse(encoded.contains("ABC123456789"))
        assertFalse(encoded.contains("AA:BB:CC:DD:EE:FF"))
        assertFalse(encoded.contains("123e4567-e89b-12d3-a456-426614174000"))
    }

    @Test
    fun `payload is gzip envelope compatible with the report service`() {
        val bundle = buildReportBundle(snapshot, listOf("profiles"), "No cambia de perfil")

        val envelope = encodeReportPayload(bundle)
        val raw = Base64.getDecoder().decode(envelope.getString("payload"))
        val decoded = GZIPInputStream(ByteArrayInputStream(raw)).bufferedReader().use { it.readText() }

        assertEquals("colores", envelope.getString("app"))
        assertEquals("gzip", envelope.getString("enc"))
        assertEquals(bundle.toString(), JSONObject(decoded).toString())
    }

    @Test
    fun `service response exposes COL code and rejects malformed success`() {
        assertEquals(
            ReportResult.Success("COL-123", "https://example.test/issue/1"),
            parseReportResponse(201, """{"ok":true,"code":"COL-123","issueUrl":"https://example.test/issue/1"}"""),
        )
        assertTrue(parseReportResponse(200, """{"ok":true}""") is ReportResult.Failure)
        assertTrue(parseReportResponse(503, """{"error":"unavailable"}""") is ReportResult.Failure)
    }
}
