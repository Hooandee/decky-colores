package com.hooandee.colores.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.settings.AppAppearance
import com.hooandee.colores.settings.AppLanguage
import com.hooandee.colores.settings.ThemeMode

@Composable
fun ColoresScreen(
    viewModel: ColoresViewModel,
    onGrantPermission: () -> Unit,
    onAudioCaptureRequest: () -> Unit,
    onGrantUsage: () -> Unit,
    appearance: AppAppearance,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentChange: (RgbColor) -> Unit,
    currentLanguageTag: String?,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    if (settingsOpen) {
        SettingsScreen(
            state = state,
            appearance = appearance,
            currentLanguageTag = currentLanguageTag,
            onBack = { settingsOpen = false },
            onThemeModeChange = onThemeModeChange,
            onAccentChange = onAccentChange,
            onLanguageChange = onLanguageChange,
            onChargerOnlyChange = viewModel::setChargerOnly,
            onProfileAutomationChange = viewModel::setProfileAutomation,
            onGrantUsage = onGrantUsage,
            onLedPreviewChange = viewModel::setLedPreviewEnabled,
            onSubmitReport = viewModel::submitReport,
            onResetReport = viewModel::resetReport,
        )
        return
    }
    DashboardScreen(
        state = state,
        onGrantPermission = onGrantPermission,
        onPowerChange = viewModel::setPower,
        onTargetChange = viewModel::selectTarget,
        onColorChange = viewModel::setEditingColor,
        onSaturationChange = viewModel::setSaturation,
        onBrightnessChange = viewModel::setBrightness,
        onOpenProfiles = viewModel::openProfilePicker,
        onOpenSettings = { settingsOpen = true },
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
                onBatteryBreatheChange = viewModel::setBatteryBreathe,
                onTemperatureBreatheChange = viewModel::setTemperatureBreathe,
                onSensorBandsChange = viewModel::setSensorBands,
                onAudioScaleChange = viewModel::setAudioScale,
                onAudioSensitivityChange = viewModel::setAudioSensitivity,
                onAudioCaptureRequest = onAudioCaptureRequest,
            ),
    )
    if (state.profilePickerOpen) {
        AppProfilesDialog(
            state = state,
            onDismiss = viewModel::closeProfilePicker,
            onGlobal = viewModel::selectGlobalProfile,
            onApp = viewModel::selectAppProfile,
            onFollowGlobal = viewModel::setSelectedAppFollowGlobal,
            onForget = viewModel::forgetSelectedAppProfile,
        )
    }
}
