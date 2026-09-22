package com.hooandee.colores.effects

import com.hooandee.colores.control.AppMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsServiceProtocolTest {
    @Test
    fun `foreground service types follow platform availability`() {
        assertEquals(0, foregroundServiceTypes(sdk = 30, mediaProjection = false))
        assertEquals(0x20, foregroundServiceTypes(sdk = 30, mediaProjection = true))
        assertEquals(0x40000000, foregroundServiceTypes(sdk = 34, mediaProjection = false))
        assertEquals(0x40000020, foregroundServiceTypes(sdk = 34, mediaProjection = true))
    }

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
        assertEquals(EffectsServiceCommand.START_AMBIENT, resolveEffectsServiceCommand(true, ACTION_START_AMBIENT))
        assertEquals(EffectsServiceCommand.STOP_AMBIENT, resolveEffectsServiceCommand(true, ACTION_STOP_AMBIENT))
        assertEquals(EffectsServiceCommand.UPDATE_AMBIENT, resolveEffectsServiceCommand(true, ACTION_UPDATE_AMBIENT))
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

    @Test
    fun `ambient stop uses a regular start and reconciles only ambient mode`() {
        val policy = effectsServiceCommandPolicy(EffectsServiceCommand.STOP_AMBIENT)

        assertEquals(EffectsServiceStartMode.REGULAR, policy.startMode)
        assertTrue(policy.reconcileController)
        assertTrue(shouldReconcileAmbientController(requested = true, mode = AppMode.AMBIENT))
        assertFalse(shouldReconcileAmbientController(requested = true, mode = AppMode.COLOR))
    }

    @Test
    fun `capture survives a temporary app profile while the global profile uses it`() {
        assertFalse(shouldEndCapture(CaptureOwner.AUDIO, AppMode.COLOR, globalMode = AppMode.AUDIO))
        assertTrue(shouldEndCapture(CaptureOwner.AUDIO, AppMode.COLOR, globalMode = AppMode.COLOR))
        assertTrue(shouldEndCapture(CaptureOwner.AMBIENT, AppMode.EFFECT, globalMode = null))
        assertFalse(shouldEndCapture(CaptureOwner.AMBIENT, AppMode.AMBIENT, globalMode = AppMode.COLOR))
    }

    @Test
    fun `stop commands are only dispatched to a live service or capture`() {
        assertFalse(shouldDispatchCaptureStop(serviceActive = false, captureLive = false))
        assertTrue(shouldDispatchCaptureStop(serviceActive = true, captureLive = false))
        assertTrue(shouldDispatchCaptureStop(serviceActive = false, captureLive = true))
    }

    @Test
    fun `a stop skipped during restore settles with the latest start id`() {
        val settler = EffectsServiceSettler { true }

        settler.onStartCommand(1)
        settler.beginRestore()
        settler.onStartCommand(2)

        assertEquals(null, settler.settle())
        assertEquals(2, settler.endRestore())
    }

    @Test
    fun `settling keeps the service while owners remain`() {
        val settler = EffectsServiceSettler { false }
        settler.onStartCommand(3)

        assertEquals(null, settler.settle())
    }

    @Test
    fun `projection consent requires an ok result with data`() {
        assertTrue(hasProjectionConsent(resultCode = -1, resultDataPresent = true))
        assertFalse(hasProjectionConsent(resultCode = 0, resultDataPresent = true))
        assertFalse(hasProjectionConsent(resultCode = -1, resultDataPresent = false))
    }

    @Test
    fun `foreground refusal status separates consent errors from background limits`() {
        assertTrue(foregroundRefusalRequiresAuthorization(SecurityException("projection")))
        assertFalse(foregroundRefusalRequiresAuthorization(IllegalStateException("background")))
    }
}
