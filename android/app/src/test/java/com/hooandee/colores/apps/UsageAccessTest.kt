package com.hooandee.colores.apps

import android.app.AppOpsManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAccessTest {
    @Test
    fun `default app op falls back to the declared permission grant`() {
        assertTrue(usageAccessGranted(AppOpsManager.MODE_ALLOWED, permissionGranted = false))
        assertTrue(usageAccessGranted(AppOpsManager.MODE_DEFAULT, permissionGranted = true))
        assertFalse(usageAccessGranted(AppOpsManager.MODE_DEFAULT, permissionGranted = false))
        assertFalse(usageAccessGranted(AppOpsManager.MODE_IGNORED, permissionGranted = true))
        assertFalse(usageAccessGranted(AppOpsManager.MODE_ERRORED, permissionGranted = true))
    }
}
