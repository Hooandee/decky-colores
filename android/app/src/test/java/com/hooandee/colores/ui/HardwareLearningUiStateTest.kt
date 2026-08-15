package com.hooandee.colores.ui

import com.hooandee.colores.device.learning.EvidenceLevel
import com.hooandee.colores.device.learning.DetectionOutcome
import com.hooandee.colores.device.learning.HardwareLearningResult
import com.hooandee.colores.device.learning.HardwareLearningState
import com.hooandee.colores.device.learning.HardwareLearningStatus
import com.hooandee.colores.device.learning.LearningBlockReason
import com.hooandee.colores.device.learning.ProbeCandidate
import com.hooandee.colores.device.learning.ProbeEvidence
import com.hooandee.colores.device.learning.ProbeStep
import com.hooandee.colores.device.learning.ProbeSurface
import com.hooandee.colores.device.learning.RollbackStatus
import com.hooandee.colores.device.learning.UserObservation
import com.hooandee.colores.device.learning.ZoneLocation
import com.hooandee.colores.device.DeviceCapabilities
import com.hooandee.colores.device.DetectedAndroidDevice
import com.hooandee.colores.device.GenericVendorLed
import com.hooandee.colores.led.Htr3212Descriptor
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareLearningUiStateTest {
    private val candidate =
        ProbeCandidate(
            "android-sysfs-multicolor",
            1,
            ProbeSurface.SYSFS_RGB,
            SysfsRgbDescriptor("/sys/class/leds/gamepad", 2, 255, SysfsColorKind.MULTI_INTENSITY_HEX),
            emptySet(),
        )

    @Test
    fun `color is always the first visible verification`() {
        val state = HardwareLearningState.Ready(candidate, listOf(ProbeStep.COLOR, ProbeStep.ZONE), emptyList())

        assertEquals(ProbeRequest(ProbeStep.COLOR, null), HardwareLearningUiState(sessionState = state).nextProbe)
    }

    @Test
    fun `action layout keeps the active decision in the fixed footer`() {
        val consent = HardwareLearningUiState(sessionState = HardwareLearningState.ConsentRequired(candidate))
        val ready = HardwareLearningUiState(sessionState = HardwareLearningState.Ready(candidate, listOf(ProbeStep.COLOR), emptyList()))
        val awaiting =
            HardwareLearningUiState(
                sessionState =
                    HardwareLearningState.AwaitingAnswer(
                        candidate,
                        listOf(ProbeStep.COLOR),
                        ProbeStep.COLOR,
                        null,
                        emptyList(),
                    ),
            )

        assertEquals(HardwareLearningActionLayout.CONSENT, consent.actionLayout)
        assertEquals(HardwareLearningActionLayout.RUN_PROBE, ready.actionLayout)
        assertEquals(HardwareLearningActionLayout.OBSERVATION, awaiting.actionLayout)
    }

    @Test
    fun `zone prompts advance one confirmed position at a time after color`() {
        val evidence =
            listOf(
                ProbeEvidence(ProbeStep.COLOR, null, EvidenceLevel.TRANSPORT_CONFIRMED, null),
                ProbeEvidence(ProbeStep.COLOR, null, EvidenceLevel.USER_CONFIRMED, UserObservation.YES),
                ProbeEvidence(ProbeStep.ZONE, 0, EvidenceLevel.TRANSPORT_CONFIRMED, null),
                ProbeEvidence(ProbeStep.ZONE, 0, EvidenceLevel.USER_CONFIRMED, UserObservation.YES),
            )
        val state = HardwareLearningState.Ready(candidate, listOf(ProbeStep.COLOR, ProbeStep.ZONE), evidence)

        assertEquals(ProbeRequest(ProbeStep.ZONE, 1), HardwareLearningUiState(sessionState = state).nextProbe)
    }

    @Test
    fun `whole joystick answer follows the stick under test`() {
        assertEquals(ZoneLocation.LEFT_WHOLE, wholeStickLocation(zone = 0))
        assertEquals(ZoneLocation.LEFT_WHOLE, wholeStickLocation(zone = 3))
        assertEquals(ZoneLocation.RIGHT_WHOLE, wholeStickLocation(zone = 4))
        assertEquals(ZoneLocation.RIGHT_WHOLE, wholeStickLocation(zone = 7))
    }

    @Test
    fun `grouped HTR lights end the multipoint route without seven redundant probes`() {
        val descriptor =
            GenericVendorLed.descriptor(8).copy(
                driver = "htr3212",
                htr3212 = Htr3212Descriptor(1, 0, 0x3c, listOf(0, 1, 3, 2), listOf(1, 2, 3, 0)),
            )
        val htrCandidate = ProbeCandidate("htr3212-multipoint", 2, ProbeSurface.HTR3212, descriptor, emptySet())
        val evidence =
            listOf(
                ProbeEvidence(ProbeStep.COLOR, null, EvidenceLevel.USER_CONFIRMED, UserObservation.YES),
                ProbeEvidence(ProbeStep.ZONE, 0, EvidenceLevel.USER_CONFIRMED, UserObservation.YES, ZoneLocation.LEFT_WHOLE),
            )
        val state = HardwareLearningState.Ready(htrCandidate, listOf(ProbeStep.COLOR, ProbeStep.ZONE), evidence)
        val ui = HardwareLearningUiState(sessionState = state)

        assertNull(ui.nextProbe)
        assertTrue(ui.canFinish)
    }

    @Test
    fun `negative color answer ends the cartridge without probing more controls`() {
        val evidence =
            listOf(
                ProbeEvidence(ProbeStep.COLOR, null, EvidenceLevel.NOT_OBSERVED, UserObservation.NO),
            )
        val state = HardwareLearningState.Ready(candidate, listOf(ProbeStep.COLOR, ProbeStep.ZONE), evidence)

        assertNull(HardwareLearningUiState(sessionState = state).nextProbe)
        assertTrue(HardwareLearningUiState(sessionState = state).canFinish)
    }

    @Test
    fun `last blocked result exposes severe report and no adaptation action`() {
        val result =
            HardwareLearningResult(
                status = HardwareLearningStatus.BLOCKED,
                candidate = candidate,
                evidence = emptyList(),
                capabilities = DeviceCapabilities(false, false, false, 1),
                rollbackStatus = RollbackStatus.RESTORED_AND_READ_BACK,
            )
        val ui = HardwareLearningUiState(sessionState = HardwareLearningState.Complete(result), candidateIndex = 1, candidateCount = 2)

        assertTrue(ui.showBlockedReport)
        assertFalse(ui.hasNextCandidate)
    }

    @Test
    fun `completed failed learning remains visible and reportable after dismiss`() {
        val result =
            HardwareLearningResult(
                status = HardwareLearningStatus.BLOCKED,
                candidate = candidate,
                evidence = emptyList(),
                capabilities = DeviceCapabilities(false, false, false, 1),
                rollbackStatus = RollbackStatus.RESTORED_AND_READ_BACK,
            )
        val dismissed =
            dismissedHardwareLearningUiState(
                HardwareLearningUiState(
                    dialogOpen = true,
                    sessionState = HardwareLearningState.Complete(result),
                    results = listOf(result),
                ),
            )
        val state =
            ColoresUiState(
                detectionOutcome =
                    DetectionOutcome.Candidates(
                        com.hooandee.colores.device.AndroidDeviceIdentity("Odin2 Portal", "kalama", "AYN", emptyMap()),
                        listOf(candidate),
                    ),
                hardwareLearning = dismissed,
            )

        assertFalse(dismissed.dialogOpen)
        assertTrue(dismissed.autoPromptDismissed)
        assertEquals(listOf(result), dismissed.results)
        assertTrue(state.hardwareLearningNeedsReport)
        assertTrue(dismissed.canOpenReport)
    }

    @Test
    fun `main UI exposes learning only when detection returned safe candidates`() {
        val identity = com.hooandee.colores.device.AndroidDeviceIdentity("Mystery", "mystery", "Maker", emptyMap())
        val state = ColoresUiState(detectionOutcome = DetectionOutcome.Candidates(identity, listOf(candidate)))

        assertTrue(state.hasHardwareLearningCandidates)
        assertEquals(listOf(candidate), state.hardwareLearningCandidates)
        assertFalse(ColoresUiState().hasHardwareLearningCandidates)
    }

    @Test
    fun `first unverified hardware route opens discovery automatically`() {
        val identity = com.hooandee.colores.device.AndroidDeviceIdentity("Retroid Pocket 5", "kona", "Retroid", emptyMap())
        val state =
            ColoresUiState(
                loading = false,
                detectionOutcome = DetectionOutcome.Candidates(identity, listOf(candidate)),
            )

        assertTrue(shouldAutomaticallyOpenHardwareLearning(state))
        assertFalse(shouldAutomaticallyOpenHardwareLearning(state.copy(hardwareLearning = state.hardwareLearning.copy(dialogOpen = true))))
    }

    @Test
    fun `dismissing the automatic prompt prevents it from reopening on refresh`() {
        val identity = com.hooandee.colores.device.AndroidDeviceIdentity("Retroid Pocket 5", "kona", "Retroid", emptyMap())
        val state =
            ColoresUiState(
                loading = false,
                detectionOutcome = DetectionOutcome.Candidates(identity, listOf(candidate)),
                hardwareLearning = HardwareLearningUiState(autoPromptDismissed = true),
            )

        assertFalse(shouldAutomaticallyOpenHardwareLearning(state))
    }

    @Test
    fun `completed discovery does not reopen on every refresh`() {
        val identity = com.hooandee.colores.device.AndroidDeviceIdentity("Retroid Pocket 5", "kona", "Retroid", emptyMap())
        val result =
            HardwareLearningResult(
                status = HardwareLearningStatus.CANCELLED,
                candidate = candidate,
                evidence = emptyList(),
                capabilities = DeviceCapabilities(false, false, false, 1),
                rollbackStatus = RollbackStatus.RESTORED_AND_READ_BACK,
            )
        val state =
            ColoresUiState(
                loading = false,
                detectionOutcome = DetectionOutcome.Candidates(identity, listOf(candidate)),
                hardwareLearning = HardwareLearningUiState(results = listOf(result)),
            )

        assertFalse(shouldAutomaticallyOpenHardwareLearning(state))
    }

    @Test
    fun `confirmed two-zone fallback recommends the multipoint test`() {
        val result =
            HardwareLearningResult(
                status = HardwareLearningStatus.ADAPTED,
                candidate = candidate,
                evidence = emptyList(),
                capabilities = DeviceCapabilities(true, true, true, 2),
                rollbackStatus = RollbackStatus.RESTORED_AND_READ_BACK,
            )
        val ui =
            HardwareLearningUiState(
                sessionState = HardwareLearningState.Complete(result),
                candidateIndex = 0,
                candidateCount = 2,
            )

        assertTrue(ui.confirmedTwoZoneFallback)
        assertFalse(ui.copy(candidateCount = 1).confirmedTwoZoneFallback)
    }

    @Test
    fun `grouped multipoint result explains that the confirmed two-zone fallback remains active`() {
        val fallback =
            HardwareLearningResult(
                status = HardwareLearningStatus.ADAPTED,
                candidate = candidate,
                evidence = emptyList(),
                capabilities = DeviceCapabilities(true, false, true, 2),
                rollbackStatus = RollbackStatus.RESTORED_AND_READ_BACK,
            )
        val descriptor =
            GenericVendorLed.descriptor(8).copy(
                driver = "htr3212",
                htr3212 = Htr3212Descriptor(1, 0, 0x3c, listOf(0, 1, 3, 2), listOf(1, 2, 3, 0)),
            )
        val htrCandidate = ProbeCandidate("htr3212-multipoint", 2, ProbeSurface.HTR3212, descriptor, emptySet())
        val grouped =
            HardwareLearningResult(
                status = HardwareLearningStatus.BLOCKED,
                candidate = htrCandidate,
                evidence =
                    listOf(
                        ProbeEvidence(
                            ProbeStep.ZONE,
                            0,
                            EvidenceLevel.USER_CONFIRMED,
                            UserObservation.YES,
                            ZoneLocation.LEFT_WHOLE,
                        ),
                    ),
                capabilities = DeviceCapabilities(true, false, false, 1),
                rollbackStatus = RollbackStatus.RESTORED_WITHOUT_HARDWARE_READBACK,
            )
        val ui =
            HardwareLearningUiState(
                sessionState = HardwareLearningState.Complete(grouped),
                candidateIndex = 1,
                candidateCount = 2,
                results = listOf(fallback, grouped),
            )

        assertTrue(ui.groupedMultipointWithFallback)
    }

    @Test
    fun `calibrated HTR binding is not offered again after rediscovery`() {
        val identity = com.hooandee.colores.device.AndroidDeviceIdentity("Odin2 Portal", "kalama", "AYN", emptyMap())
        val observedDescriptor =
            GenericVendorLed.descriptor(8).copy(
                driver = "htr3212",
                htr3212 = Htr3212Descriptor(3, 5, 0x3c, listOf(0, 1, 2, 3), listOf(0, 1, 2, 3), 0x0d),
            )
        val calibratedDescriptor =
            observedDescriptor.copy(
                htr3212 = observedDescriptor.htr3212?.copy(leftOrder = listOf(1, 3, 0, 2), rightOrder = listOf(3, 2, 1, 0)),
            )
        val htrCandidate = ProbeCandidate("htr3212-multipoint", 2, ProbeSurface.HTR3212, observedDescriptor, emptySet())
        val learned =
            DetectedAndroidDevice(
                id = "learned-htr3212",
                friendlyName = "AYN Odin 2 Portal",
                capabilities = DeviceCapabilities(true, false, true, 8),
                led = calibratedDescriptor,
                previewProfileId = null,
                previewCalibration = null,
            )
        val state =
            ColoresUiState(
                detected = learned,
                detectionOutcome = DetectionOutcome.Resolved(identity, learned, listOf(htrCandidate)),
                learnedHardware = true,
            )

        assertFalse(state.hasHardwareLearningCandidates)
    }

    @Test
    fun `startup restore failure can open a critical report without results`() {
        val ui = HardwareLearningUiState(restoreFailure = true)

        assertTrue(ui.canOpenReport)
        assertFalse(ui.canDismiss)
    }

    @Test
    fun `failed cancellation remains visible as a critical restore failure`() {
        assertTrue(
            hardwareLearningCancellationFailed(
                RollbackStatus.RESTORE_FAILED,
                HardwareLearningState.Blocked(LearningBlockReason.RESTORE_FAILED),
            ),
        )
        assertTrue(
            hardwareLearningCancellationFailed(
                RollbackStatus.RESTORED_AND_READ_BACK,
                HardwareLearningState.Blocked(LearningBlockReason.JOURNAL_UNAVAILABLE),
            ),
        )
        assertFalse(hardwareLearningCancellationFailed(RollbackStatus.RESTORED_AND_READ_BACK, HardwareLearningState.Idle))
        assertFalse(
            hardwareLearningRestoreFailed(
                RollbackStatus.RESTORED_AND_READ_BACK,
                HardwareLearningState.Blocked(LearningBlockReason.JOURNAL_UNAVAILABLE),
            ),
        )
        assertTrue(
            hardwareLearningRestoreFailed(
                RollbackStatus.RESTORE_FAILED,
                HardwareLearningState.Blocked(LearningBlockReason.RESTORE_FAILED),
            ),
        )
    }

    @Test
    fun `unavailable journal exposes a critical report and cannot be dismissed`() {
        val ui = HardwareLearningUiState(sessionState = HardwareLearningState.Blocked(LearningBlockReason.JOURNAL_UNAVAILABLE))

        assertEquals(LearningBlockReason.JOURNAL_UNAVAILABLE, ui.criticalBlockReason)
        assertTrue(ui.canOpenReport)
        assertFalse(ui.canDismiss)
    }
}
