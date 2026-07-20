package com.hooandee.colores.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun ColoresScreen(
    viewModel: ColoresViewModel,
    onGrantPermission: () -> Unit,
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
    )
}
