package com.hooandee.colores.effects

import com.hooandee.colores.control.ServiceOwner
import org.junit.Assert.assertEquals
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
}
