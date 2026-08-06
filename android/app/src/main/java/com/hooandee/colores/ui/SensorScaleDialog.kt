package com.hooandee.colores.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hooandee.colores.R
import com.hooandee.colores.engine.SensorBand
import com.hooandee.colores.engine.SensorKind
import kotlin.math.roundToInt

@Composable
internal fun SensorScaleDialog(
    kind: SensorKind,
    initial: List<SensorBand>,
    defaults: List<SensorBand>,
    projection: LedColorProjection,
    onSave: (List<SensorBand>) -> Unit,
    onDismiss: () -> Unit,
) {
    var model by remember(kind, initial) { mutableStateOf(SensorScaleEditorModel.create(kind, initial)) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 22.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sensor_scale_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.sensor_scale_subtitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                        Text(stringResource(R.string.sensor_scale_cancel))
                    }
                }
                Spacer(Modifier.height(12.dp))
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    if (maxWidth >= 720.dp) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            SensorBandList(model, projection, { model = model.select(it) }, Modifier.weight(1f).fillMaxHeight())
                            SensorBandEditor(model, projection, { model = it }, Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SensorBandList(model, projection, { model = model.select(it) }, Modifier.fillMaxWidth().weight(0.9f))
                            SensorBandEditor(model, projection, { model = it }, Modifier.fillMaxWidth().weight(1.1f))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { model = SensorScaleEditorModel.create(kind, defaults).select(model.selectedIndex) },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(stringResource(R.string.sensor_scale_reset))
                    }
                    Button(
                        onClick = {
                            onSave(model.bands)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(stringResource(R.string.sensor_scale_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorBandList(
    model: SensorScaleEditorModel,
    projection: LedColorProjection,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.sensor_scale_preview), style = MaterialTheme.typography.labelLarge)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(
                            Brush.horizontalGradient(model.bands.asReversed().map { projection.display(it.color).toComposeColor() }),
                            RoundedCornerShape(999.dp),
                        ),
            )
            Spacer(Modifier.height(2.dp))
            Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                model.bands.forEachIndexed { index, band ->
                    val selected = index == model.selectedIndex
                    Surface(
                        onClick = { onSelect(index) },
                        modifier = Modifier.fillMaxWidth().semantics {
                            this.selected = selected
                            role = Role.RadioButton
                        },
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color(0xFF181920),
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(if (selected) 2.dp else 1.dp, Color.White.copy(alpha = if (selected) 0.48f else 0.08f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(30.dp),
                                color = projection.display(band.color).toComposeColor(),
                                shape = CircleShape,
                                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.5f)),
                            ) {}
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sensorBandLabel(model.kind, index), fontWeight = FontWeight.SemiBold)
                                Text(
                                    sensorThresholdLabel(model.kind, band.min.toInt()),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorBandEditor(
    model: SensorScaleEditorModel,
    projection: LedColorProjection,
    onChange: (SensorScaleEditorModel) -> Unit,
    modifier: Modifier,
) {
    val band = model.selectedBand
    val saturation = band.color.toHsvColor().saturation
    val range = model.thresholdRange
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(sensorBandLabel(model.kind, model.selectedIndex), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                RingColorPicker(
                    color = band.color,
                    enabled = true,
                    projection = projection,
                    contentDescription = stringResource(R.string.sensor_scale_color),
                    onColorChange = { onChange(model.updateColor(it)) },
                )
            }
            Text(
                band.color.toHexString(),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.saturation_title), style = MaterialTheme.typography.labelMedium)
                Text("${(saturation * 100).roundToInt()}%", fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = saturation,
                onValueChange = {
                    val changed = band.color.toHsvColor().copy(saturation = it).toRgbColor()
                    onChange(model.updateColor(changed))
                },
                valueRange = 0f..1f,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.sensor_scale_threshold), style = MaterialTheme.typography.labelMedium)
                Text(sensorThresholdLabel(model.kind, band.min.toInt()), fontWeight = FontWeight.SemiBold)
            }
            if (range == null) {
                Text(
                    stringResource(R.string.sensor_scale_minimum_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Slider(
                    value = band.min.toFloat(),
                    onValueChange = { onChange(model.updateThreshold(it.roundToInt())) },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    enabled = range.first < range.last,
                )
            }
        }
    }
}

@Composable
private fun sensorThresholdLabel(
    kind: SensorKind,
    value: Int,
): String =
    when (kind) {
        SensorKind.BATTERY -> stringResource(R.string.sensor_scale_threshold_percent, value)
        SensorKind.TEMPERATURE -> stringResource(R.string.sensor_scale_threshold_celsius, value)
    }

@Composable
private fun sensorBandLabel(
    kind: SensorKind,
    index: Int,
): String {
    val resource =
        when (kind) {
            SensorKind.BATTERY ->
                listOf(
                    R.string.battery_band_1,
                    R.string.battery_band_2,
                    R.string.battery_band_3,
                    R.string.battery_band_4,
                    R.string.battery_band_5,
                )
            SensorKind.TEMPERATURE ->
                listOf(
                    R.string.temperature_band_1,
                    R.string.temperature_band_2,
                    R.string.temperature_band_3,
                    R.string.temperature_band_4,
                    R.string.temperature_band_5,
                )
        }[index]
    return stringResource(resource)
}
