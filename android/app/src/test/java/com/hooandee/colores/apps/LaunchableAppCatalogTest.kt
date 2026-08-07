package com.hooandee.colores.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchableAppCatalogTest {
    private val raw =
        listOf(
            RawLaunchableApp("org.citra.emu", "Citra"),
            RawLaunchableApp("app.gamenative", "GameNative"),
            RawLaunchableApp("org.citra.emu", "Citra Canary"),
            RawLaunchableApp("com.hooandee.colores", "Colores"),
        )

    @Test
    fun `catalog deduplicates activities and excludes Colores`() {
        val result = normalizeApps(raw, ownPackage = "com.hooandee.colores")

        assertEquals(listOf("org.citra.emu", "app.gamenative"), result.map { it.packageName })
    }

    @Test
    fun `configured applications sort first and search ignores case`() {
        val normalized = normalizeApps(raw, "com.hooandee.colores", setOf("app.gamenative"))

        assertEquals("app.gamenative", normalized.first().packageName)
        assertEquals(listOf("org.citra.emu"), filterApps(normalized, "CITRA").map { it.packageName })
    }

    @Test
    fun `blank labels fall back to package`() {
        val result = normalizeApps(listOf(RawLaunchableApp("org.game", "")), "own")

        assertEquals("org.game", result.single().label)
    }
}
