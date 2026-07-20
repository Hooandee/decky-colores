package com.hooandee.colores.control

import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Test

class LightingPreferencesTest {
    private fun preferences(): Pair<LightingPreferences, MutableMap<String, String>> {
        val store = mutableMapOf<String, String>()
        return LightingPreferences(read = { store[it] }, write = { key, value -> store[key] = value }) to store
    }

    @Test
    fun `round trips the full lighting intent per device`() {
        val (prefs, _) = preferences()
        val stored =
            StoredLighting(
                mode = AppMode.PERFORMANCE,
                effectId = "wave",
                speed = 73,
                solidColor = RgbColor(12, 34, 56),
                chargerOnly = true,
                batteryBreathe = false,
            )
        prefs.save("rp5", stored)
        assertEquals(stored, prefs.load("rp5"))
    }

    @Test
    fun `an unseen device restores defaults`() {
        val (prefs, _) = preferences()
        assertEquals(StoredLighting(), prefs.load("unknown"))
    }

    @Test
    fun `a dynamic mode is restored, not collapsed to solid color`() {
        val (prefs, _) = preferences()
        prefs.save("rp5", StoredLighting(mode = AppMode.CLOCK, effectId = "spiral"))
        assertEquals(AppMode.CLOCK, prefs.load("rp5").mode)
        assertEquals("spiral", prefs.load("rp5").effectId)
    }

    @Test
    fun `corrupt json degrades to defaults`() {
        val prefs = LightingPreferences(read = { "not json" }, write = { _, _ -> })
        assertEquals(StoredLighting(), prefs.load("rp5"))
    }

    @Test
    fun `devices keep independent lighting intents`() {
        val (prefs, _) = preferences()
        prefs.save("a", StoredLighting(mode = AppMode.EFFECT, effectId = "comet"))
        prefs.save("b", StoredLighting(mode = AppMode.BATTERY))
        assertEquals(AppMode.EFFECT, prefs.load("a").mode)
        assertEquals(AppMode.BATTERY, prefs.load("b").mode)
    }
}
