package com.hooandee.colores.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hooandee.colores.R
import com.hooandee.colores.control.AppMode
import com.hooandee.colores.engine.EffectNeed
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.sensor.PerformanceMetric
import kotlin.math.roundToInt

data class ModeActions(
    val onModeChange: (AppMode) -> Unit,
    val onSensorModeChange: (AppMode) -> Unit,
    val onEffectSelect: (String) -> Unit,
    val onSpeedChange: (Int) -> Unit,
    val onChargerOnlyChange: (Boolean) -> Unit,
    val onBatteryBreatheChange: (Boolean) -> Unit,
)

@Composable
fun ModeNav(
    modes: List<AppMode>,
    selected: AppMode,
    enabled: Boolean,
    onModeChange: (AppMode) -> Unit,
) {
    if (modes.size <= 1) return
    val navSelected = if (selected.isSensor) AppMode.BATTERY else selected
    val entries = modes.filterNot { it.isSensor && it != AppMode.BATTERY }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, mode ->
            val isSelected = mode == navSelected
            SegmentedButton(
                modifier = Modifier.height(46.dp),
                selected = isSelected,
                onClick = { onModeChange(mode) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, entries.size),
                label = { Text(navLabel(mode)) },
            )
        }
    }
}

@Composable
fun ChargerOnlyRow(
    chargerOnly: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.charger_only_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.charger_only_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = chargerOnly, onCheckedChange = onChange, enabled = enabled)
        }
    }
}

@Composable
fun ModeControlPanel(
    state: ColoresUiState,
    perZone: Boolean,
    colorEnabled: Boolean,
    brightnessEnabled: Boolean,
    onTargetChange: (EditTarget) -> Unit,
    onColorChange: (RgbColor) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    gradientActions: GradientActions,
    modeActions: ModeActions,
    modifier: Modifier = Modifier,
) {
    when (state.mode) {
        AppMode.COLOR, AppMode.GRADIENT ->
            ColorControlPanel(
                state = state,
                perZone = perZone,
                colorEnabled = colorEnabled,
                brightnessEnabled = brightnessEnabled,
                onTargetChange = onTargetChange,
                onColorChange = onColorChange,
                onSaturationChange = onSaturationChange,
                onBrightnessChange = onBrightnessChange,
                gradientActions = gradientActions,
                modifier = modifier,
            )
        AppMode.EFFECT ->
            PanelSurface(modifier) {
                EffectsPanel(state, onColorChange, onSaturationChange, onBrightnessChange, gradientActions, modeActions)
            }
        AppMode.BATTERY, AppMode.TEMPERATURE, AppMode.PERFORMANCE ->
            PanelSurface(modifier) {
                SensorsPanel(state, onBrightnessChange, modeActions)
            }
        AppMode.CLOCK ->
            PanelSurface(modifier) {
                ClockPanel(state, onBrightnessChange)
            }
    }
}

@Composable
private fun PanelSurface(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun EffectsPanel(
    state: ColoresUiState,
    onColorChange: (RgbColor) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    gradientActions: GradientActions,
    modeActions: ModeActions,
) {
    SectionLabel(stringResource(R.string.effects_title))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(state.effects, key = { it.id }) { preset ->
            EffectChip(
                label = effectLabel(preset.id),
                selected = preset.id == state.effectId,
                enabled = state.canWrite,
                onClick = { modeActions.onEffectSelect(preset.id) },
            )
        }
    }
    ValueSlider(
        label = stringResource(R.string.effect_speed),
        valueLabel = "${state.speed}%",
        value = state.speed.toFloat(),
        onValueChange = { modeActions.onSpeedChange(it.roundToInt()) },
        valueRange = 0f..100f,
        enabled = state.canWrite,
    )
    when (state.currentEffect?.need ?: EffectNeed.COLOR) {
        EffectNeed.GRADIENT ->
            if (state.gradientEditable) {
                Text(
                    text = stringResource(R.string.effect_uses_gradient),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                GradientControls(state = state, actions = gradientActions)
            } else {
                EffectColorEditor(state, onColorChange, onSaturationChange)
            }
        EffectNeed.COLOR -> EffectColorEditor(state, onColorChange, onSaturationChange)
        EffectNeed.NONE ->
            Text(
                text = stringResource(R.string.effect_uses_none),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
    }
    BrightnessRow(state, onBrightnessChange)
}

@Composable
private fun EffectColorEditor(
    state: ColoresUiState,
    onColorChange: (RgbColor) -> Unit,
    onSaturationChange: (Float) -> Unit,
) {
    val hsv = state.editingColor.toHsvColor()
    Text(
        text = stringResource(R.string.effect_uses_color),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    Row(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(0.9f).fillMaxSize(), contentAlignment = Alignment.Center) {
            RingColorPicker(
                color = state.editingColor,
                enabled = state.canWrite,
                projection = state.ledColorProjection,
                contentDescription = stringResource(R.string.color_wheel_description),
                onColorChange = onColorChange,
            )
        }
        Column(modifier = Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ColorSwatchRow(state)
            ValueSlider(
                label = stringResource(R.string.saturation_title),
                valueLabel = "${(hsv.saturation * 100f).roundToInt()}%",
                value = hsv.saturation,
                onValueChange = onSaturationChange,
                valueRange = 0f..1f,
                enabled = state.canWrite,
            )
        }
    }
}

@Composable
private fun ColorSwatchRow(state: ColoresUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.rgb_sent_value, state.editingColor.toHexString()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Surface(
            modifier = Modifier.size(30.dp),
            color = state.ledColorProjection.display(state.editingColor).toComposeColor(),
            shape = CircleShape,
            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.72f)),
        ) {}
    }
}

