package com.hooandee.colores.led

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsProviderLedDeviceTest {
    @Test
    fun `reading persisted state never writes it back`() =
        runTest {
            val store = FakeSettingsStore()
            store.values["color"] = "#FFFF0038,#FFFF9900"
            store.values["brightness"] = "1.0"
            store.values["enabled"] = "1,1"
            val device = SettingsProviderLedDevice(descriptor, store, backgroundScope)

            assertEquals(
                LedState(
                    zoneColors = listOf(RgbColor(255, 0, 56), RgbColor(255, 153, 0)),
                    brightness = 100,
                    power = true,
                ),
                device.readState(),
            )
            assertTrue(store.writes.isEmpty())
        }

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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `is unavailable when its settings transport is unavailable`() =
        runTest {
            val device = SettingsProviderLedDevice(descriptor, FakeSettingsStore(available = false), backgroundScope)

            assertFalse(device.available)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `retries state after the settings transport recovers`() =
        runTest {
            val store = FakeSettingsStore(failWrites = true)
            val device = SettingsProviderLedDevice(descriptor, store, backgroundScope)

            device.applySolid(RgbColor(1, 2, 3), brightness = 47, power = true)
            runCurrent()
            assertTrue(store.values.isEmpty())

            store.failWrites = false
            advanceTimeBy(500)
            runCurrent()

            assertEquals("#FF010203,#FF010203", store.values["color"])
            assertEquals("0.47", store.values["brightness"])
            assertEquals("1,1", store.values["enabled"])
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

private class FakeSettingsStore(
    override val available: Boolean = true,
    var failWrites: Boolean = false,
) : SystemSettingsStore {
    val values = mutableMapOf<String, String>()
    val writes = mutableListOf<Pair<String, String>>()

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String): Boolean {
        if (failWrites) return false
        writes += key to value
        values[key] = value
        return true
    }
}
