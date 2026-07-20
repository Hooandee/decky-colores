package com.hooandee.colores.ui

import com.hooandee.colores.device.LedPreviewCalibration
import com.hooandee.colores.device.LedPreviewHuePoint
import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

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

    private val activeProjection = LedColorProjection(rp5, enabled = true)

    @Test
    fun `missing profile preserves exact color`() {
        val color = RgbColor(255, 56, 0)

        assertEquals(color, color.applyPreviewCalibration(null))
    }

    @Test
    fun `projection is unavailable and exact without a device profile`() {
        val projection = LedColorProjection(profile = null, enabled = true)
        val source = RgbColor(255, 153, 0)

        assertFalse(projection.available)
        assertFalse(projection.active)
        assertEquals(0f, projection.glowAlpha)
        assertEquals(source, projection.display(source))
    }

    @Test
    fun `projection stays exact while its persisted toggle is disabled`() {
        val projection = LedColorProjection(profile = rp5, enabled = false)
        val source = RgbColor(255, 153, 0)

        assertTrue(projection.available)
        assertFalse(projection.active)
        assertEquals(0f, projection.glowAlpha)
        assertEquals(source, projection.display(source))
    }

    @Test
    fun `active projection centralizes calibrated color and glow`() {
        val projection = LedColorProjection(profile = rp5, enabled = true)
        val source = RgbColor(255, 153, 0)

        assertTrue(projection.active)
        assertEquals(rp5.glowAlpha, projection.glowAlpha)
        assertTrue(projection.display(source).toHsvColor().hue in 52f..60f)
    }

    @Test
    fun `calibrated wheel projects orange coordinate to observed yellow hue`() {
        val angle = Math.toRadians(36.0)

        val result =
            colorWheelDisplayAt(
                normalizedX = cos(angle).toFloat(),
                normalizedY = sin(angle).toFloat(),
                projection = activeProjection,
            )

        assertTrue(requireNotNull(result).toHsvColor().hue in 52f..60f)
    }

    @Test
    fun `wheel rejects coordinates outside its circle`() {
        assertEquals(null, colorWheelDisplayAt(1f, 1f, activeProjection))
    }

    @Test
    fun `calibrated wheel pixels stay transparent outside the circle`() {
        val pixels = calibratedColorWheelPixels(size = 101, projection = activeProjection)

        assertEquals(0, pixels.first())
        assertEquals(0, pixels.last())
    }

    @Test
    fun `calibrated wheel pixels project their visible hue`() {
        val size = 101
        val center = (size - 1) / 2f
        val radius = size / 2f
        val angle = Math.toRadians(36.0)
        val x = (center + cos(angle) * radius * 0.9).toInt()
        val y = (center + sin(angle) * radius * 0.9).toInt()
        val pixel = calibratedColorWheelPixels(size, activeProjection)[y * size + x]
        val color = RgbColor(pixel shr 16 and 0xFF, pixel shr 8 and 0xFF, pixel and 0xFF)

        assertTrue(color.toHsvColor().hue in 52f..60f)
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
