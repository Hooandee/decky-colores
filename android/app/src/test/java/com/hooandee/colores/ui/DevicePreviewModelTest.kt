package com.hooandee.colores.ui

import com.hooandee.colores.device.LedGridCell
import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePreviewModelTest {
    @Test
    fun `Thor preview groups four real zones per demonstrated stick`() {
        val colors = (1..8).map { RgbColor(it, it + 10, it + 20) }
        val layout =
            listOf(
                cell(0, "top_left"),
                cell(0, "bottom_left"),
                cell(0, "bottom_right"),
                cell(0, "top_right"),
                cell(1, "bottom_left"),
                cell(1, "top_left"),
                cell(1, "top_right"),
                cell(1, "bottom_right"),
            )

        val preview = devicePreviewGroups(colors, layout)

        assertTrue(preview.representsSticks)
        assertEquals(colors.take(4), preview.groups[0].map { it.color })
        assertEquals(listOf(182f, 92f, 2f, 272f), preview.groups[0].map { it.startAngle })
        assertEquals(colors.drop(4), preview.groups[1].map { it.color })
        assertEquals(listOf(92f, 182f, 272f, 2f), preview.groups[1].map { it.startAngle })
        assertEquals(List(4) { 86f }, preview.groups[0].map { it.sweepAngle })
    }

    @Test
    fun `two layout free outputs become two honest light groups`() {
        val colors = listOf(RgbColor(1, 2, 3), RgbColor(4, 5, 6))

        val preview = devicePreviewGroups(colors, null)

        assertFalse(preview.representsSticks)
        assertEquals(2, preview.groups.size)
        assertEquals(listOf(colors[0]), preview.groups[0].map { it.color })
        assertEquals(listOf(colors[1]), preview.groups[1].map { it.color })
        assertEquals(360f, preview.groups[0].single().sweepAngle)
        assertEquals(360f, preview.groups[1].single().sweepAngle)
    }

    @Test
    fun `one layout free output becomes one continuous light group`() {
        val color = RgbColor(1, 2, 3)

        val preview = devicePreviewGroups(listOf(color), null)

        assertFalse(preview.representsSticks)
        assertEquals(1, preview.groups.size)
        assertEquals(color, preview.groups.single().single().color)
        assertEquals(-90f, preview.groups.single().single().startAngle)
        assertEquals(360f, preview.groups.single().single().sweepAngle)
    }

    @Test
    fun `missing layout splits larger frames into two evenly spaced light groups`() {
        val colors = (1..8).map { RgbColor(it, 0, 0) }

        val preview = devicePreviewGroups(colors, null)

        assertFalse(preview.representsSticks)
        assertEquals(colors.take(4), preview.groups[0].map { it.color })
        assertEquals(colors.drop(4), preview.groups[1].map { it.color })
        assertEquals(listOf(-133f, -43f, 47f, 137f), preview.groups[0].map { it.startAngle })
    }

    @Test
    fun `mixed layout falls back without dropping colors`() {
        val colors = (1..5).map { RgbColor(it, 0, 0) }
        val layout =
            listOf(
                cell(0, "top"),
                cell(0, "bottom"),
                cell(1, "top"),
                cell(1, "bottom"),
                cell(2, "top"),
            )

        val preview = devicePreviewGroups(colors, layout)

        assertFalse(preview.representsSticks)
        assertEquals(colors, preview.groups.flatten().map { it.color })
    }

    private fun cell(
        stick: Int,
        position: String,
    ) = LedGridCell(stick = stick, row = 0, col = 0, position = position)
}
