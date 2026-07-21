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
