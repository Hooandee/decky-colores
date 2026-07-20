package com.hooandee.colores.ui

import com.hooandee.colores.device.DeviceCapabilities
import com.hooandee.colores.gradient.GradientPreset
import com.hooandee.colores.gradient.DeviceGradientPreferences
import com.hooandee.colores.gradient.LightingMode
import com.hooandee.colores.gradient.SavedGradient
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.led.LedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradientUiStateTest {
    private val red = RgbColor(255, 0, 0)
    private val green = RgbColor(0, 255, 0)
    private val blue = RgbColor(0, 0, 255)
    private val preset = GradientPreset("primary", listOf(red, green, blue))

    @Test
    fun `gradient requires color per-zone support and at least two zones`() {
        assertTrue(DeviceCapabilities(true, true, true, 2).supportsGradient(deviceSupportsPerZone = true))
        assertFalse(DeviceCapabilities(true, true, true, 1).supportsGradient(deviceSupportsPerZone = true))
        assertFalse(DeviceCapabilities(true, true, false, 2).supportsGradient(deviceSupportsPerZone = true))
        assertFalse(DeviceCapabilities(false, true, true, 2).supportsGradient(deviceSupportsPerZone = true))
        assertFalse(DeviceCapabilities(true, true, true, 2).supportsGradient(deviceSupportsPerZone = false))
    }

    @Test
    fun `preset expands to the real zone count and selects the first stop`() {
        val state = GradientUiState().selectPreset(preset, zones = 4)

        assertEquals(LightingMode.GRADIENT, state.mode)
        assertEquals(listOf(red, RgbColor(85, 170, 0), RgbColor(0, 170, 85), blue), state.stops)
        assertEquals(0, state.selectedStopIndex)
        assertEquals("primary", state.selectedPresetId)
    }

    @Test
    fun `reverse preserves the selected physical color`() {
        val state =
            GradientUiState(
                mode = LightingMode.GRADIENT,
                stops = listOf(red, green, blue),
                selectedStopIndex = 0,
                selectedPresetId = "primary",
            ).reversed()

        assertEquals(listOf(blue, green, red), state.stops)
        assertEquals(2, state.selectedStopIndex)
        assertEquals("primary", state.selectedPresetId)
    }

    @Test
    fun `restore reuses the selected built-in preset`() {
        val restored =
            GradientUiState(
                mode = LightingMode.GRADIENT,
                stops = listOf(blue, blue),
                selectedPresetId = "primary",
                presets = listOf(preset),
            ).restorePreset(zones = 2)

        assertEquals(listOf(red, blue), restored.stops)
    }

    @Test
    fun `editing one stop preserves all others`() {
        val state = GradientUiState(stops = listOf(red, blue), selectedStopIndex = 1, selectedPresetId = "primary")

        val changed = state.replaceSelectedStop(green)

        assertEquals(listOf(red, green), changed.stops)
        assertEquals("primary", changed.selectedPresetId)
    }

    @Test
    fun `gradient hydration keeps persisted stops when live hardware only exposes fallback colors`() {
        val saved = listOf(SavedGradient("Guardado", listOf(green, blue)))
        val preferences =
            DeviceGradientPreferences(
                mode = LightingMode.GRADIENT,
                currentStops = listOf(green, green),
                lastPresetId = "primary",
                savedGradients = saved,
            )

        val state =
            hydrateGradientUiState(
                liveColors = listOf(red, blue),
                preferences = preferences,
                presets = listOf(preset),
                zones = 2,
                supported = true,
            )

        assertEquals(listOf(green, green), state.stops)
        assertEquals(LightingMode.GRADIENT, state.mode)
        assertEquals("primary", state.selectedPresetId)
        assertEquals(saved, state.savedGradients)
    }

    @Test
    fun `color mode hydration uses live LED colors`() {
        val state =
            hydrateGradientUiState(
                liveColors = listOf(red, blue),
                preferences = DeviceGradientPreferences(mode = LightingMode.COLOR, currentStops = listOf(green, green)),
                presets = listOf(preset),
                zones = 2,
                supported = true,
            )

        assertEquals(listOf(red, blue), state.stops)
        assertEquals(LightingMode.COLOR, state.mode)
    }

    @Test
    fun `hydrated gradient replaces fallback colors in live LED state`() {
        val fallback = LedState(List(4) { red } + List(4) { blue }, brightness = 73, power = true)
        val stops = (1..8).map { RgbColor(it, it + 10, it + 20) }
        val gradient = GradientUiState(mode = LightingMode.GRADIENT, stops = stops)

        val synced = fallback.syncWithGradient(gradient)

        assertEquals(stops, synced.zoneColors)
        assertEquals(73, synced.brightness)
        assertTrue(synced.power)
        assertEquals(stops, synced.copy(brightness = 40).zoneColors)
    }

    @Test
    fun `unsupported devices always hydrate in color mode`() {
        val state =
            hydrateGradientUiState(
                liveColors = listOf(red),
                preferences = DeviceGradientPreferences(mode = LightingMode.GRADIENT, currentStops = listOf(red, blue)),
                presets = listOf(preset),
                zones = 1,
                supported = false,
            )

        assertEquals(LightingMode.COLOR, state.mode)
        assertEquals(listOf(red), state.stops)
    }
}
