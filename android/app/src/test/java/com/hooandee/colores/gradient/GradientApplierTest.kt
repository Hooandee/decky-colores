package com.hooandee.colores.gradient

import com.hooandee.colores.led.LedDevice
import com.hooandee.colores.led.LedState
import com.hooandee.colores.led.RgbColor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradientApplierTest {
    private val red = RgbColor(255, 0, 0)
    private val blue = RgbColor(0, 0, 255)

    @Test
    fun `applies exactly the detected number of zones without changing brightness or power`() =
        runTest {
            val device = RecordingLedDevice()
            val current = LedState(listOf(red, blue), brightness = 37, power = false)

            assertTrue(GradientApplier(device).apply(listOf(red, blue), zones = 4, current = current))

            assertEquals(
                LedState(
                    listOf(red, RgbColor(170, 0, 85), RgbColor(85, 0, 170), blue),
                    brightness = 37,
                    power = false,
                ),
                device.applied,
            )
        }

    @Test
    fun `passes raw RGB directly to LedDevice`() =
        runTest {
            val device = RecordingLedDevice()
            val raw = listOf(RgbColor(255, 38, 0), RgbColor(0, 108, 255))

            GradientApplier(device).apply(raw, zones = 2, current = LedState(raw, 100, true))

            assertEquals(raw, device.applied?.zoneColors)
        }
}

private class RecordingLedDevice : LedDevice {
    var applied: LedState? = null

    override val available = true
    override val supportsPerZone = true

    override suspend fun readState() = LedState(emptyList(), 100, true)

    override suspend fun applyZones(
        colors: List<RgbColor>,
        brightness: Int,
        power: Boolean,
    ): Boolean {
        applied = LedState(colors, brightness, power)
        return true
    }

    override suspend fun applySolid(
        color: RgbColor,
        brightness: Int,
        power: Boolean,
    ) = applyZones(listOf(color), brightness, power)

    override fun invalidate() = Unit
}
