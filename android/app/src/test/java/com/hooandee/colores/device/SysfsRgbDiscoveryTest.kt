package com.hooandee.colores.device

import com.hooandee.colores.led.FakeSysfsAccess
import com.hooandee.colores.led.SysfsAccess
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import org.junit.Assert.assertTrue
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

    @Test
    fun `prefixed per channel nodes become one rgb surface`() {
        val names = listOf("stick:red", "stick:green", "stick:blue")
        val access =
            fakeAccess(
                files = names.associate { "/sys/class/leds/$it/max_brightness" to "31" },
                writable = names.map { "/sys/class/leds/$it/brightness" }.toSet(),
            )

        val descriptor = requireNotNull(SysfsRgbDiscovery.discover(names.map { node(it, "/sys/class/leds/$it") }, access))

        assertEquals(SysfsColorKind.CHANNEL_NODES, descriptor.kind)
        assertEquals(names.map { "/sys/class/leds/$it" }, descriptor.channelNodes)
        assertEquals(31, descriptor.maxBrightness)
    }

    @Test
    fun `bare red green blue nodes are proposed after every other surface`() {
        val bare = listOf("red", "green", "blue")
        val access =
            fakeAccess(
                files = mapOf("/sys/class/leds/rgb:rings/multi_index" to "red green blue"),
                writable = bare.map { "/sys/class/leds/$it/brightness" }.toSet() + "/sys/class/leds/rgb:rings/multi_intensity",
            )

        val all =
            SysfsRgbDiscovery.discoverAll(
                (bare + "rgb:rings").map { node(it, "/sys/class/leds/$it") },
                access,
            )

        assertEquals(listOf(SysfsColorKind.MULTI_INTENSITY_DECIMAL, SysfsColorKind.CHANNEL_NODES), all.map { it.kind })
        assertTrue(SysfsRgbDiscovery.isUnprefixedChannelGroup(all.last()))
    }

    @Test
    fun `notification channel nodes are never proposed`() {
        val names = listOf("notification:red", "notification:green", "notification:blue")
        val access = fakeAccess(files = emptyMap(), writable = names.map { "/sys/class/leds/$it/brightness" }.toSet())

        assertTrue(SysfsRgbDiscovery.discoverAll(names.map { node(it, "/sys/class/leds/$it") }, access).isEmpty())
    }

    @Test
    fun `reordered and extra channels keep an explicit multi index`() {
        val access =
            fakeAccess(
                files = mapOf("/sys/leds/rgb/multi_index" to "white green red blue green red blue white"),
                writable = setOf("/sys/leds/rgb/multi_intensity"),
            )

        val descriptor = requireNotNull(SysfsRgbDiscovery.discover(listOf(node("rgb", "/sys/leds/rgb")), access))

        assertEquals(2, descriptor.zones)
        assertEquals(listOf("white", "green", "red", "blue", "green", "red", "blue", "white"), descriptor.multiIndex)
    }

    @Test
    fun `standard multi index keeps the legacy descriptor shape`() {
        val access =
            fakeAccess(
                files = mapOf("/sys/leds/rgb/multi_index" to "red green blue red green blue"),
                writable = setOf("/sys/leds/rgb/multi_intensity"),
            )

        val descriptor = SysfsRgbDiscovery.discover(listOf(node("rgb", "/sys/leds/rgb")), access)

        assertEquals(SysfsRgbDescriptor("/sys/leds/rgb", 2, 255, SysfsColorKind.MULTI_INTENSITY_DECIMAL), descriptor)
    }

    @Test
    fun `uninterpretable multi index produces no candidate`() {
        listOf("", "red green", "rgb red", "red green blue blue", "0x1 0x2").forEach { index ->
            val access =
                fakeAccess(
                    files = mapOf("/sys/leds/rgb/multi_index" to index),
                    writable = setOf("/sys/leds/rgb/multi_intensity"),
                )

            assertNull(index, SysfsRgbDiscovery.discover(listOf(node("rgb", "/sys/leds/rgb")), access))
        }
    }

    @Test
    fun `left and right nodes also offer one composite surface`() {
        val access =
            fakeAccess(
                files =
                    mapOf(
                        "/sys/class/leds/left-stick/multi_index" to "red green blue",
                        "/sys/class/leds/right-stick/multi_index" to "rgb rgb",
                    ),
                writable = setOf("/sys/class/leds/left-stick/multi_intensity", "/sys/class/leds/right-stick/multi_intensity"),
            )

        val all =
            SysfsRgbDiscovery.discoverAll(
                listOf(node("left-stick", "/sys/class/leds/left-stick"), node("right-stick", "/sys/class/leds/right-stick")),
                access,
            )

        assertEquals(3, all.size)
        val composite = all.last()
        assertEquals(SysfsColorKind.COMPOSITE, composite.kind)
        assertEquals(3, composite.zones)
        assertEquals(all.take(2), composite.members)
        assertEquals(SysfsColorKind.MULTI_INTENSITY_DECIMAL, SysfsRgbDiscovery.discover(listOf(node("left-stick", "/sys/class/leds/left-stick")), access)?.kind)
    }

    private fun node(
        name: String,
        path: String,
    ) = SysfsLedNode(name, path)

    private fun fakeAccess(
        files: Map<String, String>,
        writable: Set<String>,
    ): SysfsAccess = FakeSysfsAccess(writable, files.toMutableMap())
}
