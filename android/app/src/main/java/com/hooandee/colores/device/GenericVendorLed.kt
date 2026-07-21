package com.hooandee.colores.device

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
    const val DEFAULT_ZONES = 2

    fun descriptor(zones: Int = DEFAULT_ZONES): SettingsProviderDescriptor =
        SettingsProviderDescriptor(
            driver = "settings_provider",
            transport = "pserver",
            colorKey = COLOR_KEY,
            colorFormat = "argb_hex_csv",
            brightnessKey = BRIGHTNESS_KEY,
            brightnessRange = 0f..1f,
            enableKeys = ENABLE_KEYS,
            zones = zones,
            requiresPermission = null,
            vendorService = "",
        )
}
