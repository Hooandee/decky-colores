package com.hooandee.colores.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hooandee.colores.R
import com.hooandee.colores.device.DeviceCapabilities
import com.hooandee.colores.led.RgbColor
import kotlin.math.roundToInt

@Composable
fun ColoresScreen(
    viewModel: ColoresViewModel,
    onGrantPermission: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            ScreenContent(
                state = state,
                onGrantPermission = onGrantPermission,
                onPowerChange = viewModel::setPower,
                onBrightnessChange = viewModel::setBrightness,
                onSameColorChange = viewModel::setSameColor,
                onSolidColorChange = viewModel::setSolidColor,
                onZoneColorChange = viewModel::setZoneColor,
            )
        }
    }
}

@Composable
private fun ScreenContent(
    state: ColoresUiState,
    onGrantPermission: () -> Unit,
    onPowerChange: (Boolean) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onSameColorChange: (Boolean) -> Unit,
    onSolidColorChange: (RgbColor) -> Unit,
    onZoneColorChange: (Int, RgbColor) -> Unit,
) {
    val detected = state.detected
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Text(
            text = detected?.friendlyName ?: stringResource(R.string.device_unknown),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (detected == null) {
            UnknownDeviceCard()
            return@Column
        }
        CapabilityChips(detected.capabilities)
        when (state.controlAccess) {
            ControlAccess.USER_PERMISSION_REQUIRED -> PermissionCard(onGrantPermission)
            ControlAccess.SERVICE_UNAVAILABLE -> ControlServiceUnavailableCard()
            ControlAccess.ENABLED -> Unit
        }
        val controlsModifier = Modifier.alpha(if (state.canWrite) 1f else 0.42f)
        SectionCard(modifier = controlsModifier) {
            SettingRow(
                title = stringResource(R.string.power_title),
                description = stringResource(R.string.power_description),
            ) {
                Switch(
                    checked = state.ledState.power,
                    onCheckedChange = onPowerChange,
                    enabled = state.canWrite,
                )
            }
        }
        if (detected.capabilities.color) {
            ColorSection(
                state = state,
                enabled = state.canWrite,
                perZone = detected.capabilities.perZone && detected.capabilities.zones > 1,
                onSameColorChange = onSameColorChange,
                onSolidColorChange = onSolidColorChange,
                onZoneColorChange = onZoneColorChange,
                modifier = controlsModifier,
            )
        }
        if (detected.capabilities.brightness) {
            SectionCard(modifier = controlsModifier) {
                Text(
                    text = stringResource(R.string.brightness_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.brightness_value, state.ledState.brightness),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = state.ledState.brightness.toFloat(),
                    onValueChange = { onBrightnessChange(it.roundToInt()) },
                    valueRange = 0f..100f,
                    enabled = state.canWrite,
                )
            }
        }
    }
}

@Composable
private fun ControlServiceUnavailableCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.control_service_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.control_service_description),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ColorSection(
    state: ColoresUiState,
    enabled: Boolean,
    perZone: Boolean,
    onSameColorChange: (Boolean) -> Unit,
    onSolidColorChange: (RgbColor) -> Unit,
    onZoneColorChange: (Int, RgbColor) -> Unit,
    modifier: Modifier,
) {
    SectionCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.color_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (perZone) {
            Spacer(Modifier.height(8.dp))
            SettingRow(title = stringResource(R.string.same_color)) {
                Switch(
                    checked = state.sameColor,
                    onCheckedChange = onSameColorChange,
                    enabled = enabled,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (state.sameColor || !perZone) {
            ColorControl(
                label = stringResource(R.string.both_sticks),
                color = state.ledState.zoneColors.first(),
                enabled = enabled,
                onColorChange = onSolidColorChange,
            )
        } else {
            ColorControl(
                label = stringResource(R.string.left_stick),
                color = state.ledState.zoneColors.first(),
                enabled = enabled,
                onColorChange = { onZoneColorChange(0, it) },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
            ColorControl(
                label = stringResource(R.string.right_stick),
                color = state.ledState.zoneColors.getOrElse(1) { state.ledState.zoneColors.first() },
                enabled = enabled,
                onColorChange = { onZoneColorChange(1, it) },
            )
        }
    }
}

@Composable
private fun ColorControl(
    label: String,
    color: RgbColor,
    enabled: Boolean,
    onColorChange: (RgbColor) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontWeight = FontWeight.Medium)
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .background(color.toComposeColor(), CircleShape),
        )
    }
    Spacer(Modifier.height(14.dp))
    HsvColorWheel(
        color = color,
        enabled = enabled,
        onColorChange = onColorChange,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PermissionCard(onGrantPermission: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.permission_description),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(onClick = onGrantPermission) {
                Text(stringResource(R.string.permission_button))
            }
        }
    }
}

@Composable
private fun UnknownDeviceCard() {
    SectionCard {
        Text(
            text = stringResource(R.string.no_leds_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_leds_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CapabilityChips(capabilities: DeviceCapabilities) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (capabilities.color) CapabilityChip(stringResource(R.string.capability_color))
        if (capabilities.brightness) CapabilityChip(stringResource(R.string.capability_brightness))
        if (capabilities.perZone) CapabilityChip(stringResource(R.string.capability_zones, capabilities.zones))
    }
}

@Composable
private fun CapabilityChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, fontWeight = FontWeight.SemiBold)
            if (description != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        trailing()
    }
}

private fun RgbColor.toComposeColor(): Color = Color(red, green, blue)
