package com.hooandee.colores.device

import com.hooandee.colores.led.SettingsProviderCodec
import com.hooandee.colores.led.SettingsProviderDescriptor

internal object GenericVendorLed {
    const val COLOR_KEY = "joystick_led_light_picker_color"
    const val BRIGHTNESS_KEY = "led_light_brightness_percent"
    val ENABLE_KEYS =
        listOf(
            "joystick_light_enabled",
            "left_joystick_light_enabled",
            "right_joystick_light_enabled",
            "left_handle_light_enabled",
            "right_handle_light_enabled",
        )
    fun descriptor(
        zones: Int,
        colorFormat: String = SettingsProviderCodec.ARGB_HEX_CSV,
    ): SettingsProviderDescriptor =
        SettingsProviderDescriptor(
            driver = "settings_provider",
            transport = "pserver",
            colorKey = COLOR_KEY,
            colorFormat = colorFormat,
            brightnessKey = BRIGHTNESS_KEY,
            brightnessRange = 0f..1f,
            enableKeys = ENABLE_KEYS,
            zones = zones.coerceAtLeast(1),
            requiresPermission = null,
            vendorService = "",
        )
}
