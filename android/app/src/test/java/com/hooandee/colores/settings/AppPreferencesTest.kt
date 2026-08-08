package com.hooandee.colores.settings

import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun `defaults to system theme and Colores accent`() {
        val preferences = AppPreferences(read = { null }, write = { _, _ -> })

        assertEquals(ThemeMode.SYSTEM, preferences.appearance.value.themeMode)
        assertEquals(RgbColor(141, 131, 255), preferences.appearance.value.accent)
    }

    @Test
    fun `invalid persisted values fall back safely`() {
        val stored = mapOf("theme_mode" to "NEON", "accent" to "not-a-color")
        val preferences = AppPreferences(read = stored::get, write = { _, _ -> })

        assertEquals(ThemeMode.SYSTEM, preferences.appearance.value.themeMode)
        assertEquals(RgbColor(141, 131, 255), preferences.appearance.value.accent)
    }

    @Test
    fun `theme and accent changes persist and update observable state`() {
        val stored = mutableMapOf<String, String>()
        val preferences = AppPreferences(read = stored::get, write = stored::put)

        preferences.setThemeMode(ThemeMode.LIGHT)
        preferences.setAccent(RgbColor(10, 260, -5))

        assertEquals("LIGHT", stored["theme_mode"])
        assertEquals("#0AFF00", stored["accent"])
        assertEquals(ThemeMode.LIGHT, preferences.appearance.value.themeMode)
        assertEquals(RgbColor(10, 255, 0), preferences.appearance.value.accent)
    }
}
