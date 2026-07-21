package com.hooandee.colores.gradient

import com.hooandee.colores.led.RgbColor
import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradientInterpolatorTest {
    private val red = RgbColor(255, 0, 0)
    private val blue = RgbColor(0, 0, 255)

    @Test
    fun `matches every shared interpolate_gradient golden vector`() {
        val vectors = JSONArray(File("../../shared/golden/gradient.json").readText())
        assertTrue(vectors.length() > 0)
        repeat(vectors.length()) { index ->
            val vector = vectors.getJSONObject(index)
            assertEquals("interpolate_gradient", vector.getString("operation"))
            val input = vector.getJSONObject("input")
            val expected = vector.getJSONObject("expected").getJSONArray("colors").colors()

            val actual = GradientInterpolator.interpolate(input.getJSONArray("stops").colors(), input.getInt("zones"))

            assertEquals("vector ${vector.getString("id")}", expected, actual)
        }
    }

    @Test
    fun `two zones preserve both endpoints`() {
        assertEquals(listOf(red, blue), GradientInterpolator.interpolate(listOf(red, blue), 2))
    }

    @Test
    fun `one zone uses the first stop`() {
        assertEquals(listOf(red), GradientInterpolator.interpolate(listOf(red, blue), 1))
    }

    @Test
    fun `one stop repeats across every zone`() {
        assertEquals(List(4) { red }, GradientInterpolator.interpolate(listOf(red), 4))
    }

    @Test
    fun `zero and negative zones return empty output`() {
        assertEquals(emptyList<RgbColor>(), GradientInterpolator.interpolate(listOf(red, blue), 0))
        assertEquals(emptyList<RgbColor>(), GradientInterpolator.interpolate(listOf(red, blue), -2))
    }

    @Test
    fun `empty stops return empty output`() {
        assertEquals(emptyList<RgbColor>(), GradientInterpolator.interpolate(emptyList(), 3))
    }

    @Test
    fun `channels are clamped before interpolation`() {
        val unsafe = RgbColor(-20, 300, 10)

        assertEquals(List(2) { RgbColor(0, 255, 10) }, GradientInterpolator.interpolate(listOf(unsafe), 2))
    }

    @Test
    fun `halfway channels use ties-to-even rounding like the Decky backend`() {
        val start = RgbColor(0, 1, 0)
        val end = RgbColor(1, 2, 0)

        assertEquals(RgbColor(0, 2, 0), GradientInterpolator.interpolate(listOf(start, end), 3)[1])
    }
}

private fun JSONArray.colors(): List<RgbColor> =
    (0 until length()).map { index ->
        getJSONObject(index).let { color ->
            RgbColor(color.getInt("r"), color.getInt("g"), color.getInt("b"))
        }
    }
