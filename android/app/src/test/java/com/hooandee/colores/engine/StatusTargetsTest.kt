package com.hooandee.colores.engine

import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTargetsTest {
    private val bands = BandSet.FALLBACK

    @Test
    fun `a real zero percent battery is coloured, not treated as absent`() {
        assertEquals(RgbColor(255, 30, 20), StatusTargets.batteryTarget(0, bands.battery))
    }

    @Test
    fun `an absent battery has no target so the renderer holds`() {
        assertNull(StatusTargets.batteryTarget(null, bands.battery))
    }

    @Test
    fun `battery breathes only while charging below full with the toggle on`() {
        assertTrue(StatusTargets.batteryBreathing(charging = true, breatheEnabled = true, levelPercent = 40))
        assertFalse(StatusTargets.batteryBreathing(charging = true, breatheEnabled = true, levelPercent = 100))
        assertFalse(StatusTargets.batteryBreathing(charging = false, breatheEnabled = true, levelPercent = 40))
        assertFalse(StatusTargets.batteryBreathing(charging = true, breatheEnabled = false, levelPercent = 40))
        assertFalse(StatusTargets.batteryBreathing(charging = true, breatheEnabled = true, levelPercent = null))
    }

    @Test
    fun `temperature target is null when unreadable and coloured otherwise`() {
        assertNull(StatusTargets.temperatureTarget(null, bands.temperature))
        assertEquals(RgbColor(255, 200, 0), StatusTargets.temperatureTarget(72.0, bands.temperature))
    }

    @Test
    fun `temperature breathes only in the critical band`() {
        assertTrue(StatusTargets.temperatureBreathing(breatheEnabled = true, celsius = 95.0))
        assertFalse(StatusTargets.temperatureBreathing(breatheEnabled = true, celsius = 70.0))
        assertFalse(StatusTargets.temperatureBreathing(breatheEnabled = false, celsius = 95.0))
        assertFalse(StatusTargets.temperatureBreathing(breatheEnabled = true, celsius = null))
    }
}
