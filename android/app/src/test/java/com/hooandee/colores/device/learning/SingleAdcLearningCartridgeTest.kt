package com.hooandee.colores.device.learning

import com.hooandee.colores.led.FakeSysfsAccess
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleAdcLearningCartridgeTest {
    private val base = SingleAdcJoypadDescriptor.DEFAULT_BASE_PATH
    private val statePaths =
        listOf(
            "custum_rgb_r",
            "custum_rgb_g",
            "custum_rgb_b",
            "led_level",
            "led_mode",
            "led_switch",
        ).map { "$base/$it" }
    private val latch = "$base/led_set"

    @Test
    fun `candidate outside the exact singleadc surface is rejected`() {
        val access = FakeSysfsAccess(emptySet())
        val cartridge = SingleAdcLearningCartridge(access)

        assertFalse(cartridge.accepts(candidate(SingleAdcJoypadDescriptor("/other"))))
    }

    @Test
    fun `probe restores every state node and latches the restored frame`() {
        val original = statePaths.withIndex().associate { (index, path) -> path to (index + 10).toString() }.toMutableMap()
        val access = FakeSysfsAccess((statePaths + latch).toSet(), original.toMutableMap())
        val cartridge = SingleAdcLearningCartridge(access)
        val candidate = candidate(SingleAdcJoypadDescriptor(base))
        val snapshot = requireNotNull(cartridge.snapshot(candidate))

        assertTrue(cartridge.execute(candidate, ProbeStep.COLOR))
        assertEquals("255", access.values["$base/custum_rgb_r"])
        assertEquals("0", access.values["$base/custum_rgb_g"])
        assertEquals("255", access.values["$base/custum_rgb_b"])
        assertEquals(RollbackStatus.RESTORED_AND_READ_BACK, cartridge.restore(candidate, snapshot))
        assertEquals(original, access.values.filterKeys(original::containsKey))
        assertEquals("1", access.values[latch])
    }

    @Test
    fun `failed restore attempts every state node and the latch`() {
        val original = statePaths.withIndex().associate { (index, path) -> path to (index + 10).toString() }.toMutableMap()
        val access = FakeSysfsAccess((statePaths + latch).toSet(), original.toMutableMap(), failedWrites = setOf(statePaths.first()))
        val cartridge = SingleAdcLearningCartridge(access)
        val candidate = candidate(SingleAdcJoypadDescriptor(base))
        val snapshot = requireNotNull(cartridge.snapshot(candidate))

        assertEquals(RollbackStatus.RESTORE_FAILED, cartridge.restore(candidate, snapshot))
        assertEquals(statePaths + latch, access.writes.map { it.first })
    }

    private fun candidate(descriptor: SingleAdcJoypadDescriptor) =
        ProbeCandidate("singleadc-joypad", 1, ProbeSurface.SINGLEADC_JOYPAD, descriptor, emptySet())
}
