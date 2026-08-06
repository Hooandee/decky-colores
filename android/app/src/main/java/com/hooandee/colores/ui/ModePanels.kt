package com.hooandee.colores.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hooandee.colores.R
import com.hooandee.colores.audio.AudioCaptureStatus
import com.hooandee.colores.control.AppMode
import com.hooandee.colores.engine.EffectNeed
import com.hooandee.colores.engine.AudioScale
import com.hooandee.colores.engine.AudioSensitivity
import com.hooandee.colores.engine.SensorBand
import com.hooandee.colores.engine.SensorKind
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.sensor.PerformanceMetric
import kotlin.math.roundToInt

data class ModeActions(
    val onModeChange: (AppMode) -> Unit,
    val onSensorModeChange: (AppMode) -> Unit,
    val onEffectSelect: (String) -> Unit,
    val onSpeedChange: (Int) -> Unit,
    val onEffectGradientChange: (Boolean) -> Unit,
    val onChargerOnlyChange: (Boolean) -> Unit,
    val onBatteryBreatheChange: (Boolean) -> Unit,
    val onTemperatureBreatheChange: (Boolean) -> Unit,
    val onSensorBandsChange: (SensorKind, List<SensorBand>) -> Unit,
    val onAudioScaleChange: (AudioScale) -> Unit,
    val onAudioSensitivityChange: (Int) -> Unit,
    val onAudioCaptureRequest: () -> Unit,
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
        AppMode.AUDIO ->
            PanelSurface(modifier) {
                AudioPanel(
                    state,
                    onBrightnessChange,
                    modeActions.onAudioCaptureRequest,
                    modeActions.onAudioScaleChange,
                    modeActions.onAudioSensitivityChange,
                )
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
    DeferredIntSlider(
        label = stringResource(R.string.effect_speed),
        committedValue = state.speed,
        valueLabel = { "$it%" },
        onValueCommit = modeActions.onSpeedChange,
        valueRange = 0..100,
        enabled = state.canWrite,
        resetKey = state.effectId,
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
        EffectNeed.COLOR -> {
            if (state.canUseGradientForEffect) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.effect_use_gradient),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.effect_use_gradient_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = state.effectUsesGradient,
                        onCheckedChange = modeActions.onEffectGradientChange,
                        enabled = state.canWrite,
                    )
                }
            }
            if (state.effectNeedsGradient) {
                Text(
                    text = stringResource(R.string.effect_uses_gradient),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                GradientControls(state = state, actions = gradientActions)
            } else {
                EffectColorEditor(state, onColorChange, onSaturationChange)
            }
        }
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
            DeferredIntSlider(
                label = stringResource(R.string.saturation_title),
                committedValue = (hsv.saturation * 100f).roundToInt(),
                valueLabel = { "$it%" },
                onValueCommit = { onSaturationChange(it / 100f) },
                valueRange = 0..100,
                enabled = state.canWrite,
                resetKey = state.effectId,
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
    var editingScale by remember { mutableStateOf<SensorKind?>(null) }
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
        AppMode.BATTERY -> BatteryContent(state, modeActions, onCustomize = { editingScale = SensorKind.BATTERY })
        AppMode.TEMPERATURE -> TemperatureContent(state, modeActions, onCustomize = { editingScale = SensorKind.TEMPERATURE })
        AppMode.PERFORMANCE -> PerformanceContent(state)
        else -> Unit
    }
    BrightnessRow(state, onBrightnessChange)
    editingScale?.let { kind ->
        SensorScaleDialog(
            kind = kind,
            initial = state.sensorBands.bands(kind),
            defaults = state.sensorBandDefaults.bands(kind),
            projection = state.ledColorProjection,
            onSave = { modeActions.onSensorBandsChange(kind, it) },
            onDismiss = { editingScale = null },
        )
    }
}

@Composable
private fun BatteryContent(
    state: ColoresUiState,
    modeActions: ModeActions,
    onCustomize: () -> Unit,
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
    OutlinedButton(
        onClick = onCustomize,
        enabled = state.canWrite,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.sensor_scale_customize))
    }
}