@Composable
private fun SensorsPanel(
    state: ColoresUiState,
    onBrightnessChange: (Int) -> Unit,
    modeActions: ModeActions,
) {
    val sensorModes = state.availableSensorModes()
    if (sensorModes.size > 1) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            sensorModes.forEachIndexed { index, mode ->
                SegmentedButton(
                    modifier = Modifier.height(44.dp),
                    selected = state.mode == mode,
                    onClick = { modeActions.onSensorModeChange(mode) },
                    enabled = state.canWrite,
                    shape = SegmentedButtonDefaults.itemShape(index, sensorModes.size),
                    label = { Text(sensorLabel(mode)) },
                )
            }
        }
    }
    when (state.mode) {
        AppMode.BATTERY -> BatteryContent(state, modeActions)
        AppMode.TEMPERATURE -> TemperatureContent(state)
        AppMode.PERFORMANCE -> PerformanceContent(state)
        else -> Unit
    }
    BrightnessRow(state, onBrightnessChange)
}

@Composable
private fun BatteryContent(
    state: ColoresUiState,
    modeActions: ModeActions,
) {
    ReadoutCard(
        title = stringResource(R.string.battery_title),
        description = stringResource(R.string.battery_description),
        value = state.batteryLevelPercent?.let { stringResource(R.string.battery_level_value, it) },
        detail = if (state.charging) stringResource(R.string.battery_charging) else null,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.battery_breathe),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
        )
        Switch(
            checked = state.batteryBreathe,
            onCheckedChange = modeActions.onBatteryBreatheChange,
            enabled = state.canWrite,
        )
    }
}

@Composable
private fun TemperatureContent(state: ColoresUiState) {
    if (!state.temperatureAvailable) {
        ReadoutCard(
            title = stringResource(R.string.temperature_unavailable_title),
            description = stringResource(R.string.temperature_unavailable_description),
            value = null,
            detail = null,
        )
        return
    }
    ReadoutCard(
        title = stringResource(R.string.temperature_title),
        description = stringResource(R.string.temperature_description),
        value = state.temperatureCelsius?.let { stringResource(R.string.temperature_value, it.roundToInt().toString()) },
        detail = null,
    )
}

@Composable
private fun PerformanceContent(state: ColoresUiState) {
    val metric = state.performanceMetric
    if (metric == null) {
        ReadoutCard(
            title = stringResource(R.string.performance_unavailable_title),
            description = stringResource(R.string.performance_unavailable_description),
            value = null,
            detail = null,
        )
        return
    }
    ReadoutCard(
        title = stringResource(R.string.performance_title),
        description = stringResource(R.string.performance_description),
        value = null,
        detail =
            when (metric) {
                PerformanceMetric.GPU -> stringResource(R.string.performance_source_gpu)
                PerformanceMetric.CPU -> stringResource(R.string.performance_source_cpu)
            },
    )
}

@Composable
private fun ClockPanel(
    state: ColoresUiState,
    onBrightnessChange: (Int) -> Unit,
) {
    ReadoutCard(
        title = stringResource(R.string.clock_title),
        description = stringResource(R.string.clock_description),
        value = null,
        detail = null,
    )
    BrightnessRow(state, onBrightnessChange)
}

@Composable
private fun ReadoutCard(
    title: String,
    description: String,
    value: String?,
    detail: String?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF181920),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (detail != null) {
                    Text(detail, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (value != null) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BrightnessRow(
    state: ColoresUiState,
    onBrightnessChange: (Int) -> Unit,
) {
    if (!state.brightnessEnabled) return
    ValueSlider(
        label = stringResource(R.string.brightness_title),
        valueLabel = stringResource(R.string.brightness_value, state.ledState.brightness),
        value = state.ledState.brightness.toFloat(),
        onValueChange = { onBrightnessChange(it.roundToInt()) },
        valueRange = 0f..100f,
        enabled = state.canWrite,
    )
}

@Composable
private fun EffectChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(44.dp).semantics { this.selected = selected },
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color(0xFF181920),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border =
            BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
            ),
    ) {
        Box(modifier = Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ValueSlider(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(12.dp))
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, enabled = enabled)
    }
}

@Composable
private fun navLabel(mode: AppMode): String =
    when (mode) {
        AppMode.COLOR -> stringResource(R.string.nav_color)
        AppMode.GRADIENT -> stringResource(R.string.nav_gradient)
        AppMode.EFFECT -> stringResource(R.string.nav_effects)
        AppMode.BATTERY, AppMode.TEMPERATURE, AppMode.PERFORMANCE -> stringResource(R.string.nav_sensors)
        AppMode.CLOCK -> stringResource(R.string.nav_clock)
    }

@Composable
private fun sensorLabel(mode: AppMode): String =
    when (mode) {
        AppMode.BATTERY -> stringResource(R.string.sensor_battery)
        AppMode.TEMPERATURE -> stringResource(R.string.sensor_temperature)
        AppMode.PERFORMANCE -> stringResource(R.string.sensor_performance)
        else -> ""
    }

@Composable
private fun effectLabel(id: String): String =
    when (id) {
        "breathing" -> stringResource(R.string.effect_breathing)
        "rainbow" -> stringResource(R.string.effect_rainbow)
        "wave" -> stringResource(R.string.effect_wave)
        "cycle" -> stringResource(R.string.effect_cycle)
        "spiral" -> stringResource(R.string.effect_spiral)
        "comet" -> stringResource(R.string.effect_comet)
        "sparkle" -> stringResource(R.string.effect_sparkle)
        "ripple" -> stringResource(R.string.effect_ripple)
        "aurora" -> stringResource(R.string.effect_aurora)
        "marquee" -> stringResource(R.string.effect_marquee)
        "chasing" -> stringResource(R.string.effect_chasing)
        "gaming" -> stringResource(R.string.effect_gaming)
        else -> id
    }
