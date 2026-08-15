package com.hooandee.colores.device

import com.hooandee.colores.device.learning.DetectionOutcome
import com.hooandee.colores.device.learning.FACT_HTR3212_LEFT
import com.hooandee.colores.device.learning.FACT_HTR3212_RIGHT
import com.hooandee.colores.device.learning.FactEvidence
import com.hooandee.colores.device.learning.HardwareFact
import com.hooandee.colores.device.learning.ProbeCandidate
import com.hooandee.colores.device.learning.ProbeSurface
import com.hooandee.colores.device.learning.resolveDetectionOutcome
import com.hooandee.colores.led.Htr3212Descriptor
import com.hooandee.colores.led.SettingsProviderDescriptor
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `validated RP5 topology activates its native eight zone profile without a binding`() {
        val rp5 = htrProfile("retroid-pocket-5", automaticActivation = true)
        val result =
            resolveDetectionOutcome(
                identity = AndroidDeviceIdentity("Retroid Pocket 5", "kona", "Moorechip", emptyMap()),
                exact = rp5,
                exactTransportAvailable = true,
                candidates = listOf(candidate),
                facts = htrFacts(),
            )

        assertEquals(rp5, (result as DetectionOutcome.Resolved).device)
    }

    @Test
    fun `validated RP5 topology replaces a learned two zone fallback`() {
        val rp5 = htrProfile("retroid-pocket-5", automaticActivation = true)
        val fallback =
            exact.copy(
                id = "learned-settings-provider-123456789abc",
                capabilities = DeviceCapabilities(color = true, brightness = true, perZone = true, zones = 2),
                led = GenericVendorLed.descriptor(2),
            )

        val result =
            resolveDetectionOutcome(
                identity = AndroidDeviceIdentity("Retroid Pocket 5", "kona", "Moorechip", emptyMap()),
                exact = rp5,
                exactTransportAvailable = true,
                candidates = listOf(candidate),
                learned = fallback,
                facts = htrFacts(),
            )

        assertEquals(rp5, (result as DetectionOutcome.Resolved).device)
    }

    @Test
    fun `validated RP5 topology preserves a learned eight zone calibration`() {
        val rp5 = htrProfile("retroid-pocket-5", automaticActivation = true)
        val calibrated = rp5.copy(id = "learned-htr3212-123456789abc")

        val result =
            resolveDetectionOutcome(
                identity = AndroidDeviceIdentity("Retroid Pocket 5", "kona", "Moorechip", emptyMap()),
                exact = rp5,
                exactTransportAvailable = true,
                candidates = listOf(candidate),
                learned = calibrated,
                facts = htrFacts(),
            )

        assertEquals(calibrated, (result as DetectionOutcome.Resolved).device)
    }

    @Test
    fun `validated RP5 topology replaces an unrelated learned eight zone route`() {
        val rp5 = htrProfile("retroid-pocket-5", automaticActivation = true)
        val unrelated =
            exact.copy(
                id = "learned-settings-provider-123456789abc",
                capabilities = DeviceCapabilities(color = true, brightness = true, perZone = true, zones = 8),
                led = GenericVendorLed.descriptor(8),
            )

        val result =
            resolveDetectionOutcome(
                identity = AndroidDeviceIdentity("Retroid Pocket 5", "kona", "Moorechip", emptyMap()),
                exact = rp5,
                exactTransportAvailable = true,
                candidates = listOf(candidate),
                learned = unrelated,
                facts = htrFacts(),
            )

        assertEquals(rp5, (result as DetectionOutcome.Resolved).device)
    }

    @Test
    fun `mismatched RP5 topology keeps discovery instead of claiming eight zones`() {
        val rp5 = htrProfile("retroid-pocket-5", automaticActivation = true)
        val result =
            resolveDetectionOutcome(
                identity = AndroidDeviceIdentity("Retroid Pocket 5", "kona", "Moorechip", emptyMap()),
                exact = rp5,
                exactTransportAvailable = true,
                candidates = listOf(candidate),
                facts = htrFacts(leftBus = 2),
            )

        assertTrue(result is DetectionOutcome.Candidates)
    }

    @Test
    fun `matching Thor topology preserves its exact profile behavior`() {
        val thor = htrProfile("ayn-thor", automaticActivation = false)
        val result =
            resolveDetectionOutcome(
                identity = AndroidDeviceIdentity("AYN Thor", "kalama", "AYN", emptyMap()),
                exact = thor,
                exactTransportAvailable = true,
                candidates = listOf(candidate),
                facts = htrFacts(),
            )

        assertEquals(thor, (result as DetectionOutcome.Resolved).device)
    }

    @Test
    fun `exact profile without an automatic topology gate wins over candidates`() {
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
    fun `learned route is used only while an ungated exact transport is unavailable`() {
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
    fun `exact HTR profile becomes a probe candidate without changing its hardware descriptor`() {
        val descriptor =
            GenericVendorLed.descriptor(8).copy(
                driver = "htr3212",
                htr3212 =
                    Htr3212Descriptor(
                        1,
                        0,
                        0x3c,
                        listOf(0, 1, 3, 2),
                        listOf(1, 2, 3, 0),
                        0x0d,
                        explicitInitialization = true,
                    ),
            )
        val profile = exact.copy(id = "retroid-pocket-5", led = descriptor)

        val candidate = requireNotNull(exactProfileCandidate(profile))

        assertEquals(ProbeSurface.HTR3212, candidate.surface)
        assertEquals(3, candidate.cartridgeVersion)
        assertEquals(descriptor, candidate.descriptor)
        assertTrue(candidate.signalKeys.contains("exact_profile"))
        assertEquals(0x0d, ((candidate.descriptor as SettingsProviderDescriptor).htr3212?.rgbStartRegister))
    }

    @Test
    fun `only topology gated exact profiles continue into hardware discovery`() {
        val rp5 = htrProfile("retroid-pocket-5", automaticActivation = true)

        assertFalse(shouldCollectVerificationCandidates(exact, exactTransportAvailable = true))
        assertTrue(shouldCollectVerificationCandidates(rp5, exactTransportAvailable = true))
        assertTrue(shouldCollectVerificationCandidates(exact, exactTransportAvailable = false))
        assertTrue(shouldCollectVerificationCandidates(null, exactTransportAvailable = false))
    }

    @Test
    fun `mismatched RP5 topology keeps the observed HTR candidate instead of probing compiled buses`() {
        val rp5 = htrProfile("retroid-pocket-5", automaticActivation = true)
        val observedProfile =
            htrProfile("observed-htr", automaticActivation = false).copy(
                led =
                    (htrProfile("observed-htr", automaticActivation = false).led as SettingsProviderDescriptor).let { descriptor ->
                        descriptor.copy(htr3212 = descriptor.htr3212?.copy(leftBus = 2))
                    },
            )
        val observed = requireNotNull(exactProfileCandidate(observedProfile))

        val candidates = verificationCandidates(listOf(observed), rp5, exactTransportAvailable = true, facts = htrFacts(leftBus = 2))

        assertEquals(listOf(observed), candidates)
    }

    @Test
    fun `validated RP5 topology replaces an observed HTR candidate with the calibrated exact profile`() {
        val rp5 = htrProfile("retroid-pocket-5", automaticActivation = true)
        val observedProfile = htrProfile("observed-htr", automaticActivation = false)
        val observed = requireNotNull(exactProfileCandidate(observedProfile))
        val expected = requireNotNull(exactProfileCandidate(rp5))

        val candidates = verificationCandidates(listOf(observed), rp5, exactTransportAvailable = true, facts = htrFacts())

        assertEquals(listOf(expected), candidates)
    }

    @Test
    fun `observed HTR follows the settings fallback before unrelated candidates`() {
        val settings =
            ProbeCandidate(
                cartridgeId = "android-settings-pserver",
                cartridgeVersion = 1,
                surface = ProbeSurface.SETTINGS_PSERVER,
                descriptor = GenericVendorLed.descriptor(2),
                signalKeys = emptySet(),
            )
        val observedHtr = requireNotNull(exactProfileCandidate(htrProfile("observed-htr", automaticActivation = false)))

        val candidates =
            verificationCandidates(
                observed = listOf(candidate, observedHtr, settings),
                exact = null,
                exactTransportAvailable = false,
                facts = emptyList(),
            )

        assertEquals(listOf(ProbeSurface.SETTINGS_PSERVER, ProbeSurface.HTR3212, ProbeSurface.SINGLEADC_JOYPAD), candidates.map { it.surface })
    }

    private fun htrProfile(
        id: String,
        automaticActivation: Boolean,
    ): DetectedAndroidDevice =
        exact.copy(
            id = id,
            led =
                GenericVendorLed.descriptor(8).copy(
                    driver = "htr3212",
                    htr3212 =
                        Htr3212Descriptor(
                            leftBus = 1,
                            rightBus = 0,
                            address = 0x3c,
                            leftOrder = listOf(0, 1, 3, 2),
                            rightOrder = listOf(1, 2, 3, 0),
                            automaticActivation = automaticActivation,
                        ),
                ),
        )

    private fun htrFacts(
        leftBus: Int = 1,
        rightBus: Int = 0,
        address: Int = 0x3c,
    ): List<HardwareFact> =
        listOf(
            HardwareFact(FACT_HTR3212_LEFT, "bus=$leftBus,address=0x%02x".format(address), FactEvidence.OBSERVED, "htr"),
            HardwareFact(FACT_HTR3212_RIGHT, "bus=$rightBus,address=0x%02x".format(address), FactEvidence.OBSERVED, "htr"),
        )
}
