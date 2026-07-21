package com.hooandee.colores.led

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsProviderCodecTest {
    private val descriptor =
        SettingsProviderDescriptor(
            driver = "settings_provider",
            colorKey = "color",
            colorFormat = "argb_hex_csv",
            brightnessKey = "brightness",
            brightnessRange = 0f..1f,
            enableKeys = listOf("enabled", "left", "right"),
            zones = 2,
            requiresPermission = "android.permission.WRITE_SETTINGS",
            vendorService = "com.rp.gameassistant",
        )

    @Test
    fun `encodes RP5 values exactly`() {
        val state =
            LedState(
                zoneColors = listOf(RgbColor(18, 52, 86), RgbColor(171, 205, 239)),
                brightness = 37,
                power = true,
            )

        assertEquals("#FF123456,#FFABCDEF", SettingsProviderCodec.encodeColors(state.zoneColors, 2))
        assertEquals("0.37", SettingsProviderCodec.encodeBrightness(state.brightness, descriptor))
        assertEquals(listOf("1,1", "1", "1"), SettingsProviderCodec.encodePower(true, 2))
        assertEquals(listOf("0,0", "0", "0"), SettingsProviderCodec.encodePower(false, 2))
    }

    @Test
    fun `encodePower emits one value per key, master first then scalars`() {
        assertEquals(listOf("1,1"), SettingsProviderCodec.encodePower(true, 2, keyCount = 1))
        assertEquals(
            listOf("1,1", "1", "1", "1", "1"),
            SettingsProviderCodec.encodePower(true, 2, keyCount = 5),
        )
    }

    @Test
    fun `decodes current RP5 settings`() {
        val state =
            SettingsProviderCodec.decode(
                colors = "#FFFF0000,#FF00FF00",
                brightness = "0.42",
                power = "1,1",
                descriptor = descriptor,
            )

        assertEquals(listOf(RgbColor(255, 0, 0), RgbColor(0, 255, 0)), state.zoneColors)
        assertEquals(42, state.brightness)
        assertEquals(true, state.power)
    }
}
