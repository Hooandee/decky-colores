package com.hooandee.colores.effects

import com.hooandee.colores.control.AppMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsServiceProtocolTest {
    @Test
    fun `an actionless keep-alive intent is not treated as a sticky restart`() {
        assertEquals(
            EffectsServiceCommand.KEEP_ALIVE,
            resolveEffectsServiceCommand(intentPresent = true, action = null),
        )
        assertEquals(
            EffectsServiceCommand.RESTORE,
            resolveEffectsServiceCommand(intentPresent = false, action = null),
        )
    }

    @Test
    fun `explicit service actions keep their command`() {
        assertEquals(EffectsServiceCommand.START_AUDIO, resolveEffectsServiceCommand(true, ACTION_START_AUDIO))
        assertEquals(EffectsServiceCommand.STOP_AUDIO, resolveEffectsServiceCommand(true, ACTION_STOP_AUDIO))
        assertEquals(EffectsServiceCommand.RESTORE, resolveEffectsServiceCommand(true, ACTION_RESTORE))
    }

    @Test
    fun `audio stop uses a regular start and reconciles the controller`() {
        val policy = effectsServiceCommandPolicy(EffectsServiceCommand.STOP_AUDIO)

        assertEquals(EffectsServiceStartMode.REGULAR, policy.startMode)
        assertEquals(true, policy.reconcileController)
    }

    @Test
    fun `capture changes only reconcile the controller while audio owns the mode`() {
        assertTrue(shouldReconcileAudioController(requested = true, mode = AppMode.AUDIO))
        assertFalse(shouldReconcileAudioController(requested = true, mode = AppMode.COLOR))
        assertFalse(shouldReconcileAudioController(requested = false, mode = AppMode.AUDIO))
    }
}
