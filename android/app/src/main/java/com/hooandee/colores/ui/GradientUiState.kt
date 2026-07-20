package com.hooandee.colores.ui

import com.hooandee.colores.device.DeviceCapabilities
import com.hooandee.colores.gradient.GradientInterpolator
import com.hooandee.colores.gradient.GradientPreset
import com.hooandee.colores.gradient.DeviceGradientPreferences
import com.hooandee.colores.gradient.LightingMode
import com.hooandee.colores.gradient.SavedGradient
import com.hooandee.colores.led.RgbColor

data class GradientUiState(
    val mode: LightingMode = LightingMode.COLOR,
    val stops: List<RgbColor> = emptyList(),
    val selectedStopIndex: Int = 0,
    val selectedPresetId: String? = null,
    val presets: List<GradientPreset> = emptyList(),
    val savedGradients: List<SavedGradient> = emptyList(),
) {
    val selectedStop: RgbColor?
        get() = stops.getOrNull(selectedStopIndex) ?: stops.firstOrNull()

    fun selectStop(index: Int): GradientUiState =
        copy(selectedStopIndex = index.coerceIn(0, (stops.size - 1).coerceAtLeast(0)))

    fun replaceSelectedStop(color: RgbColor): GradientUiState {
        if (stops.isEmpty()) return copy(stops = listOf(color), selectedStopIndex = 0, selectedPresetId = null)
        val safeIndex = selectedStopIndex.coerceIn(0, stops.lastIndex)
        return copy(
            stops = stops.mapIndexed { index, current -> if (index == safeIndex) color else current },
        )
    }

    fun selectPreset(
        preset: GradientPreset,
        zones: Int,
    ): GradientUiState =
        copy(
            mode = LightingMode.GRADIENT,
            stops = GradientInterpolator.interpolate(preset.stops, zones),
            selectedStopIndex = 0,
            selectedPresetId = preset.id,
        )

    fun reversed(): GradientUiState =
        copy(
            stops = stops.reversed(),
            selectedStopIndex = (stops.lastIndex - selectedStopIndex).coerceAtLeast(0),
        )

    fun restorePreset(zones: Int): GradientUiState {
        val preset = presets.firstOrNull { it.id == selectedPresetId } ?: return this
        return selectPreset(preset, zones)
    }
}

fun DeviceCapabilities.supportsGradient(deviceSupportsPerZone: Boolean): Boolean =
    color && perZone && zones >= 2 && deviceSupportsPerZone

fun hydrateGradientUiState(
    liveColors: List<RgbColor>,
    preferences: DeviceGradientPreferences,
    presets: List<GradientPreset>,
    zones: Int,
    supported: Boolean,
): GradientUiState {
    val source = liveColors.ifEmpty { preferences.currentStops }
    return GradientUiState(
        mode = preferences.mode.takeIf { supported } ?: LightingMode.COLOR,
        stops = GradientInterpolator.interpolate(source, zones),
        selectedPresetId = preferences.lastPresetId,
        presets = presets,
        savedGradients = preferences.savedGradients,
    )
}
