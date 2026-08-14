package com.hooandee.colores.device

import com.hooandee.colores.device.learning.DetectionOutcome
import com.hooandee.colores.device.learning.ProbeCandidate
import com.hooandee.colores.device.learning.ProbeSurface
import com.hooandee.colores.device.learning.resolveDetectionOutcome
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDeviceDetectorTest {
    private val identity = AndroidDeviceIdentity("Unknown Handheld", "unknown", "Unknown", emptyMap())
    private val candidate =
        ProbeCandidate(
            cartridgeId = "singleadc-joypad",
            cartridgeVersion = 1,
            surface = ProbeSurface.SINGLEADC_JOYPAD,
            descriptor = SingleAdcJoypadDescriptor("/sys/bus/platform/devices/singleadc-joypad"),
            signalKeys = setOf("singleadc_surface"),
        )
    private val exact =
        DetectedAndroidDevice(
            id = "ayn-thor",
            friendlyName = "AYN Thor",
            capabilities = DeviceCapabilities(color = true, brightness = true, perZone = true, zones = 8),
            led = SingleAdcJoypadDescriptor("/exact"),
            previewProfileId = null,
            previewCalibration = null,
        )

    @Test
    fun `exact profile wins over generic candidates`() {
        val result = resolveDetectionOutcome(identity, exact, exactTransportAvailable = true, candidates = listOf(candidate))

        assertEquals(exact, (result as DetectionOutcome.Resolved).device)
    }

    @Test
    fun `known profile stays identifiable when its transport is unavailable`() {
        val result = resolveDetectionOutcome(identity, exact, exactTransportAvailable = false, candidates = listOf(candidate))

        assertEquals(exact, (result as DetectionOutcome.UnavailableKnownDevice).device)
        assertEquals(listOf(candidate), result.candidates)
    }

    @Test
    fun `explicit learned route is used only while exact transport is unavailable`() {
        val learned = exact.copy(id = "learned-singleadc-joypad-123456789abc", capabilities = DeviceCapabilities(true, false, false, 1))

        val unavailable =
            resolveDetectionOutcome(
                identity,
                exact,
                exactTransportAvailable = false,
                candidates = listOf(candidate),
                learned = learned,
            )
        val available =
            resolveDetectionOutcome(
                identity,
                exact,
                exactTransportAvailable = true,
                candidates = listOf(candidate),
                learned = learned,
            )

        assertEquals(learned, (unavailable as DetectionOutcome.Resolved).device)
        assertEquals(exact, (available as DetectionOutcome.Resolved).device)
    }

    @Test
    fun `unknown device exposes candidates without becoming resolved`() {
        val result = resolveDetectionOutcome(identity, exact = null, exactTransportAvailable = false, candidates = listOf(candidate))

        assertEquals(listOf(candidate), (result as DetectionOutcome.Candidates).candidates)
    }

    @Test
    fun `unknown device without safe signals remains unsupported`() {
        val result = resolveDetectionOutcome(identity, exact = null, exactTransportAvailable = false, candidates = emptyList())

        assertTrue(result is DetectionOutcome.Unsupported)
    }

    @Test
    fun `available exact profile skips every generic candidate scan`() {
        assertEquals(false, shouldCollectGenericCandidates(exact, exactTransportAvailable = true))
        assertEquals(true, shouldCollectGenericCandidates(exact, exactTransportAvailable = false))
        assertEquals(true, shouldCollectGenericCandidates(null, exactTransportAvailable = false))
    }
}
