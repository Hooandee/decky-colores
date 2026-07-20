package com.hooandee.colores.ui

import com.hooandee.colores.led.SettingsProviderDescriptor
import org.junit.Assert.assertEquals
import org.junit.Test

class ControlAccessTest {
    @Test
    fun `PServer enables controls without a user permission`() {
        assertEquals(
            ControlAccess.ENABLED,
            ControlAccess.resolve(pServerDescriptor, deviceAvailable = true, userPermissionGranted = false),
        )
    }

    @Test
    fun `missing PServer reports unavailable vendor service`() {
        assertEquals(
            ControlAccess.SERVICE_UNAVAILABLE,
            ControlAccess.resolve(pServerDescriptor, deviceAvailable = false, userPermissionGranted = false),
        )
    }

    @Test
    fun `direct settings transport keeps its permission gate`() {
        assertEquals(
            ControlAccess.USER_PERMISSION_REQUIRED,
            ControlAccess.resolve(
                pServerDescriptor.copy(transport = "direct", requiresPermission = "android.permission.WRITE_SETTINGS"),
                deviceAvailable = true,
                userPermissionGranted = false,
            ),
        )
    }

    private val pServerDescriptor =
        SettingsProviderDescriptor(
            driver = "settings_provider",
            transport = "pserver",
            colorKey = "color",
            colorFormat = "argb_hex_csv",
            brightnessKey = "brightness",
            brightnessRange = 0f..1f,
            enableKeys = listOf("enabled", "left", "right"),
            zones = 2,
            requiresPermission = null,
            vendorService = "com.rp.gameassistant",
        )
}
