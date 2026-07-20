package com.hooandee.colores.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedPreviewPreferencesTest {
    @Test
    fun `preference defaults off and persists independently per device`() {
        val values = mutableMapOf<String, Boolean>()
        val preferences =
            LedPreviewPreferences(
                read = values::get,
                write = { key, value -> values[key] = value },
            )

        assertFalse(preferences.isEnabled("retroid-pocket-5"))
        preferences.setEnabled("retroid-pocket-5", true)

        assertTrue(preferences.isEnabled("retroid-pocket-5"))
        assertFalse(preferences.isEnabled("other-device"))
    }
}
