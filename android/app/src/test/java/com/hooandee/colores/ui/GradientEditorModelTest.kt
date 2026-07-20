package com.hooandee.colores.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradientEditorModelTest {
    @Test
    fun `RP5 zones are grouped by stick in calibrated physical order`() {
        val zones = gradientEditorZones("retroid-pocket-5", 8)

        assertEquals((0..7).toList(), zones.map { it.index })
        assertEquals(List(4) { GradientStick.LEFT } + List(4) { GradientStick.RIGHT }, zones.map { it.stick })
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
    fun `unknown layouts retain numbered generic zones`() {
        val zones = gradientEditorZones("unknown", 3)

        assertEquals(listOf(0, 1, 2), zones.map { it.index })
        zones.forEach {
            assertNull(it.stick)
            assertNull(it.position)
        }
    }
}
