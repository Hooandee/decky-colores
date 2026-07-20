package com.hooandee.colores.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hooandee.colores.device.AndroidDeviceDetector
import com.hooandee.colores.device.DetectedAndroidDevice
import com.hooandee.colores.led.LedDevice
import com.hooandee.colores.led.LedState
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.led.SettingsProviderLedDevice
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
) {
    val canWrite: Boolean
        get() = controlAccess == ControlAccess.ENABLED

    val editingColor: RgbColor
        get() = ledState.colorForEditing(editTarget)

    val mixedTarget: Boolean
        get() = editTarget == EditTarget.BOTH && ledState.hasMixedColors
}

class ColoresViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(ColoresUiState())
    val state: StateFlow<ColoresUiState> = mutableState.asStateFlow()

    private var ledDevice: LedDevice? = null
    private var refreshJob: Job? = null
    private val ledPreviewPreferences = LedPreviewPreferences(application)

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
                    detected
                        ?.takeIf { it.led.driver == "settings_provider" }
                        ?.let {
                            SettingsProviderLedDevice(
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
            mutableState.value =
                mutableState.value.copy(
                    loading = false,
                    detected = detected,
                    controlAccess = controlAccess,
                    ledState = liveState ?: mutableState.value.ledState.fitZones(detected?.capabilities?.zones ?: 2),
                    ledPreviewEnabled =
                        detected
                            ?.takeIf { it.previewCalibration != null }
                            ?.let { ledPreviewPreferences.isEnabled(it.id) }
                            ?: false,
                )
        }
    }

    fun setPower(power: Boolean) = updateLedState { it.copy(power = power) }

    fun setBrightness(brightness: Int) = updateLedState { it.copy(brightness = brightness.coerceIn(0, 100)) }

    fun selectTarget(target: EditTarget) {
        mutableState.value = mutableState.value.copy(editTarget = target)
    }

    fun setEditingColor(color: RgbColor) =
        updateLedState { state -> state.withTargetColor(mutableState.value.editTarget, color) }

    fun setSaturation(saturation: Float) =
        updateLedState { state -> state.withTargetSaturation(mutableState.value.editTarget, saturation) }

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
