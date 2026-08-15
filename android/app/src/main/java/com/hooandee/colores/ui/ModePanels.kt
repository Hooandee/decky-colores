package com.hooandee.colores.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hooandee.colores.R
import com.hooandee.colores.ambient.AmbientCaptureStatus
import com.hooandee.colores.ambient.AmbientSamplingMode
import com.hooandee.colores.audio.AudioCaptureStatus
import com.hooandee.colores.control.AppMode
import com.hooandee.colores.engine.Effects
import com.hooandee.colores.engine.EffectNeed
import com.hooandee.colores.engine.AudioScale
import com.hooandee.colores.engine.AudioSensitivity
import com.hooandee.colores.engine.SensorBand
import com.hooandee.colores.engine.SensorKind
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.sensor.PerformanceMetric
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

data class ModeActions(
    val onModeChange: (AppMode) -> Unit,
    val onSensorModeChange: (AppMode) -> Unit,
    val onEffectSelect: (String) -> Unit,
    val onSpeedChange: (Int) -> Unit,
    val onEffectGradientChange: (Boolean) -> Unit,
    val onBatteryBreatheChange: (Boolean) -> Unit,
    val onTemperatureBreatheChange: (Boolean) -> Unit,
    val onSensorBandsChange: (SensorKind, List<SensorBand>) -> Unit,
    val onAudioScaleChange: (AudioScale) -> Unit,
    val onAudioSensitivityChange: (Int) -> Unit,
    val onAudioCaptureRequest: () -> Unit,
    val onAmbientCaptureRequest: () -> Unit,
    val onAmbientCaptureFpsChange: (Int) -> Unit,
    val onAmbientSamplingModeChange: (AmbientSamplingMode) -> Unit,
    val onAmbientVividnessChange: (Int) -> Unit,
    val onAmbientSmoothingChange: (Int) -> Unit,
)

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
        AppMode.AMBIENT ->
            PanelSurface(modifier) {
                AmbientPanel(state, onBrightnessChange, modeActions)
            }
    }
}

@Composable
private fun PanelSurface(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.prismaticPanel(RoundedCornerShape(32.dp), strong = true),
        color = Color.Transparent,
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
        AppMode.BATTERY ->
            BatteryContent(
                state,
                modeActions,
                onBrightnessChange,
                onCustomize = { editingScale = SensorKind.BATTERY },
            )
        AppMode.TEMPERATURE ->
            TemperatureContent(
                state,
                modeActions,
                onBrightnessChange,
                onCustomize = { editingScale = SensorKind.TEMPERATURE },
            )
        AppMode.PERFORMANCE -> PerformanceContent(state, onBrightnessChange)
        else -> Unit
    }
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
    onBrightnessChange: (Int) -> Unit,
    onCustomize: () -> Unit,
) {
    EditorialModeLayout(
        title = stringResource(R.string.battery_title),
        description = stringResource(R.string.battery_description),
        value = state.batteryLevelPercent?.let { stringResource(R.string.battery_level_value, it) },
        status = if (state.charging) stringResource(R.string.battery_charging) else null,
    ) {
        SettingsControlRow(
            label = stringResource(R.string.battery_breathe),
            checked = state.batteryBreathe,
            enabled = state.canWrite,
            onCheckedChange = modeActions.onBatteryBreatheChange,
        )
        OutlinedButton(
            onClick = onCustomize,
            enabled = state.canWrite,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sensor_scale_customize))
        }
        BrightnessRow(state, onBrightnessChange)
    }
}

