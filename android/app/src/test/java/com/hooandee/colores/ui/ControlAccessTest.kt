package com.hooandee.colores.ui

import com.hooandee.colores.led.SettingsProviderDescriptor
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import org.junit.Assert.assertEquals
import org.junit.Test

class ControlAccessTest {
    @Test
    fun `writable sysfs node enables controls without a permission`() {
        assertEquals(
            ControlAccess.ENABLED,
            ControlAccess.resolve(
                SysfsRgbDescriptor("/n", zones = 1, maxBrightness = 255, kind = SysfsColorKind.RGB_CHANNELS),
                deviceAvailable = true,
                userPermissionGranted = false,
            ),
        )
    }

    @Test
    fun `non writable sysfs node is unavailable`() {
        assertEquals(
            ControlAccess.SERVICE_UNAVAILABLE,
            ControlAccess.resolve(
                SysfsRgbDescriptor("/n", zones = 1, maxBrightness = 255, kind = SysfsColorKind.RGB_CHANNELS),
                deviceAvailable = false,
                userPermissionGranted = true,
            ),
        )
    }

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
