package com.hooandee.colores.ui

import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Test

class GradientPreviewStopsTest {
    @Test
    fun `preview always gets at least two gradient stops`() {
        val empty = RgbColor(34, 35, 43)
        val red = RgbColor(255, 0, 0)
        val blue = RgbColor(0, 0, 255)

        assertEquals(listOf(empty, empty), emptyList<RgbColor>().previewStops(empty))
        assertEquals(listOf(red, red), listOf(red).previewStops(empty))
        assertEquals(listOf(red, blue), listOf(red, blue).previewStops(empty))
    }
}
