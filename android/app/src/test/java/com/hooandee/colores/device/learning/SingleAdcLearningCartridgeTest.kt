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
    fun `brightness probe starts high then drops and rises observably`() {
        val original = statePaths.associateWith { "25" }.toMutableMap()
        val access = FakeSysfsAccess((statePaths + latch).toSet(), original)
        val cartridge = SingleAdcLearningCartridge(access)
        val candidate = candidate(SingleAdcJoypadDescriptor(base))
        val level = { access.values.getValue("$base/led_level").toInt() }

        assertTrue(cartridge.execute(candidate, ProbeStep.COLOR))
        val color = level()
        assertTrue(cartridge.execute(candidate, ProbeStep.BRIGHTNESS_LOW))
        val low = level()
        assertTrue(cartridge.execute(candidate, ProbeStep.BRIGHTNESS_HIGH))
        val high = level()

        assertTrue(low < color)
        assertTrue(high > low)
        assertTrue(high <= 55)
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

    private val effectPaths =
        listOf("Led_rgb_r2", "Led_rgb_g2", "Led_rgb_b2", "Led_rgb_r1", "Led_rgb_g1", "Led_rgb_b1", "led_speed").map { "$base/$it" }

    @Test
    fun `breathing probe is offered only when every effect node is writable`() {
        val original = (statePaths + effectPaths).associateWith { "3" }
        val candidate = candidate(SingleAdcJoypadDescriptor(base))
        val complete = SingleAdcLearningCartridge(FakeSysfsAccess((statePaths + effectPaths + latch).toSet(), original.toMutableMap()))
        val readOnlySpeed =
            SingleAdcLearningCartridge(FakeSysfsAccess((statePaths + effectPaths + latch).toSet() - "$base/led_speed", original.toMutableMap()))
        val missingSlot =
            SingleAdcLearningCartridge(
                FakeSysfsAccess((statePaths + effectPaths + latch).toSet(), original.filterKeys { it != "$base/Led_rgb_b1" }.toMutableMap()),
            )

        assertTrue(ProbeStep.HARDWARE_EFFECT in complete.supportedSteps(candidate))
        assertFalse(ProbeStep.HARDWARE_EFFECT in readOnlySpeed.supportedSteps(candidate))
        assertFalse(ProbeStep.HARDWARE_EFFECT in missingSlot.supportedSteps(candidate))
        assertFalse(readOnlySpeed.execute(candidate, ProbeStep.HARDWARE_EFFECT))
        assertEquals(statePaths.toSet(), requireNotNull(readOnlySpeed.snapshot(candidate)).values.keys)
    }

    @Test
    fun `breathing probe writes the firmware effect and rollback restores every written node`() {
        val original = (statePaths + effectPaths).withIndex().associate { (index, path) -> path to (index + 3).toString() }
        val access = FakeSysfsAccess((statePaths + effectPaths + latch).toSet(), original.toMutableMap())
        val cartridge = SingleAdcLearningCartridge(access)
        val candidate = candidate(SingleAdcJoypadDescriptor(base))
        val snapshot = requireNotNull(cartridge.snapshot(candidate))

        assertEquals((statePaths + effectPaths).toSet(), snapshot.values.keys)
        assertTrue(cartridge.execute(candidate, ProbeStep.HARDWARE_EFFECT))
        assertEquals("2", access.values["$base/led_mode"])
        assertEquals("4", access.values["$base/led_speed"])
        assertEquals("255", access.values["$base/Led_rgb_r2"])
        assertEquals("255", access.values["$base/Led_rgb_b1"])
        assertEquals("$base/led_set" to "1", access.writes.last())
        val written = access.writes.map { it.first }.toSet() - latch
        assertTrue(snapshot.values.keys.containsAll(written))

        assertEquals(RollbackStatus.RESTORED_AND_READ_BACK, cartridge.restore(candidate, snapshot))
        assertEquals(original, access.values.filterKeys(original::containsKey))
    }

    @Test
    fun `firmware normalized effect speed after accepted restore is reported without readback`() {
        val original = (statePaths + effectPaths).associateWith { "5" }
        val access = NormalizingBrightnessAccess(original.toMutableMap(), "$base/led_speed", latch, normalizedBrightness = "3")
        val cartridge = SingleAdcLearningCartridge(access)
        val candidate = candidate(SingleAdcJoypadDescriptor(base))
        val snapshot = requireNotNull(cartridge.snapshot(candidate))

        assertEquals(RollbackStatus.RESTORED_WITHOUT_HARDWARE_READBACK, cartridge.restore(candidate, snapshot))
    }

    @Test
    fun `a color node that differs after restore remains a failure`() {
        val original = (statePaths + effectPaths).associateWith { "5" }
        val access = NormalizingBrightnessAccess(original.toMutableMap(), "$base/custum_rgb_r", latch, normalizedBrightness = "7")
        val cartridge = SingleAdcLearningCartridge(access)
        val candidate = candidate(SingleAdcJoypadDescriptor(base))
        val snapshot = requireNotNull(cartridge.snapshot(candidate))

        assertEquals(RollbackStatus.RESTORE_FAILED, cartridge.restore(candidate, snapshot))
    }

    @Test
    fun `rollback rejects a snapshot with a partial effect node set`() {
        val original = (statePaths + effectPaths).associateWith { "3" }
        val access = FakeSysfsAccess((statePaths + effectPaths + latch).toSet(), original.toMutableMap())
        val cartridge = SingleAdcLearningCartridge(access)
        val candidate = candidate(SingleAdcJoypadDescriptor(base))
        val partial = ProbeSnapshot(original.filterKeys { it != "$base/led_speed" })

        assertEquals(RollbackStatus.RESTORE_FAILED, cartridge.restore(candidate, partial))
    }

    @Test
    fun `only a confirmed breathing answer enables vendor effects in the binding`() {
        val cartridge = SingleAdcLearningCartridge(FakeSysfsAccess(emptySet()))
        val candidate = candidate(SingleAdcJoypadDescriptor(base))
        fun evidence(level: EvidenceLevel) = listOf(ProbeEvidence(ProbeStep.HARDWARE_EFFECT, null, level, null))

        val confirmed = cartridge.bindingCandidate(candidate, evidence(EvidenceLevel.USER_CONFIRMED))
        val denied = cartridge.bindingCandidate(candidate, evidence(EvidenceLevel.NOT_OBSERVED))
        val colorOnly = cartridge.bindingCandidate(candidate, listOf(ProbeEvidence(ProbeStep.COLOR, null, EvidenceLevel.USER_CONFIRMED, null)))

        assertEquals(SingleAdcJoypadDescriptor(base, vendorEffects = true), confirmed.descriptor)
        assertEquals(candidate, denied)
        assertEquals(candidate, colorOnly)
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
