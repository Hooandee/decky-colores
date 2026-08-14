package com.hooandee.colores.report

import com.hooandee.colores.device.DeviceCapabilities
import com.hooandee.colores.device.learning.EvidenceLevel
import com.hooandee.colores.device.learning.HardwareLearningResult
import com.hooandee.colores.device.learning.HardwareFact
import com.hooandee.colores.device.learning.FactEvidence
import com.hooandee.colores.device.learning.HardwareLearningStatus
import com.hooandee.colores.device.learning.ProbeCandidate
import com.hooandee.colores.device.learning.ProbeEvidence
import com.hooandee.colores.device.learning.ProbeStep
import com.hooandee.colores.device.learning.ProbeSurface
import com.hooandee.colores.device.learning.RollbackStatus
import com.hooandee.colores.device.learning.UserObservation
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareLearningReportTest {
    private val snapshot =
        AndroidReportSnapshot(
            appVersion = "0.1.0",
            manufacturer = "Unknown",
            model = "Mystery",
            androidRelease = "13",
            sdk = 33,
            deviceId = null,
            deviceName = null,
            driver = null,
            transport = null,
            color = false,
            brightness = false,
            perZone = false,
            zones = 0,
            controlStatus = "service_unavailable",
            mode = "COLOR",
            brightnessValue = 100,
            power = true,
            configuredProfiles = 0,
            automationStatus = "disabled",
        )

    @Test
    fun `blocked learning bundle requests high severity without routes values or commands`() {
        val candidate =
            ProbeCandidate(
                cartridgeId = "android-sysfs-multicolor",
                cartridgeVersion = 1,
                surface = ProbeSurface.SYSFS_RGB,
                descriptor = SysfsRgbDescriptor("/sys/class/leds/private-ring", 3, 255, SysfsColorKind.MULTI_INTENSITY_HEX),
                signalKeys = setOf("observed_index_count"),
            )
        val result =
            HardwareLearningResult(
                status = HardwareLearningStatus.BLOCKED,
                candidate = candidate,
                evidence = listOf(ProbeEvidence(ProbeStep.COLOR, null, EvidenceLevel.NOT_OBSERVED, UserObservation.NO)),
                capabilities = DeviceCapabilities(false, false, false, 1),
                rollbackStatus = RollbackStatus.RESTORED_AND_READ_BACK,
            )

        val bundle = buildHardwareLearningBundle(snapshot, listOf(result), "Las luces no respondieron")
        val raw = bundle.toString()

        assertEquals("hardware_learning", bundle.getString("report_kind"))
        assertEquals("high", bundle.getJSONObject("triage").getString("severity"))
        assertTrue(bundle.getJSONObject("triage").getJSONArray("requested_labels").toString().contains("compatibility-blocked"))
        assertFalse(raw.contains("/sys/"))
        assertFalse(raw.contains("private-ring"))
        assertFalse(raw.contains("secret-value"))
        assertFalse(raw.contains("settings put"))
    }

    @Test
    fun `restore failure is critical and candidate learning requests candidate label`() {
        val failed = result(HardwareLearningStatus.RESTORE_FAILED, RollbackStatus.RESTORE_FAILED)
        val adapted = result(HardwareLearningStatus.ADAPTED, RollbackStatus.RESTORED_AND_READ_BACK)

        assertEquals("critical", buildHardwareLearningBundle(snapshot, listOf(failed), "Restore failed").getJSONObject("triage").getString("severity"))
        assertTrue(
            buildHardwareLearningBundle(snapshot, listOf(adapted), "It worked")
                .getJSONObject("triage")
                .getJSONArray("requested_labels")
                .toString()
                .contains("compatibility-candidate"),
        )
    }

    @Test
    fun `startup restore failure remains reportable before any cartridge completes`() {
        val bundle =
            buildHardwareLearningBundle(
                snapshot = snapshot,
                results = emptyList(),
                text = "Startup restoration failed",
                forcedRestoreFailure = true,
            )

        assertEquals("critical", bundle.getJSONObject("triage").getString("severity"))
        assertTrue(bundle.getJSONObject("triage").getJSONArray("requested_labels").toString().contains("restore-failed"))
    }

    @Test
    fun `submission routes startup restore failure through learning bundle`() {
        val bundle =
            buildReportBundleForSubmission(
                snapshot = snapshot,
                categories = listOf("learning"),
                text = "Startup restoration failed",
                learningResults = emptyList(),
                restoreFailure = true,
            )

        assertEquals("hardware_learning", bundle.getString("report_kind"))
        assertEquals("critical", bundle.getJSONObject("triage").getString("severity"))
    }

    @Test
    fun `submission routes unavailable journal through a critical learning bundle`() {
        val bundle =
            buildReportBundleForSubmission(
                snapshot = snapshot,
                categories = listOf("learning"),
                text = "Journal could not be cleared",
                learningResults = emptyList(),
                criticalSafetyFailure = true,
            )

        assertEquals("hardware_learning", bundle.getString("report_kind"))
        assertEquals("critical", bundle.getJSONObject("triage").getString("severity"))
        assertTrue(bundle.getJSONObject("triage").getJSONArray("requested_labels").toString().contains("safety-failed"))
    }

    @Test
    fun `learning bundle includes only structured facts emitted by bundled cartridges`() {
        val fact = HardwareFact("controller.htr3212.left", "bus=3,address=0x3c", FactEvidence.OBSERVED, "android-i2c-htr3212")

        val bundle =
            buildHardwareLearningBundle(
                snapshot = snapshot,
                results = listOf(result(HardwareLearningStatus.BLOCKED, RollbackStatus.RESTORED_AND_READ_BACK)),
                text = "Need another cartridge",
                facts = listOf(fact),
            )
        val reported = bundle.getJSONObject("learning").getJSONArray("discovery_facts").getJSONObject(0)

        assertEquals("controller.htr3212.left", reported.getString("key"))
        assertEquals("bus=3,address=0x3c", reported.getString("value"))
        assertEquals("observed", reported.getString("evidence"))
        assertFalse(bundle.toString().contains("/sys/"))
    }

    private fun result(
        status: HardwareLearningStatus,
        rollback: RollbackStatus,
    ): HardwareLearningResult =
        HardwareLearningResult(
            status = status,
            candidate =
                ProbeCandidate(
                    "singleadc-joypad",
                    1,
                    ProbeSurface.SINGLEADC_JOYPAD,
                    com.hooandee.colores.led.SingleAdcJoypadDescriptor(),
                    setOf("singleadc_surface"),
                ),
            evidence = listOf(ProbeEvidence(ProbeStep.COLOR, null, EvidenceLevel.USER_CONFIRMED, UserObservation.YES)),
            capabilities = DeviceCapabilities(status == HardwareLearningStatus.ADAPTED, false, false, 1),
            rollbackStatus = rollback,
        )
}
