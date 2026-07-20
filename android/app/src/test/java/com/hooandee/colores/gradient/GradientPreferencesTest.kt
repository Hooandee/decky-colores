package com.hooandee.colores.gradient

import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradientPreferencesTest {
    private val red = RgbColor(255, 0, 0)
    private val blue = RgbColor(0, 0, 255)
    private val storage = mutableMapOf<String, String>()
    private val preferences = GradientPreferences(storage::get) { key, value -> storage[key] = value }

    @Test
    fun `persists and restores every editable field`() {
        val expected =
            DeviceGradientPreferences(
                mode = LightingMode.GRADIENT,
                currentStops = listOf(red, blue),
                lastPresetId = "neon",
                savedGradients = listOf(SavedGradient("Mío", listOf(blue, red))),
            )

        preferences.save("retroid-pocket-5", expected)

        assertEquals(expected, preferences.load("retroid-pocket-5"))
    }

    @Test
    fun `keeps preferences isolated by device`() {
        preferences.save("first", DeviceGradientPreferences(mode = LightingMode.GRADIENT, currentStops = listOf(red)))
        preferences.save("second", DeviceGradientPreferences(mode = LightingMode.COLOR, currentStops = listOf(blue)))

        assertEquals(listOf(red), preferences.load("first").currentStops)
        assertEquals(listOf(blue), preferences.load("second").currentStops)
        assertEquals(2, storage.size)
    }

    @Test
    fun `upsert replaces the same name and moves it to newest position`() {
        preferences.upsert("rp5", "Primero", listOf(red))
        preferences.upsert("rp5", "Segundo", listOf(blue))
        preferences.upsert("rp5", "Primero", listOf(blue, red))

        assertEquals(
            listOf(SavedGradient("Segundo", listOf(blue)), SavedGradient("Primero", listOf(blue, red))),
            preferences.load("rp5").savedGradients,
        )
    }

    @Test
    fun `upsert retains only the newest fifty gradients`() {
        repeat(51) { index -> preferences.upsert("rp5", "Gradiente $index", listOf(RgbColor(index, 0, 0))) }

        val saved = preferences.load("rp5").savedGradients

        assertEquals(50, saved.size)
        assertEquals("Gradiente 1", saved.first().name)
        assertEquals("Gradiente 50", saved.last().name)
    }

    @Test
    fun `delete removes only the matching saved gradient`() {
        preferences.upsert("rp5", "Uno", listOf(red))
        preferences.upsert("rp5", "Dos", listOf(blue))

        preferences.delete("rp5", "Uno")

        assertEquals(listOf("Dos"), preferences.load("rp5").savedGradients.map { it.name })
    }

    @Test
    fun `missing and malformed records return defaults`() {
        assertEquals(DeviceGradientPreferences(), preferences.load("missing"))
        storage["gradient:broken"] = "{"

        assertEquals(DeviceGradientPreferences(), preferences.load("broken"))
        assertNull(preferences.load("broken").lastPresetId)
    }
}
