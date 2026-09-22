package com.hooandee.colores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityStateTest {
    @Test
    fun `pending projection requests survive recreation`() {
        assertEquals(ProjectionRequest.AMBIENT, restoredProjectionRequest(ProjectionRequest.AMBIENT.name))
        assertEquals(ProjectionRequest.AUDIO, restoredProjectionRequest("AUDIO"))
        assertEquals(ProjectionRequest.NONE, restoredProjectionRequest(null))
        assertEquals(ProjectionRequest.NONE, restoredProjectionRequest("garbage"))
    }

    @Test
    fun `settings launch falls back to the generic screen`() {
        val tried = mutableListOf<String>()

        val launched = launchFirstAvailable(listOf("package", "generic")) { tried += it; it == "generic" }

        assertTrue(launched)
        assertEquals(listOf("package", "generic"), tried)
        assertFalse(launchFirstAvailable(listOf("a")) { false })
    }
}