@Composable
private fun TemperatureContent(
    state: ColoresUiState,
    modeActions: ModeActions,
    onBrightnessChange: (Int) -> Unit,
    onCustomize: () -> Unit,
) {
    if (!state.temperatureAvailable) {
        EditorialModeLayout(
            title = stringResource(R.string.temperature_unavailable_title),
            description = stringResource(R.string.temperature_unavailable_description),
            value = null,
            status = null,
        ) {
            BrightnessRow(state, onBrightnessChange)
        }
        return
    }
    EditorialModeLayout(
        title = stringResource(R.string.temperature_title),
        description = stringResource(R.string.temperature_description),
        value = state.temperatureCelsius?.let { stringResource(R.string.temperature_value, it) },
        status = null,
    ) {
        SettingsControlRow(
            label = stringResource(R.string.temperature_breathe),
            checked = state.temperatureBreathe,
            enabled = state.canWrite,
            onCheckedChange = modeActions.onTemperatureBreatheChange,
        )
        OutlinedButton(
            onClick = onCustomize,
            enabled = state.canWrite,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sensor_scale_customize))
        }
        BrightnessRow(state, onBrightnessChange)
    }
}

@Composable
private fun PerformanceContent(
    state: ColoresUiState,
    onBrightnessChange: (Int) -> Unit,
) {
    val metric = state.performanceMetric
    if (metric == null) {
        EditorialModeLayout(
            title = stringResource(R.string.performance_unavailable_title),
            description = stringResource(R.string.performance_unavailable_description),
            value = null,
            status = null,
        ) {
            BrightnessRow(state, onBrightnessChange)
        }
        return
    }
    val source =
        when (metric) {
            PerformanceMetric.GPU -> stringResource(R.string.performance_source_gpu)
            PerformanceMetric.CPU -> stringResource(R.string.performance_source_cpu)
        }
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.performance_title).uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.8.sp,
                )
                Text(
                    text = source,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                ModeExplanationAction(
                    title = stringResource(R.string.performance_title),
                    description = stringResource(R.string.performance_description),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).background(Color(0xFF8DE8C5), CircleShape))
                    Text(
                        text = stringResource(R.string.performance_source_active),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        BrightnessRow(state, onBrightnessChange)
    }
}

@Composable
private fun ClockPanel(
    state: ColoresUiState,
    onBrightnessChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L)
            currentTimeMillis = System.currentTimeMillis()
        }
    }
    val currentTime = remember(currentTimeMillis, context) {
        DateFormat.getTimeFormat(context).format(Date(currentTimeMillis))
    }
    val currentHour = remember(currentTimeMillis) {
        Calendar.getInstance().run {
            timeInMillis = currentTimeMillis
            get(Calendar.HOUR_OF_DAY) + get(Calendar.MINUTE) / 60.0
        }
    }
    val lightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    ClockSolarScene(
        currentTime = currentTime,
        currentHour = currentHour,
        projection = state.ledColorProjection,
        explanation = {
            ModeExplanationAction(
                title = stringResource(R.string.clock_title),
                description = stringResource(R.string.clock_description),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 10.dp),
                contentColor = if (lightTheme) MaterialTheme.colorScheme.onSurface else Color(0xFFF4F7FA),
            )
        },
    )
    BrightnessRow(state, onBrightnessChange)
}

