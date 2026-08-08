package com.hooandee.colores.ui

import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentPaletteTest {
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
}
