package com.hooandee.colores.effects

import com.hooandee.colores.control.ServiceOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextServiceGateTest {
    @Test
    fun `static profile automation keeps service after effect stops`() {
        var starts = 0
        var stops = 0
        val lease = ServiceOwnerLease(onStart = { starts++; true }, onStop = { stops++ })

        lease.setRequired(ServiceOwner.APP_PROFILES, true)
        lease.setRequired(ServiceOwner.EFFECTS, true)
        lease.setRequired(ServiceOwner.EFFECTS, false)

        assertEquals(1, starts)
        assertEquals(0, stops)
    }

    @Test
    fun `last owner release stops service once`() {
        var stops = 0
        val lease = ServiceOwnerLease(onStart = { true }, onStop = { stops++ })
        lease.setRequired(ServiceOwner.APP_PROFILES, true)
        lease.onServiceStarted()

        lease.setRequired(ServiceOwner.APP_PROFILES, false)
        lease.setRequired(ServiceOwner.APP_PROFILES, false)

        assertEquals(1, stops)
    }

    @Test
    fun `failed start can be retried`() {
        var attempts = 0
        val lease = ServiceOwnerLease(
            onStart = {
                attempts++
                attempts > 1
            },
            onStop = {},
        )

        lease.setRequired(ServiceOwner.APP_PROFILES, true)
        lease.setRequired(ServiceOwner.APP_PROFILES, true)

        assertEquals(2, attempts)
    }

    @Test
    fun `release before the service reaches the foreground defers the stop to the service`() {
        var stops = 0
        val lease = ServiceOwnerLease(onStart = { true }, onStop = { stops++ })

        lease.setRequired(ServiceOwner.EFFECTS, true)
        lease.setRequired(ServiceOwner.EFFECTS, false)

        assertEquals(0, stops)
        assertTrue(lease.active)
        lease.onServiceStarted()
        assertTrue(lease.releaseIfUnowned())
        assertFalse(lease.active)
    }

    @Test
    fun `a restored runtime without consumers releases the service`() {
        val lease = ServiceOwnerLease(onStart = { true }, onStop = {})
        lease.onServiceStarted()

        assertTrue(lease.releaseIfUnowned())
    }

    @Test
    fun `a pending owner keeps the service and a later release stops it`() {
        var stops = 0
        val lease = ServiceOwnerLease(onStart = { true }, onStop = { stops++ })
        lease.setRequired(ServiceOwner.APP_PROFILES, true)
        lease.onServiceStarted()

        assertFalse(lease.releaseIfUnowned())
        lease.setRequired(ServiceOwner.APP_PROFILES, false)

        assertEquals(1, stops)
    }

    @Test
    fun `service death with remaining owners restarts it`() {
        var starts = 0
        val lease = ServiceOwnerLease(onStart = { starts++; true }, onStop = {})
        lease.setRequired(ServiceOwner.APP_PROFILES, true)
        lease.onServiceStarted()

        lease.onServiceStopped()

        assertEquals(2, starts)
        assertTrue(lease.active)
        assertTrue(lease.hasOwners())
    }

    @Test
    fun `refused restart forgets owners so the next request starts again`() {
        var allowed = true
        var starts = 0
        val lease = ServiceOwnerLease(onStart = { starts++; allowed }, onStop = {})
        lease.setRequired(ServiceOwner.EFFECTS, true)
        lease.onServiceStarted()

        allowed = false
        lease.onServiceStopped()
        assertFalse(lease.hasOwners())

        allowed = true
        lease.setRequired(ServiceOwner.EFFECTS, true)
        assertEquals(3, starts)
        assertTrue(lease.hasOwners())
    }

    @Test
    fun `capture ownership ends with the service`() {
        var starts = 0
        val lease = ServiceOwnerLease(onStart = { starts++; true }, onStop = {})
        lease.onServiceStarted()
        lease.setRequired(ServiceOwner.CAPTURE, true)

        lease.onServiceStopped()

        assertEquals(0, starts)
        assertFalse(lease.hasOwners())
        assertFalse(lease.active)
    }

    @Test
    fun `releasing capture while effects still need the service keeps it running`() {
        var stops = 0
        val lease = ServiceOwnerLease(onStart = { true }, onStop = { stops++ })
        lease.onServiceStarted()
        lease.setRequired(ServiceOwner.CAPTURE, true)
        lease.setRequired(ServiceOwner.EFFECTS, true)

        lease.setRequired(ServiceOwner.CAPTURE, false)
        assertEquals(0, stops)
        lease.setRequired(ServiceOwner.EFFECTS, false)
        assertEquals(1, stops)
    }
}
