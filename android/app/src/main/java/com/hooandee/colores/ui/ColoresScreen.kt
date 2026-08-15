package com.hooandee.colores.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.hooandee.colores.R
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.settings.AppAppearance
import com.hooandee.colores.settings.AppLanguage
import com.hooandee.colores.settings.ThemeMode

@Composable
fun ColoresScreen(
    viewModel: ColoresViewModel,
    onGrantPermission: () -> Unit,
    onAudioCaptureRequest: () -> Unit,
    onAmbientCaptureRequest: () -> Unit,
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
            onOpenHardwareLearning = {
                settingsOpen = false
                viewModel.openHardwareLearning()
            },
            onForgetLearnedHardware = viewModel::forgetLearnedHardware,
        )
    } else {
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
            onOpenHardwareLearning = viewModel::openHardwareLearning,
            onOpenHardwareLearningReport = viewModel::openHardwareLearningReport,
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
                    onEditingChange = viewModel::setGradientEditing,
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
                    onAmbientCaptureRequest = onAmbientCaptureRequest,
                    onAmbientCaptureFpsChange = viewModel::setAmbientCaptureFps,
                    onAmbientSamplingModeChange = viewModel::setAmbientSamplingMode,
                    onAmbientVividnessChange = viewModel::setAmbientVividness,
                    onAmbientSmoothingChange = viewModel::setAmbientSmoothing,
                ),
        )
    }
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
    if (state.hardwareLearning.dialogOpen) {
        HardwareLearningDialog(
            ui = state.hardwareLearning,
            onDismiss = viewModel::dismissHardwareLearning,
            onConsent = viewModel::confirmHardwareLearningConsent,
            onRunProbe = viewModel::runHardwareLearningProbe,
            onAnswer = viewModel::answerHardwareLearning,
            onFinish = viewModel::finishHardwareLearningCandidate,
            onNextCandidate = viewModel::tryNextHardwareCandidate,
            onReport = viewModel::openHardwareLearningReport,
        )
    }
    if (state.hardwareLearning.reportOpen) {
        AndroidReportDialog(
            state = state,
            onDismiss = viewModel::closeHardwareLearningReport,
            onSubmit = viewModel::submitReport,
            initialCategories = setOf("learning"),
            initialText = stringResource(R.string.hardware_learning_report_description),
            lockedCategories = true,
        )
    }
}