@Composable
private fun ClockSolarScene(
    currentTime: String,
    currentHour: Double,
    projection: LedColorProjection,
    explanation: @Composable BoxScope.() -> Unit,
) {
    val palette = remember(projection) {
        List(49) { index ->
            projection.display(Effects.clockColor(index / 2.0)).toComposeColor()
        }
    }
    val currentColor = projection.display(Effects.clockColor(currentHour)).toComposeColor()
    val shape = RoundedCornerShape(26.dp)
    val lightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val sceneInk = if (lightTheme) MaterialTheme.colorScheme.onSurface else Color.White
    val sceneOutline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (lightTheme) 0.8f else 0.34f)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(174.dp)
                .clip(shape)
                .border(1.dp, sceneOutline, shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val horizonY = size.height * 0.64f
            val sunPhase = ((currentHour - 6.0) / 12.0 * PI).toFloat()
            val sunCenter =
                Offset(
                    x = size.width * (currentHour / 24.0).toFloat(),
                    y = horizonY - sin(sunPhase) * size.height * 0.42f,
                )
            val timelineStart = 24.dp.toPx()
            val timelineEnd = size.width - timelineStart
            val timelineY = size.height - 18.dp.toPx()
            val timelineHeight = 5.dp.toPx()
            val paletteBrush = Brush.horizontalGradient(palette)

            drawRect(paletteBrush)
            drawRect(
                Brush.verticalGradient(
                    0f to if (lightTheme) Color.White.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.48f),
                    0.48f to if (lightTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f),
                    1f to if (lightTheme) Color.White.copy(alpha = 0.48f) else Color.Black.copy(alpha = 0.62f),
                ),
            )

            val solarArc =
                Path().apply {
                    moveTo(size.width * 0.25f, horizonY)
                    cubicTo(
                        size.width * 0.36f,
                        size.height * 0.12f,
                        size.width * 0.64f,
                        size.height * 0.12f,
                        size.width * 0.75f,
                        horizonY,
                    )
                }
            drawPath(
                path = solarArc,
                color = sceneInk.copy(alpha = 0.28f),
                style = Stroke(width = 1.dp.toPx()),
            )

            if (sunCenter.y < size.height + 48.dp.toPx()) {
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(currentColor.copy(alpha = 0.78f), Color.Transparent),
                            center = sunCenter,
                            radius = 52.dp.toPx(),
                        ),
                    radius = 52.dp.toPx(),
                    center = sunCenter,
                )
                if (sunCenter.y <= horizonY) {
                    drawCircle(
                        color = if (lightTheme) Color(0xFFFFFBF1) else Color.White.copy(alpha = 0.92f),
                        radius = 9.dp.toPx(),
                        center = sunCenter,
                    )
                }
            }

            val horizon =
                Path().apply {
                    moveTo(0f, horizonY)
                    quadraticTo(size.width * 0.5f, horizonY - 6.dp.toPx(), size.width, horizonY)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
            drawPath(
                horizon,
                if (lightTheme) Color(0xFFF6F4F0).copy(alpha = 0.82f) else Color(5, 12, 22).copy(alpha = 0.76f),
            )
            drawLine(
                color = sceneInk.copy(alpha = if (lightTheme) 0.16f else 0.26f),
                start = Offset(0f, horizonY),
                end = Offset(size.width, horizonY),
                strokeWidth = 1.dp.toPx(),
            )

            drawRoundRect(
                brush = paletteBrush,
                topLeft = Offset(timelineStart, timelineY),
                size = androidx.compose.ui.geometry.Size(timelineEnd - timelineStart, timelineHeight),
                cornerRadius = CornerRadius(timelineHeight / 2f),
            )
            val markerX = timelineStart + (timelineEnd - timelineStart) * (currentHour / 24.0).toFloat()
            drawLine(
                color = sceneInk,
                start = Offset(markerX, timelineY - 5.dp.toPx()),
                end = Offset(markerX, timelineY + timelineHeight + 5.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
            )
            for (hour in listOf(0, 6, 12, 18, 24)) {
                val x = timelineStart + (timelineEnd - timelineStart) * (hour / 24f)
                drawCircle(
                    color = sceneInk.copy(alpha = 0.42f),
                    radius = 1.5.dp.toPx(),
                    center = Offset(x, timelineY + timelineHeight / 2f),
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.clock_title).uppercase(),
                color = sceneInk.copy(alpha = 0.68f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.8.sp,
            )
            Text(
                text = currentTime,
                color = sceneInk,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }
        explanation()
    }
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
    val statusColor =
        when (state.audio.status) {
            AudioCaptureStatus.CAPTURING -> Color(0xFF8DE8C5)
            AudioCaptureStatus.STARTING -> Color(0xFFF4F7FA)
            AudioCaptureStatus.NO_AUDIO -> Color(0xFFFFC978)
            AudioCaptureStatus.AUTHORIZATION_REQUIRED -> MaterialTheme.colorScheme.onSurfaceVariant
            AudioCaptureStatus.REVOKED, AudioCaptureStatus.ERROR -> Color(0xFFFF9B8F)
        }
    if (state.audioNeedsAuthorization) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.audio_title).uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.8.sp,
                )
                ModeExplanationAction(
                    title = stringResource(R.string.audio_title),
                    description = stringResource(R.string.audio_description),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AudioStatus(status, statusColor, Modifier.weight(1f))
                val activationDescription = stringResource(R.string.audio_activate_accessibility)
                OutlinedButton(
                    onClick = onCaptureRequest,
                    enabled = state.canWrite && state.ledState.power,
                    modifier = Modifier.height(40.dp).semantics { contentDescription = activationDescription },
                ) {
                    Text(stringResource(R.string.audio_activate))
                }
            }
            Text(
                text = stringResource(R.string.audio_privacy),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(R.string.audio_title).uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.8.sp,
                )
                AudioStatus(status, statusColor)
            }
            ModeExplanationAction(
                title = stringResource(R.string.audio_title),
                description = stringResource(R.string.audio_description),
            )
        }
    }
    OutlinedButton(
        onClick = { editingScale = true },
        enabled = state.canWrite,
        modifier = Modifier.fillMaxWidth().height(68.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.audio_scale_title), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.audio_scale_customize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            Column(modifier = Modifier.width(146.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(R.string.audio_scale_peak),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            DeferredIntSlider(
                label = stringResource(R.string.audio_sensitivity_title),
                committedValue = state.audioSensitivityDb,
                valueLabel = { audioSensitivityLabel(it) },
                onValueCommit = onSensitivityChange,
                valueRange = AudioSensitivity.MIN_DB..AudioSensitivity.MAX_DB,
                steps = AudioSensitivity.MAX_DB - AudioSensitivity.MIN_DB - 1,
                enabled = state.canWrite,
            )
        }
        if (state.brightnessEnabled) {
            Column(modifier = Modifier.weight(1f)) {
                DeferredIntSlider(
                    label = stringResource(R.string.brightness_title),
                    committedValue = state.ledState.brightness,
                    valueLabel = { stringResource(R.string.brightness_value, it) },
                    onValueCommit = onBrightnessChange,
                    valueRange = 0..100,
                    enabled = state.canWrite,
                )
            }
        }
    }
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

@Composable
private fun AudioStatus(
    status: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(
            text = status,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun AmbientPanel(
    state: ColoresUiState,
    onBrightnessChange: (Int) -> Unit,
    actions: ModeActions,
) {
    val status =
        when (state.ambient.status) {
            AmbientCaptureStatus.AUTHORIZATION_REQUIRED -> stringResource(R.string.ambient_status_authorization_required)
            AmbientCaptureStatus.STARTING -> stringResource(R.string.ambient_status_starting)
            AmbientCaptureStatus.CAPTURING -> stringResource(R.string.ambient_status_capturing)
            AmbientCaptureStatus.NO_FRAMES -> stringResource(R.string.ambient_status_no_frames)
            AmbientCaptureStatus.REVOKED -> stringResource(R.string.ambient_status_revoked)
            AmbientCaptureStatus.ERROR -> stringResource(R.string.ambient_status_error)
        }
    if (state.ambientNeedsAuthorization) {
        Text(
            text = stringResource(R.string.ambient_privacy),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    AmbientSceneWindow(
        state = state,
        status = status,
        onActivate = actions.onAmbientCaptureRequest,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ambient_sampling_title), style = MaterialTheme.typography.labelMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 1.dp)) {
                AmbientSamplingMode.entries.forEachIndexed { index, mode ->
                    val label =
                        if (mode == AmbientSamplingMode.FULL_SCENE) {
                            stringResource(R.string.ambient_sampling_full)
                        } else {
                            stringResource(R.string.ambient_sampling_bottom)
                        }
                    val accessibleLabel =
                        if (mode == AmbientSamplingMode.FULL_SCENE) {
                            stringResource(R.string.ambient_sampling_full_accessibility)
                        } else {
                            stringResource(R.string.ambient_sampling_bottom_accessibility)
                        }
                    SegmentedButton(
                        modifier =
                            Modifier
                                .height(46.dp)
                                .semantics { contentDescription = accessibleLabel },
                        selected = state.ambientSamplingMode == mode,
                        onClick = { actions.onAmbientSamplingModeChange(mode) },
                        enabled = state.canWrite,
                        shape = SegmentedButtonDefaults.itemShape(index, AmbientSamplingMode.entries.size),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        icon = {},
                    ) {
                        Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            DeferredIntSlider(
                label = stringResource(R.string.ambient_capture_rate),
                committedValue = state.ambientCaptureFps,
                valueLabel = { stringResource(R.string.ambient_capture_rate_value, it) },
                onValueCommit = actions.onAmbientCaptureFpsChange,
                valueRange = 5..30,
                steps = 4,
                enabled = state.canWrite,
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            DeferredIntSlider(
                label = stringResource(R.string.ambient_vividness),
                committedValue = state.ambientVividness,
                valueLabel = { stringResource(R.string.percent_value, it) },
                onValueCommit = actions.onAmbientVividnessChange,
                valueRange = 0..100,
                enabled = state.canWrite,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            DeferredIntSlider(
                label = stringResource(R.string.ambient_smoothing),
                committedValue = state.ambientSmoothing,
                valueLabel = { stringResource(R.string.percent_value, it) },
                onValueCommit = actions.onAmbientSmoothingChange,
                valueRange = 0..100,
                enabled = state.canWrite,
            )
        }
    }
    BrightnessRow(state, onBrightnessChange)
}

@Composable
private fun AmbientSceneWindow(
    state: ColoresUiState,
    status: String,
    onActivate: () -> Unit,
) {
    val capturing = state.ambient.status == AmbientCaptureStatus.CAPTURING
    val frameColors =
        if (capturing) {
            state.currentFrame.map { state.ledColorProjection.display(it).toComposeColor() }
        } else {
            emptyList()
        }
    val sceneColors =
        when (frameColors.size) {
            0 -> listOf(Color(0xFF131A25), Color(0xFF273347), Color(0xFF111723))
            1 -> listOf(frameColors.first(), frameColors.first())
            else -> frameColors
        }
    val statusColor =
        when (state.ambient.status) {
            AmbientCaptureStatus.CAPTURING -> Color(0xFF8DE8C5)
            AmbientCaptureStatus.STARTING -> Color(0xFFF4F7FA)
            AmbientCaptureStatus.NO_FRAMES -> Color(0xFFFFC978)
            AmbientCaptureStatus.AUTHORIZATION_REQUIRED -> Color(0xFFD8E0E7)
            AmbientCaptureStatus.REVOKED, AmbientCaptureStatus.ERROR -> Color(0xFFFF9B8F)
        }
    val needsAuthorization = state.ambientNeedsAuthorization
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(if (needsAuthorization) 96.dp else 78.dp)
                .clip(shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 6.dp.toPx()
            val innerRadius = 16.dp.toPx()
            val sceneBrush = Brush.horizontalGradient(sceneColors)

            drawRect(if (capturing) sceneBrush else Brush.linearGradient(sceneColors))
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.18f),
                    0.55f to Color.Black.copy(alpha = 0.04f),
                    1f to Color.Black.copy(alpha = 0.42f),
                ),
            )
            drawRoundRect(
                color = Color(0xFF070A0F).copy(alpha = if (capturing) 0.84f else 0.94f),
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2f, size.height - inset * 2f),
                cornerRadius = CornerRadius(innerRadius),
            )
            if (capturing) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(sceneColors.map { it.copy(alpha = 0.18f) }),
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2f, size.height - inset * 2f),
                    cornerRadius = CornerRadius(innerRadius),
                )
                sceneColors.forEachIndexed { index, color ->
                    val progress = (index + 0.5f) / sceneColors.size
                    val center = Offset(size.width * progress, size.height * 0.88f)
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors = listOf(color.copy(alpha = 0.3f), Color.Transparent),
                                center = center,
                                radius = 48.dp.toPx(),
                            ),
                        radius = 48.dp.toPx(),
                        center = center,
                    )
                }
                val lightHorizon =
                    Path().apply {
                        moveTo(20.dp.toPx(), size.height * 0.72f)
                        cubicTo(
                            size.width * 0.34f,
                            size.height * 0.66f,
                            size.width * 0.66f,
                            size.height * 0.78f,
                            size.width - 20.dp.toPx(),
                            size.height * 0.72f,
                        )
                    }
                drawPath(
                    path = lightHorizon,
                    brush = sceneBrush,
                    alpha = 0.62f,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            drawRoundRect(
                color = Color.White.copy(alpha = 0.18f),
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2f, size.height - inset * 2f),
                cornerRadius = CornerRadius(innerRadius),
                style = Stroke(width = 1.dp.toPx()),
            )
            if (capturing) {
                val railStart = 20.dp.toPx()
                val railEnd = size.width - railStart
                val railY = size.height - 10.dp.toPx()
                val railHeight = 3.dp.toPx()
                val gap = 2.dp.toPx()
                val segmentWidth = ((railEnd - railStart) - gap * (sceneColors.size - 1)) / sceneColors.size
                sceneColors.forEachIndexed { index, color ->
                    drawRoundRect(
                        color = color.copy(alpha = 0.92f),
                        topLeft = Offset(railStart + index * (segmentWidth + gap), railY),
                        size = androidx.compose.ui.geometry.Size(segmentWidth, railHeight),
                        cornerRadius = CornerRadius(railHeight / 2f),
                    )
                }
            }
        }

        if (needsAuthorization) {
            val activationDescription = stringResource(R.string.ambient_activate_accessibility)
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.ambient_title).uppercase(),
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.8.sp,
                    )
                    ModeExplanationAction(
                        title = stringResource(R.string.ambient_title),
                        description = stringResource(R.string.ambient_description),
                        modifier = Modifier.height(36.dp),
                        contentColor = Color(0xFFF4F7FA),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                        Text(
                            text = status,
                            color = statusColor,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                    OutlinedButton(
                        onClick = onActivate,
                        enabled = state.canWrite && state.ledState.power,
                        modifier =
                            Modifier
                                .height(40.dp)
                                .semantics { contentDescription = activationDescription },
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.58f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF4F7FA)),
                    ) {
                        Text(stringResource(R.string.ambient_activate))
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 8.dp, bottom = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = stringResource(R.string.ambient_title).uppercase(),
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.8.sp,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                        Text(
                            text = status,
                            color = statusColor,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
                ModeExplanationAction(
                    title = stringResource(R.string.ambient_title),
                    description = stringResource(R.string.ambient_description),
                    contentColor = Color(0xFFF4F7FA),
                )
            }
        }
    }
}

