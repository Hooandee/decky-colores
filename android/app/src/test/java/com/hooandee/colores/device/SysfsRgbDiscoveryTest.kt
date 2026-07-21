package com.hooandee.colores.device

import com.hooandee.colores.led.SysfsAccess
import com.hooandee.colores.led.SysfsColorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SysfsRgbDiscoveryTest {
    @Test
    fun `packed multi intensity node reports one zone per index token`() {
        val access =
            fakeAccess(
                files = mapOf("/sys/leds/rings/multi_index" to "rgb rgb rgb rgb", "/sys/leds/rings/max_brightness" to "255"),
                writable = setOf("/sys/leds/rings/multi_intensity"),
            )

        val descriptor = SysfsRgbDiscovery.discover(listOf(node("ally:rgb:joystick_rings", "/sys/leds/rings")), access)

        requireNotNull(descriptor)
        assertEquals(4, descriptor.zones)
        assertEquals(SysfsColorKind.MULTI_INTENSITY_HEX, descriptor.kind)
        assertEquals(255, descriptor.maxBrightness)
    }

    @Test
    fun `channel named multi intensity collapses three tokens into one zone`() {
        val access =
            fakeAccess(
                files = mapOf("/sys/leds/rgb/multi_index" to "red green blue"),
                writable = setOf("/sys/leds/rgb/multi_intensity"),
            )

        val descriptor = SysfsRgbDiscovery.discover(listOf(node("rgb", "/sys/leds/rgb")), access)

        requireNotNull(descriptor)
        assertEquals(1, descriptor.zones)
        assertEquals(SysfsColorKind.MULTI_INTENSITY_DECIMAL, descriptor.kind)
    }

    @Test
    fun `separate red green blue channels resolve to a single zone`() {
        val paths = listOf("red", "green", "blue").map { "/sys/leds/gamepad/$it" }
        val access = fakeAccess(files = paths.associateWith { "0" }, writable = paths.toSet())

        val descriptor = SysfsRgbDiscovery.discover(listOf(node("gamepad-rgb", "/sys/leds/gamepad")), access)

        requireNotNull(descriptor)
        assertEquals(1, descriptor.zones)
        assertEquals(SysfsColorKind.RGB_CHANNELS, descriptor.kind)
    }

    @Test
    fun `a clearly named status LED is excluded even when writable`() {
        val paths = listOf("red", "green", "blue").map { "/sys/leds/notif/$it" }
        val access = fakeAccess(files = paths.associateWith { "0" }, writable = paths.toSet())

        val descriptor =
            SysfsRgbDiscovery.discover(listOf(node("rgb:notification", "/sys/leds/notif")), access)

        assertNull(descriptor)
    }

    @Test
    fun `prefers an rgb named node over an unnamed one`() {
        val access =
            fakeAccess(
                files =
                    mapOf(
                        "/sys/leds/led1/multi_index" to "rgb",
                        "/sys/leds/rings/multi_index" to "rgb rgb",
                    ),
                writable = setOf("/sys/leds/led1/multi_intensity", "/sys/leds/rings/multi_intensity"),
            )

        val descriptor =
            SysfsRgbDiscovery.discover(
                listOf(node("led1", "/sys/leds/led1"), node("rgb:rings", "/sys/leds/rings")),
                access,
            )

        assertEquals("/sys/leds/rings", descriptor?.nodePath)
        assertEquals(2, descriptor?.zones)
    }

    @Test
    fun `skips a non writable node and reports no controllable surface`() {
        val access = fakeAccess(files = mapOf("/sys/leds/rings/multi_index" to "rgb"), writable = emptySet())

        val descriptor = SysfsRgbDiscovery.discover(listOf(node("rgb:rings", "/sys/leds/rings")), access)

        assertNull(descriptor)
    }

    private fun node(
        name: String,
        path: String,
    ) = SysfsLedNode(name, path)

    private fun fakeAccess(
        files: Map<String, String>,
        writable: Set<String>,
    ): SysfsAccess =
        object : SysfsAccess {
            override fun read(path: String): String? = files[path]

            override fun exists(path: String): Boolean = path in files || path in writable

            override fun canWrite(path: String): Boolean = path in writable

            override fun write(
                path: String,
                value: String,
            ): Boolean = path in writable
        }
}
