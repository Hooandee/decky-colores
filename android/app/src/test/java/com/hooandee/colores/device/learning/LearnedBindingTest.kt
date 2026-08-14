package com.hooandee.colores.device.learning

import com.hooandee.colores.device.AndroidDeviceIdentity
import com.hooandee.colores.device.DetectedAndroidDevice
import com.hooandee.colores.device.DeviceCapabilities
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.device.GenericVendorLed
import com.hooandee.colores.led.Htr3212Descriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LearnedBindingTest {
    private val identity = AndroidDeviceIdentity("Mystery", "mystery", "Maker", emptyMap())
    private val descriptor = SingleAdcJoypadDescriptor()
    private val candidate = ProbeCandidate("singleadc-joypad", 1, ProbeSurface.SINGLEADC_JOYPAD, descriptor, emptySet())
    private val binding =
        LearnedDeviceBinding(
            identityHash = learningIdentityHash(identity),
            cartridgeId = "singleadc-joypad",
            cartridgeVersion = 1,
            descriptorJson = encodeLearningDescriptor(descriptor),
            capabilities = DeviceCapabilities(color = true, brightness = false, perZone = false, zones = 1),
            appVersion = "0.1.0",
            learnedAtEpochMs = 1234L,
        )

    @Test
    fun `matching observed binding becomes an experimental device`() {
        val device = requireNotNull(resolveLearnedDevice(identity, binding, listOf(candidate)))

        assertEquals("learned-singleadc-joypad-${binding.identityHash.take(12)}", device.id)
        assertEquals(binding.capabilities, device.capabilities)
        assertEquals(descriptor, device.led)
    }

    @Test
    fun `binding for another identity is ignored`() {
        assertNull(resolveLearnedDevice(identity.copy(model = "Other"), binding, listOf(candidate)))
    }

    @Test
    fun `stale descriptor or cartridge version is ignored`() {
        assertNull(resolveLearnedDevice(identity, binding.copy(cartridgeVersion = 2), listOf(candidate)))
        assertNull(
            resolveLearnedDevice(
                identity,
                binding.copy(descriptorJson = encodeLearningDescriptor(SingleAdcJoypadDescriptor("/other"))),
                listOf(candidate),
            ),
        )
    }

    @Test
    fun `HTR descriptor preserves its bounded routing through persistence`() {
        val descriptor =
            GenericVendorLed.descriptor(8).copy(
                driver = "htr3212",
                htr3212 =
                    Htr3212Descriptor(
                        leftBus = 3,
                        rightBus = 5,
                        address = 0x3c,
                        leftOrder = listOf(0, 1, 2, 3),
                        rightOrder = listOf(3, 2, 1, 0),
                        rgbStartRegister = 0x0d,
                    ),
            )

        assertEquals(descriptor, decodeLearningDescriptor(encodeLearningDescriptor(descriptor)))
    }

    @Test
    fun `calibrated HTR order survives candidate rediscovery`() {
        val observedDescriptor =
            GenericVendorLed.descriptor(8).copy(
                driver = "htr3212",
                htr3212 =
                    Htr3212Descriptor(
                        leftBus = 3,
                        rightBus = 5,
                        address = 0x3c,
                        leftOrder = listOf(0, 1, 2, 3),
                        rightOrder = listOf(0, 1, 2, 3),
                        rgbStartRegister = 0x0d,
                    ),
            )
        val calibratedDescriptor =
            observedDescriptor.copy(
                htr3212 =
                    observedDescriptor.htr3212?.copy(
                        leftOrder = listOf(1, 3, 0, 2),
                        rightOrder = listOf(3, 2, 1, 0),
                    ),
            )
        val htrCandidate =
            ProbeCandidate(
                cartridgeId = HTR3212_PROBE_ID,
                cartridgeVersion = 1,
                surface = ProbeSurface.HTR3212,
                descriptor = observedDescriptor,
                signalKeys = setOf("htr3212_pair"),
            )
        val htrBinding =
            binding.copy(
                cartridgeId = HTR3212_PROBE_ID,
                descriptorJson = encodeLearningDescriptor(calibratedDescriptor),
                capabilities = DeviceCapabilities(color = true, brightness = false, perZone = true, zones = 8),
            )

        val resolved = requireNotNull(resolveLearnedDevice(identity, htrBinding, listOf(htrCandidate)))

        assertEquals(calibratedDescriptor, resolved.led)
    }

    @Test
    fun `rediscovery rejects changed or invalid HTR routing`() {
        val observedDescriptor =
            GenericVendorLed.descriptor(8).copy(
                driver = "htr3212",
                htr3212 =
                    Htr3212Descriptor(
                        leftBus = 3,
                        rightBus = 5,
                        address = 0x3c,
                        leftOrder = listOf(0, 1, 2, 3),
                        rightOrder = listOf(0, 1, 2, 3),
                        rgbStartRegister = 0x0d,
                    ),
            )
        val htrCandidate = ProbeCandidate(HTR3212_PROBE_ID, 1, ProbeSurface.HTR3212, observedDescriptor, emptySet())
        val changedBus = observedDescriptor.copy(htr3212 = observedDescriptor.htr3212?.copy(leftBus = 4))
        val invalidOrder = observedDescriptor.copy(htr3212 = observedDescriptor.htr3212?.copy(leftOrder = listOf(0, 0, 1, 2)))
        val baseBinding =
            binding.copy(
                cartridgeId = HTR3212_PROBE_ID,
                capabilities = DeviceCapabilities(color = true, brightness = false, perZone = true, zones = 8),
            )

        assertNull(resolveLearnedDevice(identity, baseBinding.copy(descriptorJson = encodeLearningDescriptor(changedBus)), listOf(htrCandidate)))
        assertNull(resolveLearnedDevice(identity, baseBinding.copy(descriptorJson = encodeLearningDescriptor(invalidOrder)), listOf(htrCandidate)))
    }

    @Test
    fun `a native profile recognizes the compatible learned device id for migration`() {
        val portalIdentity = AndroidDeviceIdentity("Odin2 Portal", "kalama", "AYN", emptyMap())
        val learnedDescriptor =
            GenericVendorLed.descriptor(8).copy(
                driver = "htr3212",
                htr3212 =
                    Htr3212Descriptor(
                        leftBus = 3,
                        rightBus = 5,
                        address = 0x3c,
                        leftOrder = listOf(0, 3, 2, 1),
                        rightOrder = listOf(2, 3, 0, 1),
                        rgbStartRegister = 0x0d,
                    ),
            )
        val capabilities = DeviceCapabilities(color = true, brightness = true, perZone = true, zones = 8)
        val portalBinding =
            binding.copy(
                identityHash = learningIdentityHash(portalIdentity),
                cartridgeId = HTR3212_PROBE_ID,
                descriptorJson = encodeLearningDescriptor(learnedDescriptor),
                capabilities = capabilities,
            )
        val exact =
            DetectedAndroidDevice(
                id = "ayn-odin2-portal",
                friendlyName = "AYN Odin 2 Portal",
                capabilities = capabilities,
                led = learnedDescriptor.copy(vendorService = "com.odin.gameassistant"),
                previewProfileId = null,
                previewCalibration = null,
            )

        assertEquals(
            "learned-htr3212-multipoint-${portalBinding.identityHash.take(12)}",
            learnedDeviceIdForPromotion(portalIdentity, exact, portalBinding),
        )
    }
}
