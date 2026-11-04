package com.hooandee.colores.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenBrightnessTest {
    @Test
    fun `slider level follows the android gamma curve`() {
        assertEquals(0f, ScreenBrightness.sliderLevel(10, 10, 255), 0.001f)
        assertEquals(1f, ScreenBrightness.sliderLevel(255, 10, 255), 0.001f)
        assertEquals(0.5f, ScreenBrightness.sliderLevel(30, 10, 255), 0.03f)
        assertTrue(ScreenBrightness.sliderLevel(64, 10, 255) > 64f / 255f)
    }

    @Test
    fun `slider level clamps values outside the configured range`() {
        assertEquals(0f, ScreenBrightness.sliderLevel(1, 10, 255), 0f)
        assertEquals(1f, ScreenBrightness.sliderLevel(400, 10, 255), 0f)
        assertEquals(1f, ScreenBrightness.sliderLevel(100, 255, 255), 0f)
    }

    @Test
    fun `led brightness scales the user maximum with a visible floor`() {
        assertEquals(80, ScreenBrightness.ledBrightness(80, 1f))
        assertEquals(40, ScreenBrightness.ledBrightness(80, 0.5f))
        assertEquals(10, ScreenBrightness.ledBrightness(100, 0f))
        assertEquals(1, ScreenBrightness.ledBrightness(5, 0f))
        assertEquals(0, ScreenBrightness.ledBrightness(0, 1f))
    }

    @Test
    fun `ramp eases monotonically to its target`() {
        val ramp = FactorRamp(durationMs = 400)
        assertTrue(ramp.settled(0))
        ramp.setTarget(0.2f, 1_000)
        assertFalse(ramp.settled(1_000))
        val values = (0..8).map { ramp.value(1_000L + it * 50L) }
        assertEquals(1f, values.first(), 0f)
        assertTrue(values.zipWithNext().all { (a, b) -> a >= b })
        assertEquals(0.2f, values.last(), 0.0001f)
        assertTrue(ramp.settled(1_400))
    }

    @Test
    fun `retargeting mid ramp continues from the current value`() {
        val ramp = FactorRamp(durationMs = 400)
        ramp.setTarget(0f, 0)
        val mid = ramp.value(200)
        ramp.setTarget(1f, 200)
        assertEquals(mid, ramp.value(200), 0.0001f)
        assertEquals(1f, ramp.value(600), 0.0001f)
    }
}
