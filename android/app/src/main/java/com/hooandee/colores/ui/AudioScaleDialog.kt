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
import com.hooandee.colores.engine.AudioScale
import kotlin.math.roundToInt

@Composable
internal fun AudioScaleDialog(
    initial: AudioScale,
    level: Double,
    active: Boolean,
    projection: LedColorProjection,
    onSave: (AudioScale) -> Unit,
    onDismiss: () -> Unit,
) {
    var model by remember(initial) { mutableStateOf(AudioScaleEditorModel(initial)) }
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
                            text = stringResource(R.string.audio_scale_editor_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.audio_scale_editor_subtitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                        Text(stringResource(R.string.audio_scale_cancel))
                    }
                }
                Spacer(Modifier.height(12.dp))
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    if (maxWidth >= 720.dp) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            AudioScaleOverview(model, level, active, projection, { model = model.select(it) }, Modifier.weight(1f).fillMaxHeight())
                            AudioScaleColorEditor(model, projection, { model = it }, Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            AudioScaleOverview(model, level, active, projection, { model = model.select(it) }, Modifier.fillMaxWidth().weight(0.9f))
                            AudioScaleColorEditor(model, projection, { model = it }, Modifier.fillMaxWidth().weight(1.1f))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { model = model.reset() },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(stringResource(R.string.audio_scale_reset))
                    }
                    Button(
                        onClick = {
                            onSave(model.scale)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(stringResource(R.string.audio_scale_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioScaleOverview(
    model: AudioScaleEditorModel,
    level: Double,
    active: Boolean,
    projection: LedColorProjection,
    onSelect: (AudioScaleStop) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.audio_scale_preview), style = MaterialTheme.typography.labelLarge)
            AudioLevelBars(level, active, model.scale)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(model.scale.previewBrush(projection), RoundedCornerShape(999.dp)),
            )
            Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioScaleStop.entries.forEach { stop ->
                    val selected = stop == model.selected
                    val color = projection.display(model.scale.color(stop)).toComposeColor()
                    Surface(
                        onClick = { onSelect(stop) },
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
                                color = color,
                                shape = CircleShape,
                                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.5f)),
                            ) {}
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stop.label(), fontWeight = FontWeight.SemiBold)
                                Text(
                                    stop.rangeLabel(model.scale),
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
private fun AudioScaleColorEditor(
    model: AudioScaleEditorModel,
    projection: LedColorProjection,
    onChange: (AudioScaleEditorModel) -> Unit,
    modifier: Modifier,
) {
    val color = model.selectedColor
    val saturation = color.toHsvColor().saturation
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
            Text(model.selected.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                RingColorPicker(
                    color = color,
                    enabled = true,
                    projection = projection,
                    contentDescription = stringResource(R.string.audio_scale_color),
                    onColorChange = { onChange(model.updateColor(it)) },
                )
            }
            Text(
                color.toHexString(),
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
                    val changed = color.toHsvColor().copy(saturation = it).toRgbColor()
                    onChange(model.updateColor(changed))
                },
                valueRange = 0f..1f,
            )
            val threshold = model.threshold
            val range = model.thresholdRange
            if (threshold == null || range == null) {
                Text(
                    stringResource(R.string.audio_scale_low_range),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.audio_scale_threshold_title), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.audio_scale_threshold, threshold), fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = threshold.toFloat(),
                    onValueChange = { onChange(model.updateThreshold(it.roundToInt())) },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    enabled = range.first < range.last,
                )
                Text(
                    stringResource(R.string.audio_scale_threshold_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun AudioScale.color(stop: AudioScaleStop) =
    when (stop) {
        AudioScaleStop.LOW -> lowColor
        AudioScaleStop.MEDIUM -> mediumColor
        AudioScaleStop.PEAK -> peakColor
    }

private fun AudioScale.previewBrush(projection: LedColorProjection): Brush =
    Brush.horizontalGradient(
        0f to projection.display(lowColor).toComposeColor(),
        mediumAt / 100f to projection.display(mediumColor).toComposeColor(),
        peakAt / 100f to projection.display(peakColor).toComposeColor(),
    )

@Composable
private fun AudioScaleStop.label(): String =
    stringResource(
        when (this) {
            AudioScaleStop.LOW -> R.string.audio_scale_low
            AudioScaleStop.MEDIUM -> R.string.audio_scale_medium
            AudioScaleStop.PEAK -> R.string.audio_scale_peak
        },
    )

@Composable
private fun AudioScaleStop.rangeLabel(scale: AudioScale): String =
    when (this) {
        AudioScaleStop.LOW -> stringResource(R.string.audio_scale_low_range)
        AudioScaleStop.MEDIUM -> stringResource(R.string.audio_scale_threshold, scale.mediumAt)
        AudioScaleStop.PEAK -> stringResource(R.string.audio_scale_threshold, scale.peakAt)
    }