@Composable
private fun TemperatureContent(
    state: ColoresUiState,
    modeActions: ModeActions,
    onCustomize: () -> Unit,
) {
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
        value = state.temperatureCelsius?.let { stringResource(R.string.temperature_value, it) },
        detail = null,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.temperature_breathe),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
        )
        Switch(
            checked = state.temperatureBreathe,
            onCheckedChange = modeActions.onTemperatureBreatheChange,
            enabled = state.canWrite,
        )
    }
    OutlinedButton(
        onClick = onCustomize,
        enabled = state.canWrite,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.sensor_scale_customize))
    }
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
private fun AudioPanel(
    state: ColoresUiState,
    onBrightnessChange: (Int) -> Unit,
    onCaptureRequest: () -> Unit,
    onScaleChange: (AudioScale) -> Unit,
    onSensitivityChange: (Int) -> Unit,
) {
    var editingScale by remember { mutableStateOf(false) }
    val status =
        when (state.audio.status) {
            AudioCaptureStatus.AUTHORIZATION_REQUIRED -> stringResource(R.string.audio_status_authorization_required)
            AudioCaptureStatus.STARTING -> stringResource(R.string.audio_status_starting)
            AudioCaptureStatus.CAPTURING -> stringResource(R.string.audio_status_capturing)
            AudioCaptureStatus.NO_AUDIO -> stringResource(R.string.audio_status_no_audio)
            AudioCaptureStatus.REVOKED -> stringResource(R.string.audio_status_revoked)
            AudioCaptureStatus.ERROR -> stringResource(R.string.audio_status_error)
        }
    ReadoutCard(
        title = stringResource(R.string.audio_title),
        description = stringResource(R.string.audio_description),
        value = null,
        detail = status,
    )
    OutlinedButton(
        onClick = { editingScale = true },
        enabled = state.canWrite,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.audio_scale_title), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.audio_scale_customize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(modifier = Modifier.width(112.dp)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(state.audioScale.previewBrush(), RoundedCornerShape(999.dp)),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.audio_scale_low),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = stringResource(R.string.audio_scale_peak),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
    DeferredIntSlider(
        label = stringResource(R.string.audio_sensitivity_title),
        committedValue = state.audioSensitivityDb,
        valueLabel = { audioSensitivityLabel(it) },
        onValueCommit = onSensitivityChange,
        valueRange = AudioSensitivity.MIN_DB..AudioSensitivity.MAX_DB,
        steps = AudioSensitivity.MAX_DB - AudioSensitivity.MIN_DB - 1,
        enabled = state.canWrite,
    )
    Text(
        text = stringResource(R.string.audio_privacy),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    if (state.audioNeedsAuthorization) {
        OutlinedButton(
            onClick = onCaptureRequest,
            enabled = state.canWrite && state.ledState.power,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.audio_activate))
        }
    }
    BrightnessRow(state, onBrightnessChange)
    if (editingScale) {
        AudioScaleDialog(
            initial = state.audioScale,
            level = AudioSensitivity.adjust(state.audio.level, state.audioSensitivityDb),
            active = state.audio.status == AudioCaptureStatus.CAPTURING,
            projection = state.ledColorProjection,
            onSave = onScaleChange,
            onDismiss = { editingScale = false },
        )
    }
}

private fun AudioScale.previewBrush(): Brush =
    Brush.horizontalGradient(
        0f to lowColor.toComposeColor(),
        mediumAt / 100f to mediumColor.toComposeColor(),
        peakAt / 100f to peakColor.toComposeColor(),
    )

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
    DeferredIntSlider(
        label = stringResource(R.string.brightness_title),
        committedValue = state.ledState.brightness,
        valueLabel = { stringResource(R.string.brightness_value, it) },
        onValueCommit = onBrightnessChange,
        valueRange = 0..100,
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
internal fun ValueSlider(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
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
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
        )
    }
}

@Composable
private fun audioSensitivityLabel(gainDb: Int): String =
    when {
        gainDb < AudioSensitivity.NORMAL_DB -> stringResource(R.string.audio_sensitivity_soft, gainDb)
        gainDb > AudioSensitivity.NORMAL_DB -> stringResource(R.string.audio_sensitivity_intense, gainDb)
        else -> stringResource(R.string.audio_sensitivity_normal)
    }

@Composable
private fun navLabel(mode: AppMode): String =
    when (mode) {
        AppMode.COLOR -> stringResource(R.string.nav_color)
        AppMode.GRADIENT -> stringResource(R.string.nav_gradient)
        AppMode.EFFECT -> stringResource(R.string.nav_effects)
        AppMode.BATTERY, AppMode.TEMPERATURE, AppMode.PERFORMANCE -> stringResource(R.string.nav_sensors)
        AppMode.CLOCK -> stringResource(R.string.nav_clock)
        AppMode.AUDIO -> stringResource(R.string.nav_audio)
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
