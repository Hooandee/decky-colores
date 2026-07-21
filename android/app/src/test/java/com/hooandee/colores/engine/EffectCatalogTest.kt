package com.hooandee.colores.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectCatalogTest {
    private fun sharedCatalog() = EffectCatalog.parse(File("../../shared/effects.json").readText())

    @Test
    fun `parses every declared effect from the shared contract`() {
        val catalog = sharedCatalog()
        val ids = catalog.presets.map { it.id }
        assertEquals(
            listOf("breathing", "rainbow", "wave", "cycle", "spiral", "comet", "sparkle", "ripple", "aurora"),
            ids,
        )
    }

    @Test
    fun `maps the needs field to the palette requirement`() {
        val catalog = sharedCatalog()
        assertEquals(EffectNeed.COLOR, catalog.byId("breathing")!!.need)
        assertEquals(EffectNeed.NONE, catalog.byId("rainbow")!!.need)
        assertEquals(EffectNeed.GRADIENT, catalog.byId("wave")!!.need)
        assertEquals(EffectNeed.GRADIENT, catalog.byId("spiral")!!.need)
    }

    @Test
    fun `keeps a default speed and reference colours for each effect`() {
        val catalog = sharedCatalog()
        catalog.presets.forEach { preset ->
            assertTrue(preset.defaultSpeed in 0..100)
            assertTrue(preset.colors.isNotEmpty())
        }
    }

    @Test
    fun `malformed json degrades to an empty catalog`() {
        val catalog = EffectCatalog.parse("not json")
        assertTrue(catalog.presets.isEmpty())
        assertNull(catalog.byId("breathing"))
        assertEquals("breathing", catalog.defaultEffectId)
    }

    @Test
    fun `default effect id is the first preset`() {
        assertNotNull(sharedCatalog().byId(sharedCatalog().defaultEffectId))
        assertEquals("breathing", sharedCatalog().defaultEffectId)
    }
}
