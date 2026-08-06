package com.hooandee.colores.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun ColoresScreen(
    viewModel: ColoresViewModel,
    onGrantPermission: () -> Unit,
    onAudioCaptureRequest: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    DashboardScreen(
        state = state,
        onGrantPermission = onGrantPermission,
        onPowerChange = viewModel::setPower,
        onTargetChange = viewModel::selectTarget,
        onColorChange = viewModel::setEditingColor,
        onSaturationChange = viewModel::setSaturation,
        onBrightnessChange = viewModel::setBrightness,
        onLedPreviewChange = viewModel::setLedPreviewEnabled,
        gradientActions =
            GradientActions(
                onStopChange = viewModel::selectGradientStop,
                onPresetChange = viewModel::selectGradientPreset,
                onSavedChange = viewModel::selectSavedGradient,
                onRestore = viewModel::restoreGradientPreset,
                onSave = viewModel::saveGradient,
                onDelete = viewModel::deleteGradient,
                onColorChange = viewModel::setEditingColor,
                onSaturationChange = viewModel::setSaturation,
                onSpeedChange = viewModel::setGradientSpeed,
            ),
        modeActions =
            ModeActions(
                onModeChange = viewModel::setAppMode,
                onSensorModeChange = viewModel::selectSensorMode,
                onEffectSelect = viewModel::selectEffect,
                onSpeedChange = viewModel::setSpeed,
                onEffectGradientChange = viewModel::setEffectUsesGradient,
                onChargerOnlyChange = viewModel::setChargerOnly,
                onBatteryBreatheChange = viewModel::setBatteryBreathe,
                onTemperatureBreatheChange = viewModel::setTemperatureBreathe,
                onSensorBandsChange = viewModel::setSensorBands,
                onAudioScaleChange = viewModel::setAudioScale,
                onAudioSensitivityChange = viewModel::setAudioSensitivity,
                onAudioCaptureRequest = onAudioCaptureRequest,
            ),
    )
}
