package com.hooandee.colores.led

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SysfsRgbDeviceTest {
    @Test
    fun `hex multi intensity writes packed colors per zone and scaled brightness`() =
        runTest {
            val access = FakeSysfsAccess(writable = setOf("/n/multi_intensity", "/n/brightness"))
            val device =
                SysfsRgbDevice(
                    SysfsRgbDescriptor("/n", zones = 2, maxBrightness = 255, kind = SysfsColorKind.MULTI_INTENSITY_HEX),
                    access,
                    backgroundScope,
                )

            assertTrue(device.applyZones(listOf(RgbColor(255, 0, 0), RgbColor(0, 255, 0)), brightness = 100, power = true))
            runCurrent()

            assertEquals("0xFF0000 0x00FF00", access.values["/n/multi_intensity"])
            assertEquals("255", access.values["/n/brightness"])
        }

    @Test
    fun `decimal multi intensity writes per channel values`() =
        runTest {
            val access = FakeSysfsAccess(writable = setOf("/n/multi_intensity", "/n/brightness"))
            val device =
                SysfsRgbDevice(
                    SysfsRgbDescriptor("/n", zones = 2, maxBrightness = 255, kind = SysfsColorKind.MULTI_INTENSITY_DECIMAL),
                    access,
                    backgroundScope,
                )

            device.applyZones(listOf(RgbColor(10, 20, 30), RgbColor(40, 50, 60)), brightness = 50, power = true)
            runCurrent()

            assertEquals("10 20 30 40 50 60", access.values["/n/multi_intensity"])
            assertEquals("128", access.values["/n/brightness"])
        }

    @Test
    fun `rgb channels scale to max brightness`() =
        runTest {
            val access = FakeSysfsAccess(writable = setOf("/n/red", "/n/green", "/n/blue", "/n/brightness"))
            val device =
                SysfsRgbDevice(
                    SysfsRgbDescriptor("/n", zones = 1, maxBrightness = 100, kind = SysfsColorKind.RGB_CHANNELS),
                    access,
                    backgroundScope,
                )

            device.applySolid(RgbColor(255, 0, 0), brightness = 100, power = true)
            runCurrent()

            assertEquals("100", access.values["/n/red"])
            assertEquals("0", access.values["/n/green"])
            assertEquals("0", access.values["/n/blue"])
            assertEquals("100", access.values["/n/brightness"])
        }

    @Test
    fun `power off clears the channels and brightness`() =
        runTest {
            val access = FakeSysfsAccess(writable = setOf("/n/red", "/n/green", "/n/blue", "/n/brightness"))
            val device =
                SysfsRgbDevice(
                    SysfsRgbDescriptor("/n", zones = 1, maxBrightness = 100, kind = SysfsColorKind.RGB_CHANNELS),
                    access,
                    backgroundScope,
                )

            device.applySolid(RgbColor(255, 0, 0), brightness = 100, power = false)
            runCurrent()

            assertEquals("0", access.values["/n/red"])
            assertEquals("0", access.values["/n/brightness"])
        }

    @Test
    fun `multi intensity follows multi index and scales to max brightness`() =
        runTest {
            val access = FakeSysfsAccess(writable = setOf("/n/multi_intensity", "/n/brightness"))
            val device =
                SysfsRgbDevice(
                    SysfsRgbDescriptor(
                        "/n",
                        zones = 1,
                        maxBrightness = 100,
                        kind = SysfsColorKind.MULTI_INTENSITY_DECIMAL,
                        multiIndex = listOf("white", "green", "red", "blue", "amber"),
                    ),
                    access,
                    backgroundScope,
                )

            device.applySolid(RgbColor(255, 51, 0), brightness = 100, power = true)
            runCurrent()

            assertEquals("0 20 100 0 0", access.values["/n/multi_intensity"])
            assertEquals("100", access.values["/n/brightness"])
        }

    @Test
    fun `separate channel nodes fold brightness into each channel`() =
        runTest {
            val nodes = listOf("/sys/class/leds/s:red", "/sys/class/leds/s:green", "/sys/class/leds/s:blue")
            val access = FakeSysfsAccess(writable = nodes.map { "$it/brightness" }.toSet())
            val device =
                SysfsRgbDevice(
                    SysfsRgbDescriptor(nodes.first(), 1, 200, SysfsColorKind.CHANNEL_NODES, channelNodes = nodes),
                    access,
                    backgroundScope,
                )

            assertTrue(device.available)
            device.applySolid(RgbColor(255, 0, 51), brightness = 50, power = true)
            runCurrent()

            assertEquals(listOf("100", "0", "20"), nodes.map { access.values["$it/brightness"] })
        }

    @Test
    fun `composite descriptor splits zones across member nodes`() =
        runTest {
            val left = SysfsRgbDescriptor("/l", 1, 255, SysfsColorKind.MULTI_INTENSITY_DECIMAL)
            val right = SysfsRgbDescriptor("/r", 2, 255, SysfsColorKind.MULTI_INTENSITY_HEX)
            val access = FakeSysfsAccess(writable = setOf("/l/multi_intensity", "/l/brightness", "/r/multi_intensity", "/r/brightness"))
            val device =
                SysfsRgbDevice(
                    SysfsRgbDescriptor("/l", 3, 255, SysfsColorKind.COMPOSITE, members = listOf(left, right)),
                    access,
                    backgroundScope,
                )

            device.applyZones(listOf(RgbColor(1, 2, 3), RgbColor(255, 0, 0), RgbColor(0, 0, 255)), brightness = 100, power = true)
            runCurrent()

            assertTrue(device.supportsPerZone)
            assertEquals("1 2 3", access.values["/l/multi_intensity"])
            assertEquals("0xFF0000 0x0000FF", access.values["/r/multi_intensity"])
            assertEquals("255", access.values["/r/brightness"])
        }

    @Test
    fun `is unavailable when the color node is not writable`() {
        val access = FakeSysfsAccess(writable = emptySet())
        val device =
            SysfsRgbDevice(
                SysfsRgbDescriptor("/n", zones = 1, maxBrightness = 255, kind = SysfsColorKind.MULTI_INTENSITY_HEX),
                access,
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            )

        assertFalse(device.available)
    }
}
