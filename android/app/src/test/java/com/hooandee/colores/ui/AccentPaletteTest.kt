package com.hooandee.colores.ui

import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentPaletteTest {
    private val sampleAccents =
        listOf(
            RgbColor(0, 0, 0),
            RgbColor(255, 255, 255),
            RgbColor(255, 240, 0),
            RgbColor(0, 40, 255),
            RgbColor(141, 131, 255),
            RgbColor(255, 154, 170),
            RgbColor(255, 54, 85),
            RgbColor(35, 205, 116),
            RgbColor(128, 128, 128),
        )

    @Test
    fun `accent text stays readable on the brightest glass surface`() {
        sampleAccents.forEach { accent ->
            assertTrue(contrastRatio(accentRoles(accent, dark = true).primary, DarkGlassSurface) >= 4.5)
            assertTrue(contrastRatio(accentRoles(accent, dark = false).primary, LightGlassSurface) >= 4.5)
        }
    }

    @Test
    fun `accent fill separates from glass and keeps a white thumb visible`() {
        val white = RgbColor(255, 255, 255)
        sampleAccents.forEach { accent ->
            listOf(false, true).forEach { dark ->
                val fill = accentRoles(accent, dark).fill
                val surface = if (dark) DarkGlassSurface else LightGlassSurface
                assertTrue("$accent dark=$dark", contrastRatio(fill, surface) >= 3.0)
                assertTrue("$accent dark=$dark", contrastRatio(fill, white) >= 3.0)
            }
        }
    }

    @Test
    fun `accent fill keeps the chosen hue`() {
        val fill = accentRoles(RgbColor(255, 54, 85), dark = true).fill
        val distance = kotlin.math.abs(fill.toHsvColor().hue - RgbColor(255, 54, 85).toHsvColor().hue)
        assertTrue(minOf(distance, 360f - distance) < 6f)
    }

    @Test
    fun `generated accent roles keep readable foreground contrast`() {
        val accents =
            listOf(
                RgbColor(0, 0, 0),
                RgbColor(255, 255, 255),
                RgbColor(255, 240, 0),
                RgbColor(0, 40, 255),
                RgbColor(141, 131, 255),
            )

        accents.forEach { accent ->
            listOf(false, true).forEach { dark ->
                val roles = accentRoles(accent, dark)
                assertTrue(contrastRatio(roles.primary, roles.onPrimary) >= 4.5)
                assertTrue(contrastRatio(roles.primaryContainer, roles.onPrimaryContainer) >= 4.5)
                val surface = if (dark) RgbColor(18, 19, 25) else RgbColor(255, 255, 255)
                assertTrue(contrastRatio(roles.primary, surface) >= 3.0)
            }
        }
    }

    @Test
    fun `atmosphere follows the selected accent with restrained varied shades`() {
        val orange = atmosphereRoles(RgbColor(255, 104, 28), dark = true)
        val green = atmosphereRoles(RgbColor(35, 205, 116), dark = true)

        assertNotEquals(orange.beam, green.beam)
        assertNotEquals(orange.coolGlow, orange.warmGlow)
        assertTrue(hueDistance(orange.beam, 20f) < 38f)
        assertTrue(hueDistance(green.beam, 145f) < 38f)
        assertTrue(channelRange(orange.backgroundStart) <= 18)
        assertTrue(channelRange(green.backgroundStart) <= 18)
    }

    @Test
    fun `light and dark atmospheres keep their intended luminance`() {
        val accent = RgbColor(141, 131, 255)
        val dark = atmosphereRoles(accent, dark = true)
        val light = atmosphereRoles(accent, dark = false)

        assertTrue(dark.backgroundStart.toHsvColor().value < 0.16f)
        assertTrue(dark.panelSurface.toHsvColor().value < 0.24f)
        assertTrue(light.backgroundStart.toHsvColor().value > 0.82f)
        assertTrue(light.panelSurface.toHsvColor().value > 0.88f)
    }

    @Test
    fun `neutral accents do not introduce an unrelated hue`() {
        val neutral = atmosphereRoles(RgbColor(128, 128, 128), dark = true)

        assertTrue(channelRange(neutral.coolGlow) <= 2)
        assertTrue(channelRange(neutral.warmGlow) <= 2)
        assertTrue(channelRange(neutral.backgroundStart) <= 4)
    }

    private fun hueDistance(color: RgbColor, expected: Float): Float {
        val distance = kotlin.math.abs(color.toHsvColor().hue - expected)
        return minOf(distance, 360f - distance)
    }

    private fun channelRange(color: RgbColor): Int =
        maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)
}
