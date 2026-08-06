package com.hooandee.colores.engine

import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AudioScaleTest {
    private val green = RgbColor(0, 230, 90)
    private val yellow = RgbColor(255, 200, 0)
    private val red = RgbColor(255, 40, 0)

    @Test
    fun `classic scale reaches its three colors at the configured thresholds`() {
        val scale = AudioScale.DEFAULT

        assertEquals(green, scale.colorAt(0.0))
        assertEquals(yellow, scale.colorAt(0.5))
        assertEquals(red, scale.colorAt(1.0))
    }

    @Test
    fun `custom thresholds move the exact medium and peak colors`() {
        val scale = AudioScale(green, yellow, red, mediumAt = 25, peakAt = 75)

        assertEquals(yellow, scale.colorAt(0.25))
        assertEquals(red, scale.colorAt(0.75))
        assertEquals(red, scale.colorAt(1.0))
    }

    @Test
    fun `thresholds must stay ordered inside the meter range`() {
        assertThrows(IllegalArgumentException::class.java) {
            AudioScale(green, yellow, red, mediumAt = 80, peakAt = 80)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioScale(green, yellow, red, mediumAt = 0, peakAt = 90)
        }
    }
}
