package com.hooandee.colores.ui

import com.hooandee.colores.device.LedPreviewCalibration
import com.hooandee.colores.device.LedPreviewHuePoint
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
            hueMap =
                listOf(
                    LedPreviewHuePoint(0f, 0f),
                    LedPreviewHuePoint(13f, 30f),
                    LedPreviewHuePoint(36f, 56f),
                    LedPreviewHuePoint(60f, 60f),
                    LedPreviewHuePoint(120f, 120f),
                    LedPreviewHuePoint(180f, 180f),
                    LedPreviewHuePoint(240f, 240f),
                    LedPreviewHuePoint(300f, 300f),
                    LedPreviewHuePoint(347f, 315f),
                ),
        )

    @Test
    fun `missing profile preserves exact color`() {
        val color = RgbColor(255, 56, 0)

        assertEquals(color, color.applyPreviewCalibration(null))
    }

    @Test
    fun `RP5 profile maps orange to the observed yellow hue without mutating the source`() {
        val source = RgbColor(255, 153, 0)
        val result = source.applyPreviewCalibration(rp5)

        assertEquals(RgbColor(255, 153, 0), source)
        assertTrue(result.toHsvColor().hue in 52f..60f)
    }

    @Test
    fun `RP5 profile maps pink red to the observed magenta hue`() {
        val result = RgbColor(255, 0, 56).applyPreviewCalibration(rp5)

        assertTrue(result.toHsvColor().hue in 310f..320f)
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
