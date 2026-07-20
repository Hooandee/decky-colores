package com.hooandee.colores.engine

import com.hooandee.colores.led.RgbColor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorBandsTest {
    private fun shared() = BandSet.parse(File("../../shared/bands.json").readText())

    @Test
    fun `parses ordered battery and temperature bands from the shared contract`() {
        val bands = shared()
        assertEquals(5, bands.battery.size)
        assertEquals(5, bands.temperature.size)
        assertTrue(bands.battery.zipWithNext().all { (a, b) -> a.min > b.min })
        assertTrue(bands.temperature.zipWithNext().all { (a, b) -> a.min > b.min })
        assertEquals(0.0, bands.battery.last().min, 0.0)
        assertEquals(0.0, bands.temperature.last().min, 0.0)
    }

    @Test
    fun `battery band colours match the shared thresholds`() {
        val bands = shared().battery
        assertEquals(RgbColor(0, 120, 255), Effects.bandColor(100.0, bands))
        assertEquals(RgbColor(0, 200, 60), Effects.bandColor(61.0, bands))
        assertEquals(RgbColor(255, 200, 0), Effects.bandColor(41.0, bands))
        assertEquals(RgbColor(255, 110, 0), Effects.bandColor(21.0, bands))
        assertEquals(RgbColor(255, 30, 20), Effects.bandColor(0.0, bands))
    }

    @Test
    fun `temperature critical band is red`() {
        val bands = shared().temperature
        assertEquals(RgbColor(255, 30, 20), Effects.bandColor(95.0, bands))
        assertEquals(RgbColor(0, 120, 255), Effects.bandColor(30.0, bands))
    }

    @Test
    fun `malformed json degrades to the built-in fallback bands`() {
        val bands = BandSet.parse("nonsense")
        assertEquals(BandSet.FALLBACK, bands)
    }
}
