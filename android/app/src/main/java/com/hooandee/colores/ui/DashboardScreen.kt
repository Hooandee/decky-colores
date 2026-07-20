package com.hooandee.colores.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hooandee.colores.R
import com.hooandee.colores.led.RgbColor

@Composable
fun DashboardScreen(
    state: ColoresUiState,
    onGrantPermission: () -> Unit,
    onPowerChange: (Boolean) -> Unit,
    onTargetChange: (EditTarget) -> Unit,
    onColorChange: (RgbColor) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onLedPreviewChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Surface
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            DashboardHeader(
                state = state,
                onPowerChange = onPowerChange,
            )
            Spacer(Modifier.height(14.dp))
            val detected = state.detected
            if (detected == null) {
                ControlStatusCard(
                    title = stringResource(R.string.no_leds_title),
                    description = stringResource(R.string.no_leds_description),
                )
                return@Column
            }
            when (state.controlAccess) {
                ControlAccess.USER_PERMISSION_REQUIRED ->
                    ControlStatusCard(
                        title = stringResource(R.string.permission_title),
                        description = stringResource(R.string.permission_description),
                        action = stringResource(R.string.permission_button),
                        onAction = onGrantPermission,
                    )
                ControlAccess.SERVICE_UNAVAILABLE ->
                    ControlStatusCard(
                        title = stringResource(R.string.control_service_title),
                        description = stringResource(R.string.control_service_description),
                    )
                ControlAccess.ENABLED ->
                    DashboardBody(
                        state = state,
                        perZone = detected.capabilities.perZone && detected.capabilities.zones > 1,
                        colorEnabled = detected.capabilities.color,
                        brightnessEnabled = detected.capabilities.brightness,
                        onTargetChange = onTargetChange,
                        onColorChange = onColorChange,
                        onSaturationChange = onSaturationChange,
                        onBrightnessChange = onBrightnessChange,
                        onLedPreviewChange = onLedPreviewChange,
                        modifier = Modifier.weight(1f),
                    )
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    state: ColoresUiState,
    onPowerChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.4.sp,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.detected?.friendlyName ?: stringResource(R.string.device_unknown),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.canWrite) ConnectedPill()
            }
        }
        if (state.detected != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.power_title),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                Switch(
                    checked = state.ledState.power,
                    onCheckedChange = onPowerChange,
                    enabled = state.canWrite,
                )
            }
        }
    }
}

@Composable
private fun ConnectedPill() {
    Surface(
        color = Color(0xFF17372F),
        contentColor = Color(0xFF82E7C7),
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(6.dp),
                color = Color(0xFF82E7C7),
                shape = RoundedCornerShape(999.dp),
            ) {}
            Text(
                text = stringResource(R.string.status_connected),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DashboardBody(
    state: ColoresUiState,
    perZone: Boolean,
    colorEnabled: Boolean,
    brightnessEnabled: Boolean,
    onTargetChange: (EditTarget) -> Unit,
    onColorChange: (RgbColor) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onLedPreviewChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val left = state.ledState.zoneColors.firstOrNull() ?: state.editingColor
        val right = state.ledState.zoneColors.getOrElse(1) { left }
        if (maxWidth >= 760.dp) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DeviceScene(
                    leftColor = left,
                    rightColor = right,
                    selectedTarget = state.editTarget,
                    power = state.ledState.power,
                    enabled = state.canWrite && colorEnabled,
                    perZone = perZone,
                    projection = state.ledColorProjection,
                    onLedPreviewChange = onLedPreviewChange,
                    onTargetChange = onTargetChange,
                    modifier = Modifier.weight(0.88f).fillMaxHeight(),
                )
                ColorControlPanel(
                    state = state,
                    perZone = perZone,
                    colorEnabled = colorEnabled,
                    brightnessEnabled = brightnessEnabled,
                    onTargetChange = onTargetChange,
                    onColorChange = onColorChange,
                    onSaturationChange = onSaturationChange,
                    onBrightnessChange = onBrightnessChange,
                    modifier = Modifier.weight(1.12f).fillMaxHeight(),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DeviceScene(
                    leftColor = left,
                    rightColor = right,
                    selectedTarget = state.editTarget,
                    power = state.ledState.power,
                    enabled = state.canWrite && colorEnabled,
                    perZone = perZone,
                    projection = state.ledColorProjection,
                    onLedPreviewChange = onLedPreviewChange,
                    onTargetChange = onTargetChange,
                    modifier = Modifier.fillMaxWidth().height(380.dp),
                )
                ColorControlPanel(
                    state = state,
                    perZone = perZone,
                    colorEnabled = colorEnabled,
                    brightnessEnabled = brightnessEnabled,
                    onTargetChange = onTargetChange,
                    onColorChange = onColorChange,
                    onSaturationChange = onSaturationChange,
                    onBrightnessChange = onBrightnessChange,
                    modifier = Modifier.fillMaxWidth().height(430.dp),
                )
            }
        }
    }
}

@Composable
private fun ControlStatusCard(
    title: String,
    description: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null && onAction != null) {
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}
