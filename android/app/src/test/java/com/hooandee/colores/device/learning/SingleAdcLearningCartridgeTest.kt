package com.hooandee.colores.device.learning

import com.hooandee.colores.led.FakeSysfsAccess
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SysfsAccess
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
    fun `firmware normalized brightness after accepted restore is reported without readback`() {
        val original =
            mapOf(
                "$base/custum_rgb_r" to "255",
                "$base/custum_rgb_g" to "94",
                "$base/custum_rgb_b" to "58",
                "$base/led_level" to "255",
                "$base/led_mode" to "1",
                "$base/led_switch" to "1",
            )
        val access = NormalizingBrightnessAccess(original.toMutableMap(), "$base/led_level", latch)
        val cartridge = SingleAdcLearningCartridge(access)
        val candidate = candidate(SingleAdcJoypadDescriptor(base))
        val snapshot = requireNotNull(cartridge.snapshot(candidate))

        assertTrue(cartridge.execute(candidate, ProbeStep.COLOR))

        assertEquals(RollbackStatus.RESTORED_WITHOUT_HARDWARE_READBACK, cartridge.restore(candidate, snapshot))
        assertEquals(original + ("$base/led_level" to "138"), access.values.filterKeys(original::containsKey))
    }

    @Test
    fun `brightness becoming unreadable after restore remains a failure`() {
        val original = statePaths.associateWith { "255" }
        val access = NormalizingBrightnessAccess(original.toMutableMap(), "$base/led_level", latch, normalizedBrightness = null)
        val cartridge = SingleAdcLearningCartridge(access)
        val candidate = candidate(SingleAdcJoypadDescriptor(base))
        val snapshot = requireNotNull(cartridge.snapshot(candidate))

        assertEquals(RollbackStatus.RESTORE_FAILED, cartridge.restore(candidate, snapshot))
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

    private class NormalizingBrightnessAccess(
        val values: MutableMap<String, String>,
        private val brightness: String,
        private val latch: String,
        private val normalizedBrightness: String? = "138",
    ) : SysfsAccess {
        override fun read(path: String): String? = values[path]

        override fun exists(path: String): Boolean = path == latch || path in values

        override fun canWrite(path: String): Boolean = exists(path)

        override fun write(
            path: String,
            value: String,
        ): Boolean {
            if (!canWrite(path)) return false
            values[path] = value.trim()
            if (path == latch) {
                normalizedBrightness?.let { values[brightness] = it } ?: values.remove(brightness)
            }
            return true
        }
    }
}
