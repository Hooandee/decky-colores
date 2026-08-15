package com.hooandee.colores.device.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProbeCartridgeCatalogTest {
    @Test
    fun `catalog exposes only the audited cartridge families`() {
        val catalog =
            ProbeCartridgeCatalog(
                listOf(
                    fakeCartridge("settings-pserver-joystick"),
                    fakeCartridge("singleadc-joypad"),
                    fakeCartridge("android-sysfs-multicolor"),
                    fakeCartridge("htr3212-multipoint"),
                    fakeCartridge("generic-i2c"),
                ),
            )

        assertEquals(
            listOf("settings-pserver-joystick", "singleadc-joypad", "android-sysfs-multicolor", "htr3212-multipoint"),
            catalog.all.map(ProbeCartridge::id),
        )
        assertNull(catalog.find("generic-i2c", 1))
    }

    @Test
    fun `catalog requires the exact cartridge version`() {
        val catalog = ProbeCartridgeCatalog(listOf(fakeCartridge("singleadc-joypad", version = 2)))

        assertNull(catalog.find("singleadc-joypad", 1))
        assertEquals(2, catalog.find("singleadc-joypad", 2)?.version)
    }

    private fun fakeCartridge(
        id: String,
        version: Int = 1,
    ): ProbeCartridge =
        object : ProbeCartridge {
            override val id = id
            override val version = version
            override val surface = ProbeSurface.SINGLEADC_JOYPAD

            override fun accepts(candidate: ProbeCandidate) = true

            override fun snapshot(candidate: ProbeCandidate) = ProbeSnapshot(emptyMap())

            override fun supportedSteps(candidate: ProbeCandidate) = emptyList<ProbeStep>()

            override fun execute(
                candidate: ProbeCandidate,
                step: ProbeStep,
                zone: Int?,
            ) = true

            override fun restore(
                candidate: ProbeCandidate,
                snapshot: ProbeSnapshot,
            ) = RollbackStatus.RESTORED_AND_READ_BACK
        }
}
