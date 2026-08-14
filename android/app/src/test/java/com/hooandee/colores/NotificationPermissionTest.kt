package com.hooandee.colores

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionTest {
    @Test
    fun `notification permission is requested only when the platform requires it`() {
        assertFalse(shouldRequestNotificationPermission(sdk = 32, granted = false))
        assertFalse(shouldRequestNotificationPermission(sdk = 33, granted = true))
        assertTrue(shouldRequestNotificationPermission(sdk = 33, granted = false))
    }
}
