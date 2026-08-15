package com.hooandee.colores.device.learning

import com.hooandee.colores.device.AndroidDeviceIdentity
import com.hooandee.colores.device.GenericVendorLed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareLearningGraphTest {
    private val identity = AndroidDeviceIdentity("Portal", "kalama", "AYN", emptyMap())

    @Test
    fun `information cartridges unlock progressively from emitted facts`() {
        val first =
            fakeInformationCartridge("first", emptySet()) {
                InformationCartridgeResult(
                    facts = listOf(HardwareFact("controller.family", "htr3212", FactEvidence.OBSERVED, "first")),
                )
            }
        val second =
            fakeInformationCartridge("second", setOf("controller.family")) {
                InformationCartridgeResult(
                    facts = listOf(HardwareFact("controller.count", "2", FactEvidence.OBSERVED, "second")),
                )
            }

        val route = HardwareLearningGraph(listOf(second, first)).resolve(identity, emptyList())

        assertEquals(listOf("controller.count", "controller.family"), route.facts.map(HardwareFact::key).sorted())
        assertEquals(listOf("first", "second"), route.inspectedCartridgeIds.sorted())
    }

    @Test
    fun `seed candidates become facts and duplicate candidates are collapsed`() {
        val seed = settingsCandidate()
        val cartridge =
            fakeInformationCartridge("derived", setOf(FACT_SETTINGS_PSERVER)) {
                InformationCartridgeResult(candidates = listOf(seed, seed))
            }

        val route = HardwareLearningGraph(listOf(cartridge)).resolve(identity, listOf(seed))

        assertTrue(route.facts.any { it.key == FACT_SETTINGS_PSERVER })
        assertEquals(1, route.candidates.size)
    }

    private fun settingsCandidate() =
        ProbeCandidate(
            cartridgeId = SETTINGS_PROBE_ID,
            cartridgeVersion = PROBE_VERSION,
            surface = ProbeSurface.SETTINGS_PSERVER,
            descriptor = GenericVendorLed.descriptor(2),
            signalKeys = setOf("observed_color_count"),
        )

    private fun fakeInformationCartridge(
        cartridgeId: String,
        requirements: Set<String>,
        inspect: (HardwareLearningContext) -> InformationCartridgeResult,
    ) =
        object : InformationCartridge {
            override val id = cartridgeId
            override val version = 1
            override val requiredFactKeys = requirements

            override fun inspect(context: HardwareLearningContext): InformationCartridgeResult = inspect(context)
        }
}
