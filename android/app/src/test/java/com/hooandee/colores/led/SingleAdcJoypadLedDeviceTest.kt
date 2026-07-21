package com.hooandee.colores.led

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SingleAdcJoypadLedDeviceTest {
    private val base = "/sys/bus/platform/devices/singleadc-joypad"
    private val nodes =
        listOf("custum_rgb_r", "custum_rgb_g", "custum_rgb_b", "led_level", "led_mode", "led_switch", "led_set")
            .map { "$base/$it" }
            .toSet()

    @Test
    fun `static color writes custum rgb, level, mode and latches led_set`() =
        runTest {
            val access = FakeJoypadAccess(nodes)
            val device = SingleAdcJoypadLedDevice(SingleAdcJoypadDescriptor(base), access, backgroundScope)

            device.applySolid(RgbColor(255, 128, 0), brightness = 80, power = true)
            runCurrent()

            assertEquals("255", access.values["$base/custum_rgb_r"])
            assertEquals("128", access.values["$base/custum_rgb_g"])
            assertEquals("0", access.values["$base/custum_rgb_b"])
            assertEquals("80", access.values["$base/led_level"])
            assertEquals("1", access.values["$base/led_mode"])
            assertEquals("1", access.values["$base/led_switch"])
            assertEquals("1", access.values["$base/led_set"])
        }

    @Test
    fun `power off drops the switch and still latches`() =
        runTest {
            val access = FakeJoypadAccess(nodes)
            val device = SingleAdcJoypadLedDevice(SingleAdcJoypadDescriptor(base), access, backgroundScope)

            device.applySolid(RgbColor(255, 0, 0), brightness = 100, power = false)
            runCurrent()

            assertEquals("0", access.values["$base/led_switch"])
            assertEquals("1", access.values["$base/led_set"])
            assertNull(access.values["$base/custum_rgb_r"])
        }

    @Test
    fun `colored hardware effect writes its led mode, speed and color`() =
        runTest {
            val access = FakeJoypadAccess(nodes + setOf("$base/led_speed", "$base/Led_rgb_r1", "$base/Led_rgb_g1", "$base/Led_rgb_b1", "$base/Led_rgb_r2", "$base/Led_rgb_g2", "$base/Led_rgb_b2"))
            val device = SingleAdcJoypadLedDevice(SingleAdcJoypadDescriptor(base), access, backgroundScope)

            assertTrue(device.applyHardwareEffect("breathing", RgbColor(10, 20, 30), brightness = 100, speed = 100, power = true))
            runCurrent()

            assertEquals("2", access.values["$base/led_mode"])
            assertEquals("8", access.values["$base/led_speed"])
            assertEquals("10", access.values["$base/custum_rgb_r"])
            assertEquals("10", access.values["$base/Led_rgb_r1"])
            assertEquals("30", access.values["$base/Led_rgb_b2"])
            assertEquals("1", access.values["$base/led_set"])
        }

    @Test
    fun `fixed hardware effect zeroes the zone colors`() =
        runTest {
            val access = FakeJoypadAccess(nodes + setOf("$base/led_speed", "$base/Led_rgb_r1", "$base/Led_rgb_g1", "$base/Led_rgb_b1", "$base/Led_rgb_r2", "$base/Led_rgb_g2", "$base/Led_rgb_b2"))
            val device = SingleAdcJoypadLedDevice(SingleAdcJoypadDescriptor(base), access, backgroundScope)

            device.applyHardwareEffect("marquee", RgbColor(255, 0, 0), brightness = 100, speed = 50, power = true)
            runCurrent()

            assertEquals("4", access.values["$base/led_mode"])
            assertEquals("0", access.values["$base/Led_rgb_r1"])
        }

    @Test
    fun `unknown hardware effect is rejected`() =
        runTest {
            val access = FakeJoypadAccess(nodes)
            val device = SingleAdcJoypadLedDevice(SingleAdcJoypadDescriptor(base), access, backgroundScope)
            assertFalse(device.applyHardwareEffect("nope", RgbColor(1, 2, 3), 100, 50, true))
        }

    @Test
    fun `exposes anbernic hardware effects`() {
        val device =
            SingleAdcJoypadLedDevice(
                SingleAdcJoypadDescriptor(base),
                FakeJoypadAccess(nodes),
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            )
        assertEquals(listOf("breathing", "rainbow", "marquee", "chasing", "gaming"), device.hardwareEffects.map { it.id })
        assertTrue(device.hardwareEffects.first { it.id == "breathing" }.needsColor)
        assertFalse(device.hardwareEffects.first { it.id == "marquee" }.needsColor)
    }

    @Test
    fun `reports a single zone and no per zone`() {
        val device =
            SingleAdcJoypadLedDevice(
                SingleAdcJoypadDescriptor(base),
                FakeJoypadAccess(nodes),
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            )
        assertFalse(device.supportsPerZone)
        assertTrue(device.available)
    }

    @Test
    fun `is unavailable when the nodes are not writable`() {
        val device =
            SingleAdcJoypadLedDevice(
                SingleAdcJoypadDescriptor(base),
                FakeJoypadAccess(emptySet()),
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            )
        assertFalse(device.available)
    }
}

private class FakeJoypadAccess(
    private val writable: Set<String>,
    val values: MutableMap<String, String> = mutableMapOf(),
) : SysfsAccess {
    override fun read(path: String): String? = values[path]

    override fun exists(path: String): Boolean = path in writable || path in values

    override fun canWrite(path: String): Boolean = path in writable

    override fun write(
        path: String,
        value: String,
    ): Boolean {
        if (path !in writable) return false
        values[path] = value.trim()
        return true
    }
}
