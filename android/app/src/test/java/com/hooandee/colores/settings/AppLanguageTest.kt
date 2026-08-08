package com.hooandee.colores.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `language tags map to supported choices`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag(""))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromLanguageTag("es-ES"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en-US"))
    }

    @Test
    fun `language choices expose the locale tags used by Android`() {
        assertEquals("", AppLanguage.SYSTEM.languageTag)
        assertEquals("es", AppLanguage.SPANISH.languageTag)
        assertEquals("en", AppLanguage.ENGLISH.languageTag)
    }
}
