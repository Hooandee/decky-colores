package com.hooandee.colores.led

import android.content.Context
import kotlinx.coroutines.CoroutineScope

internal object LedDeviceFactory {
    fun create(
        context: Context,
        descriptor: LedDescriptor,
        scope: CoroutineScope,
    ): LedDevice? =
        when (descriptor) {
            is SettingsProviderDescriptor ->
                when (descriptor.driver) {
                    "settings_provider" -> SettingsProviderLedDevice(context, descriptor, scope)
                    "htr3212" -> Htr3212LedDevice(context, descriptor, scope)
                    else -> null
                }
            is SysfsRgbDescriptor -> SysfsRgbDevice(descriptor, scope)
            is SingleAdcJoypadDescriptor -> SingleAdcJoypadLedDevice(descriptor, scope)
        }
}
