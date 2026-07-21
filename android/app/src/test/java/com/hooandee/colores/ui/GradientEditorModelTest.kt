package com.hooandee.colores.ui

import com.hooandee.colores.device.LedGridCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradientEditorModelTest {
    private val rp5Layout =
        listOf(
            LedGridCell(0, 0, 0, "top"),
            LedGridCell(0, 0, 1, "left"),
            LedGridCell(0, 1, 0, "bottom"),
            LedGridCell(0, 1, 1, "right"),
            LedGridCell(1, 0, 0, "top"),
            LedGridCell(1, 0, 1, "left"),
            LedGridCell(1, 1, 0, "bottom"),
            LedGridCell(1, 1, 1, "right"),
        )

    private val thorLayout =
        listOf(
            LedGridCell(0, 0, 0, "top_left"),
            LedGridCell(0, 1, 0, "bottom_left"),
            LedGridCell(0, 1, 1, "bottom_right"),
            LedGridCell(0, 0, 1, "top_right"),
            LedGridCell(1, 1, 0, "bottom_left"),
            LedGridCell(1, 0, 0, "top_left"),
            LedGridCell(1, 0, 1, "top_right"),
            LedGridCell(1, 1, 1, "bottom_right"),
        )

    @Test
    fun `data layout groups zones into two sticks with cardinal positions`() {
        val zones = gradientEditorZones(rp5Layout, 8)

        assertEquals((0..7).toList(), zones.map { it.index })
        assertEquals(List(4) { 0 } + List(4) { 1 }, zones.map { it.stick })
        assertEquals(
            listOf(
                GradientZonePosition.TOP,
                GradientZonePosition.LEFT,
                GradientZonePosition.BOTTOM,
                GradientZonePosition.RIGHT,
            ).let { it + it },
            zones.map { it.position },
        )
    }

    @Test
    fun `data layout maps a calibrated two by two grid, mirrored between sticks`() {
        val zones = gradientEditorZones(thorLayout, 8)

        assertEquals(List(4) { 0 } + List(4) { 1 }, zones.map { it.stick })
        val left = zones.take(4)
        assertEquals(GradientZonePosition.TOP_LEFT, left[0].position)
        assertEquals(0 to 0, left[0].row to left[0].col)
        assertEquals(GradientZonePosition.BOTTOM_LEFT, left[1].position)
        assertEquals(1 to 0, left[1].row to left[1].col)
        val right = zones.drop(4)
        assertEquals(GradientZonePosition.BOTTOM_LEFT, right[0].position)
        assertEquals(1 to 0, right[0].row to right[0].col)
        assertEquals(GradientZonePosition.TOP_LEFT, right[1].position)
        assertEquals(0 to 0, right[1].row to right[1].col)
        assertEquals(4, left.map { it.row to it.col }.toSet().size)
        assertEquals(4, right.map { it.row to it.col }.toSet().size)
    }

    @Test
    fun `null layout falls back to a numbered grid of up to four columns`() {
        val zones = gradientEditorZones(null, 6)

        assertEquals(listOf(0, 1, 2, 3, 4, 5), zones.map { it.index })
        zones.forEach {
            assertNull(it.stick)
            assertNull(it.position)
        }
        assertEquals(0 to 0, zones[0].row to zones[0].col)
        assertEquals(0 to 3, zones[3].row to zones[3].col)
        assertEquals(1 to 0, zones[4].row to zones[4].col)
        assertTrue(zones.all { it.col in 0..3 })
    }

    @Test
    fun `layout of the wrong length falls back to a numbered grid`() {
        val zones = gradientEditorZones(rp5Layout, 6)

        zones.forEach { assertNull(it.stick) }
        assertEquals(0 to 0, zones[0].row to zones[0].col)
    }
}
