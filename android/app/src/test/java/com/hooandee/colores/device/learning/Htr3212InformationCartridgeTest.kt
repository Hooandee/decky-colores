package com.hooandee.colores.device.learning

import com.hooandee.colores.device.AndroidDeviceIdentity
import com.hooandee.colores.device.GenericVendorLed
import com.hooandee.colores.led.SettingsProviderDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Htr3212InformationCartridgeTest {
    private val identity = AndroidDeviceIdentity("Odin2 Portal", "kalama", "AYN", emptyMap())

    @Test
    fun `paired left and right controllers unlock an eight zone candidate`() {
        val cartridge =
            Htr3212InformationCartridge(
                topologyReader =
                    I2cTopologyReader {
                        listOf(
                            I2cController(bus = 3, address = 0x3c, driver = "htr3212l"),
                            I2cController(bus = 5, address = 0x3c, driver = "htr3212r"),
                        )
                    },
            )
        val route = HardwareLearningGraph(listOf(cartridge)).resolve(identity, listOf(settingsCandidate()))

        val candidate = route.candidates.single { it.cartridgeId == HTR3212_PROBE_ID }
        val descriptor = candidate.descriptor as SettingsProviderDescriptor
        assertEquals(HTR3212_PROBE_VERSION, candidate.cartridgeVersion)
        assertEquals(8, descriptor.zones)
        assertEquals(3, descriptor.htr3212?.leftBus)
        assertEquals(5, descriptor.htr3212?.rightBus)
        assertEquals(0x3c, descriptor.htr3212?.address)
        assertEquals(0x0d, descriptor.htr3212?.rgbStartRegister)
        assertFalse(descriptor.htr3212?.explicitInitialization == true)
        assertTrue(route.facts.any { it.key == FACT_HTR3212_PAIR })
    }

    @Test
    fun `observed RP5 pair enables only its physically validated initialization`() {
        val cartridge =
            Htr3212InformationCartridge(
                topologyReader =
                    I2cTopologyReader {
                        listOf(
                            I2cController(bus = 1, address = 0x3c, driver = "htr3212l"),
                            I2cController(bus = 0, address = 0x3c, driver = "htr3212r"),
                        )
                    },
            )
        val rp5 = AndroidDeviceIdentity("Retroid Pocket 5", "kona", "Moorechip", emptyMap())

        val route = HardwareLearningGraph(listOf(cartridge)).resolve(rp5, listOf(settingsCandidate()))
        val descriptor = route.candidates.single { it.cartridgeId == HTR3212_PROBE_ID }.descriptor as SettingsProviderDescriptor

        assertTrue(descriptor.htr3212?.explicitInitialization == true)
    }

    @Test
    fun `one controller records evidence without claiming multipoint`() {
        val cartridge =
            Htr3212InformationCartridge(
                topologyReader = I2cTopologyReader { listOf(I2cController(3, 0x3c, "htr3212l")) },
            )

        val route = HardwareLearningGraph(listOf(cartridge)).resolve(identity, listOf(settingsCandidate()))

        assertTrue(route.facts.any { it.key == FACT_HTR3212_LEFT })
        assertTrue(route.candidates.none { it.cartridgeId == HTR3212_PROBE_ID })
    }

    @Test
    fun `unrelated devices at the same address never unlock the cartridge`() {
        val cartridge =
            Htr3212InformationCartridge(
                topologyReader =
                    I2cTopologyReader {
                        listOf(
                            I2cController(3, 0x3c, "camera"),
                            I2cController(5, 0x3c, "touch"),
                        )
                    },
            )

        val route = HardwareLearningGraph(listOf(cartridge)).resolve(identity, listOf(settingsCandidate()))

        assertTrue(route.candidates.none { it.cartridgeId == HTR3212_PROBE_ID })
        assertTrue(route.facts.none { it.key.startsWith("controller.htr3212") })
    }

    private fun settingsCandidate() =
        ProbeCandidate(
            cartridgeId = SETTINGS_PROBE_ID,
            cartridgeVersion = PROBE_VERSION,
            surface = ProbeSurface.SETTINGS_PSERVER,
            descriptor = GenericVendorLed.descriptor(2),
            signalKeys = setOf("observed_color_count"),
        )
}
