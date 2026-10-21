package com.hooandee.colores.engine

import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ColorCalibrationTest {
    private val thor = ColorCalibration(yellow = 37, orange = -56)

    @Test
    fun `neutral calibration keeps colors`() {
        val colors = listOf(RgbColor(255, 128, 0))
        assertSame(colors, ColorCalibration.NEUTRAL.apply(colors))
    }

    @Test
    fun `thor yellow and orange are fixed together`() {
        assertEquals(RgbColor(161, 255, 0), thor.apply(RgbColor(255, 255, 0)))
        assertEquals(RgbColor(161, 81, 0), thor.apply(RgbColor(255, 128, 0)))
    }

    @Test
    fun `white step warms by lowering blue and cools by lowering red and green`() {
        assertEquals(RgbColor(255, 255, 204), ColorCalibration(white = -20).apply(RgbColor(255, 255, 255)))
        assertEquals(RgbColor(204, 204, 255), ColorCalibration(white = 20).apply(RgbColor(255, 255, 255)))
    }

    @Test
    fun `rounding matches decky half up`() {
        assertEquals(RgbColor(0, 0, 127), ColorCalibration(white = -50).apply(RgbColor(0, 0, 253)))
    }

    @Test
    fun `steps are clamped to their range`() {
        assertEquals(ColorCalibration(60, -100, 10), ColorCalibration.of(80, -150, 10))
        assertEquals(ColorCalibration(yellow = 60), ColorCalibration.NEUTRAL.with(ColorCalibration.Step.YELLOW, 99))
    }
}
