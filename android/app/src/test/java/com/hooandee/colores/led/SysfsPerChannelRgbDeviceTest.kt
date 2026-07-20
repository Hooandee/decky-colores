package com.hooandee.colores.led

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SysfsPerChannelRgbDeviceTest {
    private val descriptor =
        SettingsProviderDescriptor(
            driver = "sysfs_rgb",
            transport = "direct",
            colorKey = "",
            colorFormat = "sysfs_channels",
            brightnessKey = "",
            brightnessRange = 0f..1f,
            enableKeys = emptyList(),
            zones = 1,
            requiresPermission = null,
            vendorService = "",
            sysfs = SysfsRgbDescriptor("/sys/class/leds", "red", "green", "blue", 255),
        )

    private class FakeFs {
        val values = mutableMapOf("/sys/class/leds/red/brightness" to 0, "/sys/class/leds/green/brightness" to 0, "/sys/class/leds/blue/brightness" to 0)
        val writable = mutableSetOf(*values.keys.toTypedArray())

        fun write(
            path: String,
            value: Int,
        ): Boolean {
            if (path !in writable) return false
            values[path] = value
            return true
        }

        fun read(path: String): Int? = values[path]

        fun canWrite(path: String): Boolean = path in writable
    }

    private fun device(
        fs: FakeFs,
        scope: kotlinx.coroutines.CoroutineScope,
    ) = SysfsPerChannelRgbDevice(
        descriptor = descriptor,
        hardware = descriptor.sysfs!!,
        writeChannel = fs::write,
        readChannel = fs::read,
        channelWritable = fs::canWrite,
        scope = scope,
    )

    @Test
    fun `available only when all channels are writable`() =
        runTest {
            val fs = FakeFs()
            assertTrue(device(fs, backgroundScope).available)
            fs.writable.remove("/sys/class/leds/blue/brightness")
            assertFalse(device(fs, backgroundScope).available)
        }

    @Test
    fun `applies a color scaled by brightness across the three channels`() =
        runTest {
            val fs = FakeFs()
            val device = device(fs, backgroundScope)
            device.applyZones(listOf(RgbColor(255, 128, 0)), brightness = 100, power = true)
            runCurrent()
            assertEquals(255, fs.values["/sys/class/leds/red/brightness"])
            assertEquals(128, fs.values["/sys/class/leds/green/brightness"])
            assertEquals(0, fs.values["/sys/class/leds/blue/brightness"])

            device.applyZones(listOf(RgbColor(255, 128, 0)), brightness = 50, power = true)
            advanceTimeBy(40)
            runCurrent()
            assertEquals(128, fs.values["/sys/class/leds/red/brightness"])
            assertEquals(64, fs.values["/sys/class/leds/green/brightness"])
        }

    @Test
    fun `power off writes zero to every channel without losing the requested color`() =
        runTest {
            val fs = FakeFs()
            val device = device(fs, backgroundScope)
            fs.values["/sys/class/leds/red/brightness"] = 99
            device.applyZones(listOf(RgbColor(200, 100, 50)), brightness = 100, power = false)
            runCurrent()
            assertEquals(0, fs.values["/sys/class/leds/red/brightness"])
            assertEquals(0, fs.values["/sys/class/leds/green/brightness"])
            assertEquals(0, fs.values["/sys/class/leds/blue/brightness"])
        }

    @Test
    fun `reads the current channel values back as a color`() =
        runTest {
            val fs = FakeFs()
            fs.values["/sys/class/leds/red/brightness"] = 255
            fs.values["/sys/class/leds/green/brightness"] = 0
            fs.values["/sys/class/leds/blue/brightness"] = 128
            val state = device(fs, backgroundScope).readState()
            assertEquals(RgbColor(255, 0, 128), state.zoneColors.first())
            assertTrue(state.power)
        }

    @Test
    fun `is single zone`() =
        runTest {
            assertFalse(device(FakeFs(), backgroundScope).supportsPerZone)
        }
}
