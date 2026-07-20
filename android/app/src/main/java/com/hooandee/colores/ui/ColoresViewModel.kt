package com.hooandee.colores.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hooandee.colores.device.AndroidDeviceDetector
import com.hooandee.colores.device.DetectedAndroidDevice
import com.hooandee.colores.gradient.DeviceGradientPreferences
import com.hooandee.colores.gradient.GradientApplier
import com.hooandee.colores.gradient.GradientInterpolator
import com.hooandee.colores.gradient.GradientPreferences
import com.hooandee.colores.gradient.GradientPreset
import com.hooandee.colores.gradient.GradientPresetRepository
import com.hooandee.colores.gradient.GradientResumePolicy
import com.hooandee.colores.gradient.LightingMode
import com.hooandee.colores.led.LedDevice
import com.hooandee.colores.led.LedDeviceFactory
import com.hooandee.colores.led.LedState
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.permission.WriteSettingsPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ColoresUiState(
    val loading: Boolean = true,
    val detected: DetectedAndroidDevice? = null,
    val controlAccess: ControlAccess = ControlAccess.SERVICE_UNAVAILABLE,
    val ledState: LedState =
        LedState(
            zoneColors = listOf(RgbColor(93, 81, 255), RgbColor(93, 81, 255)),
            brightness = 100,
            power = true,
        ),
    val editTarget: EditTarget = EditTarget.BOTH,
    val ledPreviewEnabled: Boolean = false,
    val gradientAvailable: Boolean = false,
    val gradient: GradientUiState = GradientUiState(),
) {
    val canWrite: Boolean
        get() = controlAccess == ControlAccess.ENABLED

    val editingColor: RgbColor
        get() =
            if (gradient.mode == LightingMode.GRADIENT) {
                gradient.selectedStop ?: ledState.colorForEditing(editTarget)
            } else {
                ledState.colorForEditing(editTarget)
            }

    val mixedTarget: Boolean
        get() = gradient.mode == LightingMode.COLOR && editTarget == EditTarget.BOTH && ledState.hasMixedColors

    val ledColorProjection: LedColorProjection
        get() = LedColorProjection(detected?.previewCalibration, ledPreviewEnabled)
}

class ColoresViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(ColoresUiState())
    val state: StateFlow<ColoresUiState> = mutableState.asStateFlow()

    private var ledDevice: LedDevice? = null
    private var refreshJob: Job? = null
    private val ledPreviewPreferences = LedPreviewPreferences(application)
    private val gradientPreferences = GradientPreferences(application)
    private val gradientPresets = GradientPresetRepository(application).load()

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val detected = withContext(Dispatchers.IO) { AndroidDeviceDetector(context).detect() }
            val userPermissionGranted = WriteSettingsPermission.canWrite(context)
            val device =
                if (detected != null && detected.id == mutableState.value.detected?.id) {
                    ledDevice
                } else {
                    detected?.let {
                        LedDeviceFactory.create(
                            context,
                            it.led,
                            scope = CoroutineScope(viewModelScope.coroutineContext + Dispatchers.IO),
                        )
                    }
                }
            ledDevice = device
            val controlAccess =
                detected
                    ?.let {
                        ControlAccess.resolve(
                            descriptor = it.led,
                            deviceAvailable = device?.available == true,
                            userPermissionGranted = userPermissionGranted,
                        )
                    }
                    ?: ControlAccess.SERVICE_UNAVAILABLE
            val liveState =
                if (controlAccess == ControlAccess.ENABLED && device != null) {
                    withContext(Dispatchers.IO) { runCatching { device.readState() }.getOrNull() }
                } else {
                    null
                }
            val zones = detected?.capabilities?.zones ?: 2
            val shownState = liveState ?: mutableState.value.ledState.fitZones(zones)
            val gradientSupported =
                detected?.capabilities?.supportsGradient(device?.supportsPerZone == true) == true
            val storedGradient =
                detected?.let { withContext(Dispatchers.IO) { gradientPreferences.load(it.id) } }
                    ?: DeviceGradientPreferences()
            val hydratedGradient =
                hydrateGradientUiState(
                    liveColors = liveState?.zoneColors.orEmpty(),
                    preferences = storedGradient,
                    presets = gradientPresets,
                    zones = zones,
                    supported = gradientSupported,
                ).let { gradient ->
                    if (gradient.stops.isEmpty()) gradient.copy(stops = shownState.zoneColors) else gradient
                }
            val refreshedState =
                mutableState.value.copy(
                    loading = false,
                    detected = detected,
                    controlAccess = controlAccess,
                    ledState = shownState.syncWithGradient(hydratedGradient),
                    ledPreviewEnabled =
                        detected
                            ?.takeIf { it.previewCalibration != null }
                            ?.let { ledPreviewPreferences.isEnabled(it.id) }
                            ?: false,
                    gradientAvailable = gradientSupported,
                    gradient = hydratedGradient,
                )
            mutableState.value = refreshedState
            if (
                GradientResumePolicy.shouldReapply(
                    mode = refreshedState.gradient.mode,
                    stops = refreshedState.gradient.stops,
                    gradientAvailable = refreshedState.gradientAvailable,
                    canWrite = refreshedState.canWrite,
                )
            ) {
                reapplyGradient()
            }
        }
    }

    fun reapplyGradient() {
        val current = mutableState.value
        if (
            !GradientResumePolicy.shouldReapply(
                mode = current.gradient.mode,
                stops = current.gradient.stops,
                gradientAvailable = current.gradientAvailable,
                canWrite = current.canWrite,
            )
        ) {
            return
        }
        ledDevice?.invalidate()
        applyGradient()
    }

    fun setPower(power: Boolean) = updateLedState { it.copy(power = power) }

    fun setBrightness(brightness: Int) = updateLedState { it.copy(brightness = brightness.coerceIn(0, 100)) }

    fun selectTarget(target: EditTarget) {
        mutableState.value = mutableState.value.copy(editTarget = target)
    }

    fun setEditingColor(color: RgbColor) {
        val current = mutableState.value
        if (current.gradient.mode == LightingMode.GRADIENT) {
            updateGradient(current.gradient.replaceSelectedStop(color), apply = true)
        } else {
            updateLedState { state -> state.withTargetColor(current.editTarget, color) }
        }
    }

    fun setSaturation(saturation: Float) {
        val current = mutableState.value
        if (current.gradient.mode == LightingMode.GRADIENT) {
            val changed = current.editingColor.toHsvColor().copy(saturation = saturation.coerceIn(0f, 1f)).toRgbColor()
            updateGradient(current.gradient.replaceSelectedStop(changed), apply = true)
        } else {
            updateLedState { state -> state.withTargetSaturation(current.editTarget, saturation) }
        }
    }

    fun setLightingMode(mode: LightingMode) {
        val current = mutableState.value
        if (!current.canWrite || current.detected == null || mode == current.gradient.mode) return
        if (mode == LightingMode.GRADIENT && !current.gradientAvailable) return
        if (mode == LightingMode.GRADIENT) {
            updateGradient(current.gradient.copy(mode = mode), apply = true)
            return
        }
        val color = current.gradient.selectedStop ?: current.editingColor
        val solid = current.ledState.withTargetColor(EditTarget.BOTH, color)
        mutableState.value = current.copy(ledState = solid, editTarget = EditTarget.BOTH, gradient = current.gradient.copy(mode = mode))
        persistGradient()
        applyCurrentState()
    }

    fun selectGradientStop(index: Int) {
        val current = mutableState.value
        if (!current.gradientAvailable) return
        mutableState.value = current.copy(gradient = current.gradient.selectStop(index))
    }

    fun selectGradientPreset(preset: GradientPreset) {
        val current = mutableState.value
        val zones = current.detected?.capabilities?.zones ?: return
        if (!current.gradientAvailable) return
        updateGradient(current.gradient.selectPreset(preset, zones), apply = true)
    }

    fun selectSavedGradient(name: String) {
        val current = mutableState.value
        val saved = current.gradient.savedGradients.firstOrNull { it.name == name } ?: return
        val zones = current.detected?.capabilities?.zones ?: return
        updateGradient(
            current.gradient.copy(
                mode = LightingMode.GRADIENT,
                stops = GradientInterpolator.interpolate(saved.stops, zones),
                selectedStopIndex = 0,
                selectedPresetId = null,
            ),
            apply = true,
        )
    }

    fun reverseGradient() {
        val current = mutableState.value
        if (!current.gradientAvailable) return
        updateGradient(current.gradient.reversed(), apply = true)
    }

    fun restoreGradientPreset() {
        val current = mutableState.value
        val zones = current.detected?.capabilities?.zones ?: return
        val restored = current.gradient.restorePreset(zones)
        if (restored == current.gradient) return
        updateGradient(restored, apply = true)
    }

    fun saveGradient(name: String) {
        val current = mutableState.value
        val deviceId = current.detected?.id ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || current.gradient.stops.isEmpty()) return
        persistGradient()
        val saved = gradientPreferences.upsert(deviceId, trimmed, current.gradient.stops)
        mutableState.value = mutableState.value.copy(gradient = mutableState.value.gradient.copy(savedGradients = saved.savedGradients))
    }

    fun deleteGradient(name: String) {
        val current = mutableState.value
        val deviceId = current.detected?.id ?: return
        val saved = gradientPreferences.delete(deviceId, name)
        mutableState.value = current.copy(gradient = current.gradient.copy(savedGradients = saved.savedGradients))
    }

    fun setLedPreviewEnabled(enabled: Boolean) {
        val current = mutableState.value
        val device = current.detected?.takeIf { it.previewCalibration != null } ?: return
        mutableState.value = current.copy(ledPreviewEnabled = enabled)
        ledPreviewPreferences.setEnabled(device.id, enabled)
    }

    private fun updateLedState(transform: (LedState) -> LedState) {
        val current = mutableState.value
        if (!current.canWrite || current.detected == null) return
        mutableState.value = current.copy(ledState = transform(current.ledState))
        applyCurrentState()
    }

    private fun updateGradient(
        gradient: GradientUiState,
        apply: Boolean,
    ) {
        val current = mutableState.value
        val zones = current.detected?.capabilities?.zones ?: return
        if (!current.canWrite || !current.gradientAvailable) return
        val colors = GradientInterpolator.interpolate(gradient.stops, zones)
        mutableState.value = current.copy(gradient = gradient, ledState = current.ledState.copy(zoneColors = colors))
        persistGradient()
        if (apply) applyGradient()
    }

    private fun persistGradient() {
        val current = mutableState.value
        val deviceId = current.detected?.id ?: return
        gradientPreferences.save(
            deviceId,
            DeviceGradientPreferences(
                mode = current.gradient.mode,
                currentStops = current.gradient.stops,
                lastPresetId = current.gradient.selectedPresetId,
                savedGradients = current.gradient.savedGradients,
            ),
        )
    }

    private fun applyGradient() {
        val current = mutableState.value
        val device = ledDevice ?: return
        val zones = current.detected?.capabilities?.zones ?: return
        viewModelScope.launch {
            GradientApplier(device).apply(current.gradient.stops, zones, current.ledState)
        }
    }

    private fun applyCurrentState() {
        val current = mutableState.value
        val device = ledDevice ?: return
        viewModelScope.launch {
            device.applyZones(current.ledState.zoneColors, current.ledState.brightness, current.ledState.power)
        }
    }
}

private fun LedState.fitZones(zones: Int): LedState {
    val fallback = zoneColors.firstOrNull() ?: RgbColor(93, 81, 255)
    return copy(zoneColors = List(zones.coerceAtLeast(1)) { zoneColors.getOrNull(it) ?: fallback })
}
