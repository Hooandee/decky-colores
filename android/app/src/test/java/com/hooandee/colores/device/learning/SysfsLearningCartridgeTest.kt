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
        assertEquals("140", access.values[brightness])
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

    @Test
    fun `brightness steps change the observable level`() {
        val descriptor = SysfsRgbDescriptor("/sys/class/leds/gamepad-rgb", 1, 255, SysfsColorKind.MULTI_INTENSITY_HEX)
        val brightness = "${descriptor.nodePath}/brightness"
        val access =
            FakeSysfsAccess(
                setOf("${descriptor.nodePath}/multi_intensity", brightness),
                mutableMapOf("${descriptor.nodePath}/multi_intensity" to "0x010203", brightness to "10"),
            )
        val cartridge = SysfsLearningCartridge(access)
        val levels =
            listOf(ProbeStep.COLOR, ProbeStep.BRIGHTNESS_LOW, ProbeStep.BRIGHTNESS_HIGH).map { step ->
                assertTrue(cartridge.execute(candidate(descriptor), step))
                access.values[brightness]
            }

        assertEquals(listOf("140", "64", "140"), levels)
    }

    @Test
    fun `active trigger is released for the probe and restored afterwards`() {
        val descriptor = SysfsRgbDescriptor("/sys/class/leds/gamepad-rgb", 1, 255, SysfsColorKind.MULTI_INTENSITY_HEX)
        val color = "${descriptor.nodePath}/multi_intensity"
        val brightness = "${descriptor.nodePath}/brightness"
        val trigger = "${descriptor.nodePath}/trigger"
        val access =
            FakeSysfsAccess(
                setOf(color, brightness, trigger),
                mutableMapOf(color to "0x010203", brightness to "0", trigger to "none [timer] heartbeat"),
            )
        val cartridge = SysfsLearningCartridge(access)
        val candidate = candidate(descriptor)
        val snapshot = requireNotNull(cartridge.snapshot(candidate))

        assertEquals("timer", snapshot.values[trigger])
        assertTrue(cartridge.execute(candidate, ProbeStep.COLOR))
        assertEquals("none", access.values[trigger])
        access.values[brightness] = "255"
        assertEquals(RollbackStatus.RESTORED_AND_READ_BACK, cartridge.restore(candidate, snapshot))
        assertEquals("timer", access.values[trigger])
        assertEquals("0x010203", access.values[color])
    }

    @Test
    fun `journal written before trigger snapshots still restores`() {
        val descriptor = SysfsRgbDescriptor("/sys/class/leds/gamepad-rgb", 1, 255, SysfsColorKind.MULTI_INTENSITY_HEX)
        val color = "${descriptor.nodePath}/multi_intensity"
        val brightness = "${descriptor.nodePath}/brightness"
        val access = FakeSysfsAccess(setOf(color, brightness), mutableMapOf(color to "0xFF00FF", brightness to "140"))

        val status = SysfsLearningCartridge(access).restore(candidate(descriptor), ProbeSnapshot(mapOf(color to "0x010203", brightness to "9")))

        assertEquals(RollbackStatus.RESTORED_AND_READ_BACK, status)
    }

    @Test
    fun `composite and channel node surfaces are accepted only with safe members`() {
        val left = SysfsRgbDescriptor("/sys/class/leds/left", 1, 255, SysfsColorKind.MULTI_INTENSITY_HEX)
        val nodes = listOf("/sys/class/leds/s:red", "/sys/class/leds/s:green", "/sys/class/leds/s:blue")
        val channels = SysfsRgbDescriptor(nodes.first(), 1, 255, SysfsColorKind.CHANNEL_NODES, channelNodes = nodes)
        val unsafe = SysfsRgbDescriptor("/sys/class/leds/charging", 1, 255, SysfsColorKind.MULTI_INTENSITY_HEX)
        val cartridge = SysfsLearningCartridge(FakeSysfsAccess(emptySet()))

        assertTrue(cartridge.accepts(candidate(channels)))
        assertTrue(cartridge.accepts(candidate(SysfsRgbDescriptor(left.nodePath, 2, 255, SysfsColorKind.COMPOSITE, members = listOf(left, channels)))))
        assertFalse(cartridge.accepts(candidate(SysfsRgbDescriptor(left.nodePath, 2, 255, SysfsColorKind.COMPOSITE, members = listOf(left, unsafe)))))
        assertFalse(cartridge.accepts(candidate(SysfsRgbDescriptor(left.nodePath, 3, 255, SysfsColorKind.COMPOSITE, members = listOf(left, channels)))))
    }

    @Test
    fun `composite zone probe lights only the selected member position`() {
        val left = SysfsRgbDescriptor("/sys/class/leds/left", 1, 255, SysfsColorKind.MULTI_INTENSITY_HEX)
        val right = SysfsRgbDescriptor("/sys/class/leds/right", 1, 255, SysfsColorKind.MULTI_INTENSITY_HEX)
        val paths = listOf(left, right).flatMap { listOf("${it.nodePath}/multi_intensity", "${it.nodePath}/brightness") }
        val access = FakeSysfsAccess(paths.toSet(), paths.associateWith { "0" }.toMutableMap())
        val composite = SysfsRgbDescriptor(left.nodePath, 2, 255, SysfsColorKind.COMPOSITE, members = listOf(left, right))

        assertTrue(SysfsLearningCartridge(access).execute(candidate(composite), ProbeStep.ZONE, zone = 1))
        assertEquals("0x000000", access.values["/sys/class/leds/left/multi_intensity"])
        assertEquals("0xFF00FF", access.values["/sys/class/leds/right/multi_intensity"])
    }

    private fun candidate(descriptor: SysfsRgbDescriptor) =
        ProbeCandidate("android-sysfs-multicolor", 1, ProbeSurface.SYSFS_RGB, descriptor, emptySet())
}
