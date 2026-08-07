package com.hooandee.colores.effects

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsBootReceiverTest {
    @Test
    fun `lighting or an authorized profile restores one shared runtime`() {
        assertTrue(shouldRestoreRuntimeAtBoot(true, false, false))
        assertTrue(shouldRestoreRuntimeAtBoot(false, true, true))
        assertFalse(shouldRestoreRuntimeAtBoot(false, false, true))
        assertFalse(shouldRestoreRuntimeAtBoot(false, true, false))
    }
}
