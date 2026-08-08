package com.hooandee.colores.report

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportLogicTest {
    @Test
    fun `report requires a meaningful description`() {
        assertFalse(canSubmitReport("  \n"))
        assertTrue(canSubmitReport("El color se queda fijo"))
    }

    @Test
    fun `Android report categories cover native features`() {
        assertTrue(REPORT_CATEGORIES.containsAll(listOf("color", "brightness", "effects", "sensors", "audio", "profiles", "other")))
    }
}
