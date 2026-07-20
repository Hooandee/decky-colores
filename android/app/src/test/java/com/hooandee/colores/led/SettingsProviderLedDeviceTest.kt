package com.hooandee.colores.led

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsProviderLedDeviceTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `writes RP5 colors brightness and power through descriptor keys`() =
        runTest {
            val store = FakeSettingsStore()
            val device = SettingsProviderLedDevice(descriptor, store, backgroundScope)

            assertTrue(
                device.applyZones(
                    colors = listOf(RgbColor(255, 0, 0), RgbColor(0, 0, 255)),
                    brightness = 65,
                    power = true,
                ),
            )
            runCurrent()

            assertEquals("#FFFF0000,#FF0000FF", store.values["color"])
            assertEquals("0.65", store.values["brightness"])
            assertEquals("1,1", store.values["enabled"])
            assertEquals("1", store.values["left"])
            assertEquals("1", store.values["right"])
        }

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
}

private class FakeSettingsStore : SystemSettingsStore {
    val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String): Boolean {
        values[key] = value
        return true
    }
}
