package com.hooandee.colores.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveLayoutTest {
    @Test
    fun `handheld landscape aspect ratios use two panes`() {
        listOf(
            Triple(600.dp, 450.dp, "4:3"),
            Triple(640.dp, 360.dp, "16:9"),
            Triple(640.dp, 400.dp, "16:10"),
            Triple(800.dp, 360.dp, "20:9"),
        ).forEach { (width, height, label) ->
            assertTrue(label, shouldUseTwoPaneLayout(width, height, 720.dp))
        }
    }

    @Test
    fun `compact portrait keeps one pane`() {
        assertFalse(shouldUseTwoPaneLayout(596.dp, 720.dp, 720.dp))
    }

    @Test
    fun `expanded width uses two panes in any orientation`() {
        assertTrue(shouldUseTwoPaneLayout(720.dp, 960.dp, 720.dp))
    }

    @Test
    fun `only short landscape screens use compact dashboard density`() {
        assertTrue(shouldUseCompactDashboardDensity(600.dp, 450.dp))
        assertTrue(shouldUseCompactDashboardDensity(640.dp, 360.dp))
        assertTrue(shouldUseCompactDashboardDensity(640.dp, 400.dp))
        assertTrue(shouldUseCompactDashboardDensity(800.dp, 360.dp))
        assertFalse(shouldUseCompactDashboardDensity(1280.dp, 800.dp))
        assertFalse(shouldUseCompactDashboardDensity(600.dp, 800.dp))
    }

    @Test
    fun `large landscape remains available to landscape dialogs without compact density`() {
        assertTrue(isUsableLandscape(1280.dp, 800.dp))
        assertFalse(shouldUseCompactDashboardDensity(1280.dp, 800.dp))
    }

    @Test
    fun `preview rings share available width equally`() {
        assertEquals(
            86.5.dp,
            previewRingDiameter(
                availableWidth = 191.dp,
                groupCount = 2,
                spacing = 18.dp,
                preferredDiameter = 112.dp,
            ),
        )
    }
}
