package com.hooandee.colores.gradient

import com.hooandee.colores.led.RgbColor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class GradientPresetRepositoryTest {
    private val repository = GradientPresetRepository { File("../../shared/gradients.json").readText() }

    @Test
    fun `loads every shared preset with stable identifiers`() {
        val presets = repository.load()

        assertEquals(
            listOf("sunset", "ocean", "aurora", "neon", "lava", "mint", "vaporwave", "forest", "galaxy", "ember", "ice", "candy"),
            presets.map { it.id },
        )
        assertEquals(
            listOf(RgbColor(255, 94, 58), RgbColor(255, 149, 0), RgbColor(255, 45, 109)),
            presets.first().stops,
        )
    }

    @Test
    fun `invalid preset data degrades to an empty list`() {
        assertEquals(emptyList<GradientPreset>(), GradientPresetRepository { "not-json" }.load())
    }
}
