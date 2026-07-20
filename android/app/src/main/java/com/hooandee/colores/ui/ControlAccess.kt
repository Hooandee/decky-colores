package com.hooandee.colores.ui

import com.hooandee.colores.led.SettingsProviderDescriptor

enum class ControlAccess {
    ENABLED,
    USER_PERMISSION_REQUIRED,
    SERVICE_UNAVAILABLE,
    ;

    companion object {
        fun resolve(
            descriptor: SettingsProviderDescriptor,
            deviceAvailable: Boolean,
            userPermissionGranted: Boolean,
        ): ControlAccess =
            when {
                !deviceAvailable -> SERVICE_UNAVAILABLE
                descriptor.transport == "pserver" -> ENABLED
                descriptor.requiresPermission == null || userPermissionGranted -> ENABLED
                else -> USER_PERMISSION_REQUIRED
            }
    }
}