private fun AudioScale.previewBrush(): Brush =
    Brush.horizontalGradient(
        0f to lowColor.toComposeColor(),
        mediumAt / 100f to mediumColor.toComposeColor(),
        peakAt / 100f to peakColor.toComposeColor(),
    )

@Composable
private fun EditorialModeLayout(
    title: String,
    description: String,
    value: String?,
    status: String?,
    compactValue: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(0.42f).heightIn(min = 190.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title.uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.8.sp,
                )
                if (value != null) {
                    Text(
                        text = value,
                        style =
                            if (compactValue) {
                                MaterialTheme.typography.headlineLarge
                            } else {
                                MaterialTheme.typography.displaySmall
                            },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier =
                        Modifier
                            .width(72.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp)),
                )
                if (status != null) {
                    Text(
                        text = status,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                ModeExplanationAction(
                    title = title,
                    description = description,
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .weight(0.58f)
                    .heightIn(min = 190.dp)
                    .drawBehind {
                        drawLine(
                            color = dividerColor,
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(0f, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    .padding(start = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsControlRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeExplanationAction(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    contentColor: Color? = null,
) {
    var visible by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val resolvedContentColor = contentColor ?: MaterialTheme.colorScheme.onSurface

    TextButton(
        onClick = { visible = true },
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = resolvedContentColor),
    ) {
        Text(stringResource(R.string.mode_how_it_works))
        Spacer(Modifier.width(6.dp))
        Text("›", style = MaterialTheme.typography.titleMedium)
    }

    if (visible) {
        ModalBottomSheet(
            onDismissRequest = { visible = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            scrimColor = Color.Black.copy(alpha = 0.58f),
            dragHandle = null,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier =
                        Modifier
                            .width(64.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp)),
                )
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
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
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
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
