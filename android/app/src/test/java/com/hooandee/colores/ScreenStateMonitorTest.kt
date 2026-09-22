package com.hooandee.colores

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStateMonitorTest {
    @Test
    fun `screen broadcasts drive interactivity and only screen on reasserts`() {
        assertFalse(interactiveAfter(Intent.ACTION_SCREEN_OFF, current = true))
        assertTrue(interactiveAfter(Intent.ACTION_SCREEN_ON, current = false))
        assertTrue(interactiveAfter(Intent.ACTION_USER_PRESENT, current = false))
        assertFalse(interactiveAfter(null, current = false))

        assertTrue(reassertsLighting(Intent.ACTION_SCREEN_ON))
        assertFalse(reassertsLighting(Intent.ACTION_USER_PRESENT))
    }
}
