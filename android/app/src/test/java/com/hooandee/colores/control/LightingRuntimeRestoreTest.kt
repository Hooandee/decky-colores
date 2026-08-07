package com.hooandee.colores.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightingRuntimeRestoreTest {
    @Test
    fun `successful restore attaches profiles to the same device binding`() {
        val restored = RestoredLightingBinding("ayn-thor", zones = 8, gradientSupported = true)
        var attached: RestoredLightingBinding? = null

        assertTrue(attachProfileRuntime(restored) { attached = it })
        assertEquals(restored, attached)
    }

    @Test
    fun `failed restore does not attach profiles`() {
        var attached = false

        assertFalse(attachProfileRuntime(null) { attached = true })
        assertFalse(attached)
    }
}
