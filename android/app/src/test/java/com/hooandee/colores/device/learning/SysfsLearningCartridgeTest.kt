package com.hooandee.colores.device.learning

import com.hooandee.colores.led.FakeSysfsAccess
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SysfsLearningCartridgeTest {
    @Test
    fun `notification surface is rejected even if writable`() {
        val descriptor = SysfsRgbDescriptor("/sys/class/leds/rgb-notification", 1, 255, SysfsColorKind.MULTI_INTENSITY_HEX)
        val cartridge = SysfsLearningCartridge(FakeSysfsAccess(emptySet()))

        assertFalse(cartridge.accepts(candidate(descriptor)))
    }

    @Test
    fun `multi zone probe lights one position and restores color and brightness`() {
        val descriptor = SysfsRgbDescriptor("/sys/class/leds/gamepad-rgb", 3, 255, SysfsColorKind.MULTI_INTENSITY_HEX)
        val color = "${descriptor.nodePath}/multi_intensity"
        val brightness = "${descriptor.nodePath}/brightness"
        val original = mutableMapOf(color to "0x010203 0x040506 0x070809", brightness to "180")
        val access = FakeSysfsAccess(setOf(color, brightness), original.toMutableMap())
        val cartridge = SysfsLearningCartridge(access)
        val candidate = candidate(descriptor)
        val snapshot = requireNotNull(cartridge.snapshot(candidate))

        assertTrue(cartridge.execute(candidate, ProbeStep.ZONE, zone = 1))
        assertEquals("0x000000 0xFF00FF 0x000000", access.values[color])
        assertEquals("64", access.values[brightness])
        assertEquals(RollbackStatus.RESTORED_AND_READ_BACK, cartridge.restore(candidate, snapshot))
        assertEquals(original, access.values)
    }

    @Test
    fun `missing brightness snapshot refuses a color write`() {
        val descriptor = SysfsRgbDescriptor("/sys/class/leds/gamepad-rgb", 1, 255, SysfsColorKind.MULTI_INTENSITY_HEX)
        val color = "${descriptor.nodePath}/multi_intensity"
        val access = FakeSysfsAccess(setOf(color), mutableMapOf(color to "0x010203"))
        val cartridge = SysfsLearningCartridge(access)

        assertEquals(null, cartridge.snapshot(candidate(descriptor)))
        assertFalse(cartridge.execute(candidate(descriptor), ProbeStep.COLOR))
    }

    @Test
    fun `failed restore still attempts color and brightness`() {
        val descriptor = SysfsRgbDescriptor("/sys/class/leds/gamepad-rgb", 2, 255, SysfsColorKind.MULTI_INTENSITY_HEX)
        val color = "${descriptor.nodePath}/multi_intensity"
        val brightness = "${descriptor.nodePath}/brightness"
        val original = linkedMapOf(color to "0x010203 0x040506", brightness to "180")
        val access = FakeSysfsAccess(setOf(color, brightness), original.toMutableMap(), failedWrites = setOf(color))
        val cartridge = SysfsLearningCartridge(access)
        val candidate = candidate(descriptor)
        val snapshot = requireNotNull(cartridge.snapshot(candidate))

        assertEquals(RollbackStatus.RESTORE_FAILED, cartridge.restore(candidate, snapshot))
        assertEquals(listOf(color, brightness), access.writes.map { it.first })
    }

    private fun candidate(descriptor: SysfsRgbDescriptor) =
        ProbeCandidate("android-sysfs-multicolor", 1, ProbeSurface.SYSFS_RGB, descriptor, emptySet())
}
