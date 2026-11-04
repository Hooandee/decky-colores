package com.hooandee.colores.profiles

import com.hooandee.colores.control.AppMode
import com.hooandee.colores.led.RgbColor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedStylesTest {
    private val shared = File("../../shared/golden/styles.json").readText()

    @Test
    fun `reads the shared document written by Decky`() {
        val (styles, skipped) = SavedStyles.importDocument(shared)
        assertEquals(0, skipped)
        val sunset = styles.first { it.name == "Atardecer" }
        assertEquals(AppMode.GRADIENT, sunset.mode)
        assertEquals(listOf(RgbColor(255, 94, 58), RgbColor(255, 42, 104)), sunset.gradient)
        assertEquals(70, sunset.brightness)
        assertEquals(AppMode.AMBIENT, styles.first { it.name == "Cine con sonido" }.mode)
        assertEquals(true, styles.first { it.name == "Cine con sonido" }.ambientAudio)
    }

    @Test
    fun `document round trips`() {
        val (styles, _) = SavedStyles.importDocument(shared)
        val (again, skipped) = SavedStyles.importDocument(SavedStyles.document(styles))
        assertEquals(0, skipped)
        assertEquals(styles, again)
    }

    @Test
    fun `applying keeps fields the style does not carry`() {
        val base = LightingProfile(mode = AppMode.COLOR, brightness = 40, speed = 80)
        val style = SavedStyles.parse(org.json.JSONObject("""{"name":"Solo efecto","mode":"effect","effect":{"id":"rainbow","speed":20,"useGradient":false}}"""))!!
        val applied = style.applyTo(base)
        assertEquals(AppMode.EFFECT, applied.mode)
        assertEquals("rainbow", applied.effectId)
        assertEquals(20, applied.speed)
        assertEquals(40, applied.brightness)
    }

    @Test
    fun `per zone colors become static colors and the first one the solid color`() {
        val (styles, _) = SavedStyles.importDocument(shared)
        val applied = styles.first { it.name == "Sticks distintos" }.applyTo(LightingProfile())
        assertEquals(listOf(RgbColor(0, 196, 255), RgbColor(136, 86, 255)), applied.staticColors)
        assertEquals(RgbColor(0, 196, 255), applied.solidColor)
    }

    @Test
    fun `profile becomes a complete style`() {
        val style = SavedStyles.fromProfile(" Mío ", LightingProfile(mode = AppMode.AUDIO), ambientVividness = 30, ambientSmoothing = 50)!!
        assertEquals("Mío", style.name)
        assertEquals(AppMode.AUDIO, style.mode)
        assertEquals(30, style.ambientVividness)
    }

    @Test
    fun `invalid entries are skipped and foreign files rejected`() {
        val (styles, skipped) =
            SavedStyles.importDocument("""{"format":"colores-styles","version":1,"styles":[{"name":"Bien"},{"name":""},{"name":"x","mode":"disco"}]}""")
        assertEquals(listOf("Bien"), styles.map { it.name })
        assertEquals(2, skipped)
        assertTrue(runCatching { SavedStyles.importDocument("""{"format":"other","styles":[]}""") }.isFailure)
        assertTrue(runCatching { SavedStyles.importDocument("""{"format":"colores-styles","version":9,"styles":[]}""") }.isFailure)
        assertNull(SavedStyles.parse(org.json.JSONObject("""{"name":"ok","colors":[{"r":300,"g":0,"b":0}]}""")))
    }

    @Test
    fun `merge replaces by name ignoring case`() {
        val current = listOf(SavedStyle("Rojo"), SavedStyle("Azul"))
        val merge = SavedStyles.merge(current, listOf(SavedStyle("rojo", mode = AppMode.EFFECT), SavedStyle("Verde")))
        assertEquals(listOf("rojo", "Azul", "Verde"), merge.styles.map { it.name })
        assertEquals(1, merge.added)
        assertEquals(1, merge.replaced)
        assertEquals(listOf("Rojo"), SavedStyles.remove(current, "AZUL").map { it.name })
    }
}
