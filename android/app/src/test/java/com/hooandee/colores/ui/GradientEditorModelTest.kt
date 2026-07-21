package com.hooandee.colores.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradientEditorModelTest {
    @Test
    fun `RP5 zones are grouped into two sticks with cardinal positions`() {
        val zones = gradientEditorZones("retroid-pocket-5", 8)

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
    fun `AYN Thor zones map to a calibrated two by two grid, mirrored between sticks`() {
        val zones = gradientEditorZones("ayn-thor", 8)

        assertEquals(List(4) { 0 } + List(4) { 1 }, zones.map { it.stick })
        // Left ring: rows are flipped relative to the right (physically mirrored).
        val left = zones.take(4)
        assertEquals(GradientZonePosition.TOP_LEFT, left[0].position)
        assertEquals(0 to 0, left[0].row to left[0].col)
        assertEquals(GradientZonePosition.BOTTOM_LEFT, left[1].position)
        assertEquals(1 to 0, left[1].row to left[1].col)
        // Right ring: idx0=bottom-left, idx1=top-left, idx2=top-right, idx3=bottom-right.
        val right = zones.drop(4)
        assertEquals(GradientZonePosition.BOTTOM_LEFT, right[0].position)
        assertEquals(1 to 0, right[0].row to right[0].col)
        assertEquals(GradientZonePosition.TOP_LEFT, right[1].position)
        assertEquals(0 to 0, right[1].row to right[1].col)
        // every cell in a stick is unique (a real 2x2 grid)
        assertEquals(4, left.map { it.row to it.col }.toSet().size)
        assertEquals(4, right.map { it.row to it.col }.toSet().size)
    }

    @Test
    fun `unknown layouts fall back to a numbered grid of up to four columns`() {
        val zones = gradientEditorZones("unknown", 6)

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
}
