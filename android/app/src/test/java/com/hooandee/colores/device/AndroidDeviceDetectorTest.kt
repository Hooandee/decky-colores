package com.hooandee.colores.device

import com.hooandee.colores.device.learning.DetectionOutcome
import com.hooandee.colores.device.learning.FACT_HTR3212_LEFT
import com.hooandee.colores.device.learning.FACT_HTR3212_RIGHT
import com.hooandee.colores.device.learning.FactEvidence
import com.hooandee.colores.device.learning.HardwareFact
import com.hooandee.colores.device.learning.Htr3212InformationCartridge
import com.hooandee.colores.device.learning.I2cController
import com.hooandee.colores.device.learning.I2cTopologyReader
import com.hooandee.colores.device.learning.ProbeCandidate
import com.hooandee.colores.device.learning.ProbeSurface
import com.hooandee.colores.device.learning.resolveDetectionOutcome
import com.hooandee.colores.led.Htr3212Descriptor
import com.hooandee.colores.led.SettingsProviderDescriptor
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import java.io.File
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
    fun `validated Nova topology activates its native profile without a binding`() {
        val shared = File("../../shared")
        val nova =
            requireNotNull(
                DeviceRegistry.parse(
                    devicesJson = shared.resolve("devices.json").readText(),
                    previewProfilesJson = shared.resolve("led-preview-profiles.json").readText(),
                ).match(AndroidDeviceIdentity("Retroid_Pocket_Nova", "kalama", "Moorechip", emptyMap())),
            )

        val result =
            resolveDetectionOutcome(
                identity = AndroidDeviceIdentity("Retroid_Pocket_Nova", "kalama", "Moorechip", emptyMap()),
                exact = nova,
                exactTransportAvailable = true,
                candidates = listOf(candidate),
                facts = htrFacts(leftBus = 3, rightBus = 6),
            )

        assertEquals("retroid-pocket-nova", (result as DetectionOutcome.Resolved).device.id)
    }

    @Test
    fun `mismatched Nova topology stays in discovery`() {
        val shared = File("../../shared")
        val nova =
            requireNotNull(
                DeviceRegistry.parse(
                    devicesJson = shared.resolve("devices.json").readText(),
                    previewProfilesJson = shared.resolve("led-preview-profiles.json").readText(),
                ).match(AndroidDeviceIdentity("Retroid_Pocket_Nova", "kalama", "Moorechip", emptyMap())),
            )

        val result =
            resolveDetectionOutcome(
                identity = AndroidDeviceIdentity("Retroid_Pocket_Nova", "kalama", "Moorechip", emptyMap()),
                exact = nova,
                exactTransportAvailable = true,
                candidates = listOf(candidate),
                facts = htrFacts(leftBus = 3, rightBus = 5),
            )

        assertTrue(result is DetectionOutcome.Candidates)
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
    fun `PServer and an observed HTR pair reach discovery without a vendor color setting`() {
        val nova = AndroidDeviceIdentity("Retroid Pocket Nova", "kalama", "Moorechip", emptyMap())
        val cartridge =
            Htr3212InformationCartridge(
                I2cTopologyReader {
                    listOf(
                        I2cController(3, 0x3c, "htr3212l"),
                        I2cController(6, 0x3c, "htr3212r"),
                    )
                },
            )

        val route =
            resolveHardwareLearningRoute(
                identity = nova,
                pserverAvailable = true,
                seedCandidates = emptyList(),
                informationCartridges = listOf(cartridge),
            )

        assertEquals(listOf(ProbeSurface.HTR3212), route.candidates.map { it.surface })
        assertTrue(route.facts.any { it.key == FACT_HTR3212_LEFT })
        assertTrue(route.facts.any { it.key == FACT_HTR3212_RIGHT })
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

    @Test
    fun `readable Thor topology that matches keeps the exact profile`() {
        val result = detectThor(listOf(I2cController(3, 0x3c, "htr3212l"), I2cController(6, 0x3c, "htr3212r")))

        assertEquals("ayn-thor", (result as DetectionOutcome.Resolved).device.id)
        assertEquals("matched", result.facts.single { it.key == FACT_HTR3212_TOPOLOGY }.value)
    }

    @Test
    fun `observed Thor app context topology matches its exact profile`() {
        val observed =
            listOf(
                I2cController(2, 0x34, "aw882xx_smartpa"),
                I2cController(2, 0x35, "aw882xx_smartpa"),
                I2cController(2, 0x42, "sc8547-charger"),
                I2cController(2, 0x64, "bq27z561"),
                I2cController(3, 0x3c, "htr3212l"),
                I2cController(4, 0x38, "fts_ts"),
                I2cController(5, 0x38, "fts_ts"),
                I2cController(6, 0x3c, "htr3212r"),
                I2cController(6, 0x77, "bmp280"),
            )

        val result = detectThor(observed)

        assertEquals("ayn-thor", (result as DetectionOutcome.Resolved).device.id)
        assertEquals("matched", result.facts.single { it.key == FACT_HTR3212_TOPOLOGY }.value)
    }

    @Test
    fun `readable Thor topology that contradicts the profile never activates compiled buses`() {
        val observed = listOf(I2cController(4, 0x3c, "htr3212l"), I2cController(7, 0x3c, "htr3212r"))

        val result = detectThor(observed)

        assertTrue(result is DetectionOutcome.Candidates)
        val htr = (result as DetectionOutcome.Candidates).candidates.single { it.surface == ProbeSurface.HTR3212 }
        val hardware = requireNotNull((htr.descriptor as SettingsProviderDescriptor).htr3212)
        assertEquals(4 to 7, hardware.leftBus to hardware.rightBus)
        assertFalse(htr.signalKeys.contains("exact_profile"))
        val fact = result.facts.single { it.key == FACT_HTR3212_TOPOLOGY }.value
        assertTrue(fact.startsWith("contradicted"))
        assertTrue(fact.contains("left=4-0x3c"))
    }

    @Test
    fun `readable topology without the HTR drivers contradicts the exact profile`() {
        val result = detectThor(listOf(I2cController(3, 0x3c, "other-chip")))

        assertFalse(result is DetectionOutcome.Resolved)
        assertTrue(result.facts.single { it.key == FACT_HTR3212_TOPOLOGY }.value.contains("left=none"))
    }

    @Test
    fun `readable topology with only unrelated buses keeps the exact profile unverified`() {
        val result = detectThor(listOf(I2cController(0, 0x28, "touchscreen"), I2cController(9, 0x3c, "other-chip")))

        assertEquals("ayn-thor", (result as DetectionOutcome.Resolved).device.id)
        assertEquals("unverified", result.facts.single { it.key == FACT_HTR3212_TOPOLOGY }.value)
    }

    @Test
    fun `differently named HTR family chips on the compiled buses do not block the exact profile`() {
        val result = detectThor(listOf(I2cController(3, 0x3c, "htr3212"), I2cController(6, 0x3c, "htr3212")))

        assertEquals("ayn-thor", (result as DetectionOutcome.Resolved).device.id)
        assertEquals("unverified", result.facts.single { it.key == FACT_HTR3212_TOPOLOGY }.value)
    }

    @Test
    fun `unreadable or empty topology keeps the exact profile and marks it unverified`() {
        val result = detectThor(emptyList())

        assertEquals("ayn-thor", (result as DetectionOutcome.Resolved).device.id)
        assertEquals("unverified", result.facts.single { it.key == FACT_HTR3212_TOPOLOGY }.value)
    }

    @Test
    fun `topology guard ignores automatically gated profiles`() {
        val rp5 = htrProfile("retroid-pocket-5", automaticActivation = true)

        val guard = guardExactHtrTopology(rp5) { error("topology must not be read") }

        assertFalse(guard.contradicted)
        assertTrue(guard.facts.isEmpty())
    }

    @Test
    fun `six digit settings candidate never becomes the HTR vendor base`() {
        val topology = listOf(I2cController(3, 0x3c, "htr3212l"), I2cController(6, 0x3c, "htr3212r"))
        val settings = requireNotNull(GenericLedResolver.settingsCandidate(true, "#010203,#040506"))

        val result =
            detectFromInputs(
                identity = identity,
                exact = null,
                pserverAvailable = true,
                binding = null,
                readTopology = { topology },
                seedCandidates = { listOf(settings) },
                informationCartridges = listOf(Htr3212InformationCartridge { topology }),
            )

        val candidates = (result as DetectionOutcome.Candidates).candidates
        val htr = candidates.single { it.surface == ProbeSurface.HTR3212 }
        assertEquals("argb_hex_csv", (htr.descriptor as SettingsProviderDescriptor).colorFormat)
        assertTrue(candidates.contains(settings))
    }

    private fun detectThor(topology: List<I2cController>): DetectionOutcome {
        val shared = File("../../shared")
        val thorIdentity = AndroidDeviceIdentity("AYN Thor", "kalama", "AYN", emptyMap())
        val thor =
            requireNotNull(
                DeviceRegistry.parse(
                    devicesJson = shared.resolve("devices.json").readText(),
                    previewProfilesJson = shared.resolve("led-preview-profiles.json").readText(),
                ).match(thorIdentity),
            )
        return detectFromInputs(
            identity = thorIdentity,
            exact = thor,
            pserverAvailable = true,
            binding = null,
            readTopology = { topology },
            seedCandidates = { emptyList() },
            informationCartridges = listOf(Htr3212InformationCartridge { topology }),
        )
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
