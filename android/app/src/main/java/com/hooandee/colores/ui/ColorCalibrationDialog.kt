package com.hooandee.colores.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hooandee.colores.R
import com.hooandee.colores.engine.ColorCalibration
import com.hooandee.colores.engine.ColorCalibration.Step
import com.hooandee.colores.led.RgbColor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate

private const val PREVIEW_THROTTLE_MS = 80L

@Composable
internal fun ColorCalibrationDialog(
    initial: ColorCalibration,
    onPreview: (ColorCalibration?, RgbColor) -> Unit,
    onSave: (ColorCalibration) -> Unit,
    onDismiss: () -> Unit,
) {
    var calibration by remember(initial) { mutableStateOf(initial) }
    var index by remember { mutableIntStateOf(0) }
    val step = Step.entries[index]
    val last = index == Step.entries.lastIndex
    val preview by rememberUpdatedState(onPreview)
    LaunchedEffect(Unit) {
        snapshotFlow { calibration to Step.entries[index] }
            .conflate()
            .collect { (value, current) ->
                preview(value, current.sample)
                delay(PREVIEW_THROTTLE_MS)
            }
    }
    DisposableEffect(Unit) { onDispose { preview(null, RgbColor(255, 255, 255)) } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(Color(step.sample.red, step.sample.green, step.sample.blue), CircleShape),
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        "${stringResource(R.string.color_calibration_title)} · ${index + 1}/${Step.entries.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stepTitle(step), fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stepHint(step),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                GlassSlider(
                    value = calibration.valueOf(step).toFloat(),
                    onValueChange = { calibration = calibration.with(step, it.roundToInt()) },
                    valueRange = -step.limit.toFloat()..step.limit.toFloat(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stepLess(step), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stepMore(step), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (last) {
                        onSave(calibration)
                        onDismiss()
                    } else {
                        index++
                    }
                },
            ) {
                Text(stringResource(if (last) R.string.color_calibration_save else R.string.color_calibration_next))
            }
        },
        dismissButton = {
            TextButton(onClick = { if (index == 0) calibration = ColorCalibration.NEUTRAL else index-- }) {
                Text(stringResource(if (index == 0) R.string.color_calibration_reset else R.string.color_calibration_back))
            }
        },
    )
}

@Composable
private fun stepTitle(step: Step): String =
    stringResource(
        when (step) {
            Step.YELLOW -> R.string.color_calibration_yellow
            Step.ORANGE -> R.string.color_calibration_orange
            Step.WHITE -> R.string.color_calibration_white
        },
    )

@Composable
private fun stepHint(step: Step): String =
    stringResource(
        when (step) {
            Step.YELLOW -> R.string.color_calibration_yellow_hint
            Step.ORANGE -> R.string.color_calibration_orange_hint
            Step.WHITE -> R.string.color_calibration_white_hint
        },
    )

@Composable
private fun stepLess(step: Step): String =
    stringResource(
        when (step) {
            Step.YELLOW, Step.ORANGE -> R.string.color_calibration_more_red
            Step.WHITE -> R.string.color_calibration_warmer
        },
    )

@Composable
private fun stepMore(step: Step): String =
    stringResource(
        when (step) {
            Step.YELLOW -> R.string.color_calibration_more_green
            Step.ORANGE -> R.string.color_calibration_more_yellow
            Step.WHITE -> R.string.color_calibration_cooler
        },
    )
