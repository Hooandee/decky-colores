package com.hooandee.colores.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class PcmLevelMeterTest {
    @Test
    fun `silence maps to zero`() {
        assertEquals(0.0, PcmLevelMeter.level(shortArrayOf(0, 0, 0), 3), 0.000001)
    }

    @Test
    fun `pcm uses the forty decibel range against Android sixteen bit full scale`() {
        assertEquals(0.0, PcmLevelMeter.level(ShortArray(32) { 327 }, 32), 0.000001)
        assertEquals(0.5, PcmLevelMeter.level(ShortArray(32) { 3_277 }, 32), 0.0001)
        assertEquals(0.6939, PcmLevelMeter.level(ShortArray(32) { 8_000 }, 32), 0.0001)
        assertEquals(1.0, PcmLevelMeter.level(ShortArray(32) { Short.MIN_VALUE }, 32), 0.000001)
    }

    @Test
    fun `only the reported sample count contributes to rms`() {
        assertEquals(1.0, PcmLevelMeter.level(shortArrayOf(Short.MIN_VALUE, Short.MIN_VALUE, 0, 0), 2), 0.000001)
        assertEquals(0.0, PcmLevelMeter.level(shortArrayOf(Short.MIN_VALUE), 0), 0.000001)
    }

    @Test
    fun `easing has fast attack and slow release`() {
        assertEquals(0.6, PcmLevelMeter.ease(0.0, 1.0), 0.000001)
        assertEquals(0.8, PcmLevelMeter.ease(1.0, 0.0), 0.000001)
    }

    @Test
    fun `level and easing inputs are clamped`() {
        assertEquals(0.0, PcmLevelMeter.ease(-1.0, -2.0), 0.000001)
        assertEquals(1.0, PcmLevelMeter.ease(2.0, 3.0), 0.000001)
        assertEquals(0.0, PcmLevelMeter.level(shortArrayOf(1), -4), 0.000001)
    }
}
