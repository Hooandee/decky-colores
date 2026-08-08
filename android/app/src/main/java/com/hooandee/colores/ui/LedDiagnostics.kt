package com.hooandee.colores.ui

import com.hooandee.colores.led.LedDescriptor
import com.hooandee.colores.led.SettingsProviderDescriptor
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SysfsRgbDescriptor

internal fun LedDescriptor.diagnosticDriver(): String =
    when (this) {
        is SettingsProviderDescriptor -> driver
        is SysfsRgbDescriptor -> "sysfs_rgb"
        is SingleAdcJoypadDescriptor -> "singleadc_joypad"
    }

internal fun LedDescriptor.diagnosticRoute(): String =
    when (this) {
        is SettingsProviderDescriptor -> transport
        is SysfsRgbDescriptor -> "sysfs"
        is SingleAdcJoypadDescriptor -> "sysfs"
    }
