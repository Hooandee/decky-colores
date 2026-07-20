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
    val canWrite: Boolean = false,
    val ledState: LedState =
        LedState(
            zoneColors = listOf(RgbColor(93, 81, 255), RgbColor(93, 81, 255)),
            brightness = 100,
            power = true,
        ),
    val sameColor: Boolean = true,
)

class ColoresViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(ColoresUiState())
    val state: StateFlow<ColoresUiState> = mutableState.asStateFlow()

    private var ledDevice: LedDevice? = null
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val detected = withContext(Dispatchers.IO) { AndroidDeviceDetector(context).detect() }
            val canWrite = WriteSettingsPermission.canWrite(context)
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
            val liveState =
                if (canWrite && device != null) {
                    withContext(Dispatchers.IO) { runCatching { device.readState() }.getOrNull() }
                } else {
                    null
                }
            mutableState.value =
                mutableState.value.copy(
                    loading = false,
                    detected = detected,
                    canWrite = canWrite,
                    ledState = liveState ?: mutableState.value.ledState.fitZones(detected?.capabilities?.zones ?: 2),
                    sameColor = liveState?.zoneColors?.distinct()?.size?.let { it <= 1 } ?: mutableState.value.sameColor,
                )
        }
    }

    fun setPower(power: Boolean) = updateLedState { it.copy(power = power) }

    fun setBrightness(brightness: Int) = updateLedState { it.copy(brightness = brightness.coerceIn(0, 100)) }

    fun setSameColor(same: Boolean) {
        val current = mutableState.value
        val colors =
            if (same) {
                List(current.ledState.zoneColors.size) { current.ledState.zoneColors.first() }
            } else {
                current.ledState.zoneColors
            }
        mutableState.value = current.copy(sameColor = same, ledState = current.ledState.copy(zoneColors = colors))
        applyCurrentState()
    }

    fun setSolidColor(color: RgbColor) =
        updateLedState { state -> state.copy(zoneColors = List(state.zoneColors.size) { color }) }

    fun setZoneColor(
        index: Int,
        color: RgbColor,
    ) = updateLedState { state ->
        state.copy(zoneColors = state.zoneColors.mapIndexed { zone, existing -> if (zone == index) color else existing })
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
            if (current.sameColor) {
                device.applySolid(current.ledState.zoneColors.first(), current.ledState.brightness, current.ledState.power)
            } else {
                device.applyZones(current.ledState.zoneColors, current.ledState.brightness, current.ledState.power)
            }
        }
    }
}

private fun LedState.fitZones(zones: Int): LedState {
    val fallback = zoneColors.firstOrNull() ?: RgbColor(93, 81, 255)
    return copy(zoneColors = List(zones.coerceAtLeast(1)) { zoneColors.getOrNull(it) ?: fallback })
}
