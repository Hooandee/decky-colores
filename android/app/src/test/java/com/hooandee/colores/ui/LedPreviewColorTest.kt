package com.hooandee.colores.ui

import com.hooandee.colores.device.LedPreviewCalibration
import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedPreviewColorTest {
    private val rp5 =
        LedPreviewCalibration(
            saturationScale = 0.84f,
            whiteMix = 0.08f,
            redGain = 1f,
            greenGain = 1.08f,
            blueGain = 1f,
            valueGamma = 0.95f,
            glowAlpha = 0.22f,
        )

    @Test
    fun `missing profile preserves exact color`() {
        val color = RgbColor(255, 56, 0)

        assertEquals(color, color.applyPreviewCalibration(null))
    }

    @Test
    fun `RP5 profile softens orange without mutating the source`() {
        val source = RgbColor(255, 56, 0)
        val result = source.applyPreviewCalibration(rp5)

        assertEquals(RgbColor(255, 56, 0), source)
        assertEquals(255, result.red)
        assertTrue(result.green > source.green)
        assertTrue(result.blue > source.blue)
        assertTrue(result.toHsvColor().saturation < source.toHsvColor().saturation)
    }

    @Test
    fun `calibration clamps every output channel`() {
        val boosted = rp5.copy(redGain = 2f, greenGain = 2f, blueGain = 2f)

        assertEquals(
            RgbColor(255, 255, 255),
            RgbColor(255, 255, 255).applyPreviewCalibration(boosted),
        )
    }
}
