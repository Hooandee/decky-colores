package com.hooandee.colores.ui

import com.hooandee.colores.led.LedDescriptor
import com.hooandee.colores.led.SettingsProviderDescriptor
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SysfsRgbDescriptor

enum class ControlAccess {
    ENABLED,
    USER_PERMISSION_REQUIRED,
    SERVICE_UNAVAILABLE,
    ;

    companion object {
        fun resolve(
            descriptor: LedDescriptor,
            deviceAvailable: Boolean,
            userPermissionGranted: Boolean,
        ): ControlAccess =
            when {
                !deviceAvailable -> SERVICE_UNAVAILABLE
                descriptor is SysfsRgbDescriptor -> ENABLED
                descriptor is SingleAdcJoypadDescriptor -> ENABLED
                descriptor is SettingsProviderDescriptor && descriptor.transport == "pserver" -> ENABLED
                descriptor is SettingsProviderDescriptor &&
                    (descriptor.requiresPermission == null || userPermissionGranted) -> ENABLED
                else -> USER_PERMISSION_REQUIRED
            }
    }
}
