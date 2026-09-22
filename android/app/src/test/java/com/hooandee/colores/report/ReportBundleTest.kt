package com.hooandee.colores.report

import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import com.hooandee.colores.device.learning.HardwareLearningStore
import com.hooandee.colores.device.learning.ProbeSnapshot
import com.hooandee.colores.device.learning.RollbackFailureReason
import com.hooandee.colores.device.learning.RollbackRecord
import com.hooandee.colores.device.learning.encodeLearningDescriptor
import com.hooandee.colores.device.learning.summary
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
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
            ambientStatus = "capturing",
            ambientCaptureFps = 10,
            ambientSamplingMode = "full_scene",
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
        assertEquals(10, bundle.getJSONObject("state").getJSONObject("ambient").getInt("capture_fps"))
        assertFalse(encoded.contains("pixels"))
        assertFalse(encoded.contains("ABC123456789"))
        assertFalse(encoded.contains("AA:BB:CC:DD:EE:FF"))
        assertFalse(encoded.contains("123e4567-e89b-12d3-a456-426614174000"))
    }

    @Test
    fun `redaction removes emails and phone numbers but keeps hardware values`() {
        val redacted =
            redactReportText(
                "Escríbeme a jane.doe+leds@example.co.uk o al 612 345 678, +34 699-123-456 y (555) 123-4567. " +
                    "multi_intensity=255 128 255 255 fingerprint=AYN/thor/kalama:13/TKQ1.221114.001/690123456:user/release-keys " +
                    "ts=1726990000000 ip=192.168.1.184 bus=3,address=0x3c",
            )

        assertFalse(redacted.contains("jane.doe"))
        assertFalse(redacted.contains("612 345 678"))
        assertFalse(redacted.contains("699-123-456"))
        assertFalse(redacted.contains("123-4567"))
        assertTrue(redacted.contains("[email]"))
        assertTrue(redacted.contains("[phone]"))
        assertTrue(redacted.contains("255 128 255 255"))
        assertTrue(redacted.contains("TKQ1.221114.001/690123456:user"))
        assertTrue(redacted.contains("1726990000000"))
        assertTrue(redacted.contains("192.168.1.184"))
        assertTrue(redacted.contains("bus=3,address=0x3c"))
    }

    @Test
    fun `every report carries inventory discovery facts and candidates`() {
        val candidate =
            com.hooandee.colores.device.learning.ProbeCandidate(
                cartridgeId = "android-sysfs-multicolor",
                cartridgeVersion = 1,
                surface = com.hooandee.colores.device.learning.ProbeSurface.SYSFS_RGB,
                descriptor =
                    com.hooandee.colores.led.SysfsRgbDescriptor(
                        "/sys/class/leds/private-ring",
                        2,
                        255,
                        com.hooandee.colores.led.SysfsColorKind.MULTI_INTENSITY_HEX,
                    ),
                signalKeys = setOf("color_kind"),
            )
        val fact =
            com.hooandee.colores.device.learning.HardwareFact(
                "controller.htr3212.topology",
                "unverified",
                com.hooandee.colores.device.learning.FactEvidence.OBSERVED,
                "android-detector",
            )
        val inventory =
            com.hooandee.colores.device.diagnostics.HardwareInventory(
                kernel = JSONObject().put("version", "5.15"),
                sysfs = JSONObject().put("leds", org.json.JSONArray().put(JSONObject().put("name", "rgb"))),
                hardware = JSONObject().put("build", JSONObject().put("model", "Thor")),
            )

        val bundle =
            buildReportBundleForSubmission(
                snapshot = snapshot,
                categories = listOf("color"),
                text = "No cambia",
                learningResults = emptyList(),
                diagnostics =
                    ReportDiagnostics(
                        detectionOutcome = "candidates",
                        facts = listOf(fact),
                        candidates = listOf(candidate),
                        inventory = inventory,
                    ),
            )

        assertEquals("5.15", bundle.getJSONObject("kernel").getString("version"))
        assertEquals("rgb", bundle.getJSONObject("sysfs").getJSONArray("leds").getJSONObject(0).getString("name"))
        assertEquals("Thor", bundle.getJSONObject("hardware").getJSONObject("build").getString("model"))
        val discovery = bundle.getJSONObject("discovery")
        assertEquals("candidates", discovery.getString("outcome"))
        assertEquals("unverified", discovery.getJSONArray("facts").getJSONObject(0).getString("value"))
        val reported = discovery.getJSONArray("candidates").getJSONObject(0)
        assertEquals("sysfs_rgb", reported.getString("surface"))
        assertEquals("sysfs:multi_intensity_hex", reported.getString("driver"))
        assertEquals(2, reported.getInt("zones"))
        assertFalse(discovery.toString().contains("private-ring"))
    }

    @Test
    fun `archived rollback journal reaches the report as a summary without snapshot values`() {
        val values = mutableMapOf<String, String>()
        val store = HardwareLearningStore(read = values::get, write = { key, value -> values[key] = value; true }, remove = { values.remove(it); true })
        store.saveRollback(
            RollbackRecord(
                sessionId = "session-1",
                cartridgeId = "singleadc-joypad",
                cartridgeVersion = 1,
                descriptorJson = encodeLearningDescriptor(SingleAdcJoypadDescriptor()),
                snapshot = ProbeSnapshot(mapOf("/sys/bus/platform/devices/singleadc-joypad/custum_rgb_r" to "RAW_SNAPSHOT_77")),
            ),
        )
        store.recordRollbackFailure(RollbackFailureReason.RESTORE_FAILED)
        store.recordRollbackFailure(RollbackFailureReason.RESTORE_FAILED)
        assertTrue(store.archiveRollback(RollbackFailureReason.RESTORE_FAILED, archivedAtEpochMs = 1_700_000_000_000L))
        val archive = requireNotNull(store.loadArchivedRollback()).summary()

        val bundle =
            buildReportBundleForSubmission(
                snapshot = snapshot,
                categories = listOf("learning"),
                text = "",
                learningResults = emptyList(),
                diagnostics = reportDiagnostics(null, null, archive),
            )
        val summary = bundle.getJSONObject("rollback_archive")
        val encoded = bundle.toString()

        assertEquals("hardware_learning", bundle.getString("report_kind"))
        assertEquals("restore_failed", summary.getString("reason"))
        assertEquals(2, summary.getInt("attempts"))
        assertEquals("singleadc-joypad", summary.getString("cartridge_id"))
        assertEquals(1, summary.getInt("cartridge_version"))
        assertEquals(1_700_000_000_000L, summary.getLong("archived_at"))
        assertEquals("singleadc_joypad", summary.getString("surface"))
        assertFalse(encoded.contains("RAW_SNAPSHOT_77"))
        assertFalse(encoded.contains("custum_rgb_r"))
        assertFalse(encoded.contains("session-1"))
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
