package com.hooandee.colores.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hooandee.colores.R
import com.hooandee.colores.led.RgbColor
import kotlin.math.roundToInt

@Composable
internal fun GradientEditorDialog(
    state: ColoresUiState,
    actions: GradientActions,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 22.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.gradient_editor_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.gradient_editor_live_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                        Text(stringResource(R.string.gradient_editor_done), fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    if (maxWidth >= 720.dp) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            GradientZonesPane(state, actions, Modifier.weight(1.08f).fillMaxHeight())
                            GradientColorPane(state, actions, Modifier.weight(0.92f).fillMaxHeight())
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            GradientZonesPane(state, actions, Modifier.fillMaxWidth().weight(1f))
                            GradientColorPane(state, actions, Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradientZonesPane(
    state: ColoresUiState,
    actions: GradientActions,
    modifier: Modifier,
) {
    val zones = gradientEditorZones(state.detected?.id, state.gradient.stops.size)
    val projection = state.ledColorProjection
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
            GradientPreviewBar(state.gradient.stops.map(projection::display))
            Text(
                text = stringResource(R.string.gradient_editor_choose_zone),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (zones.all { it.stick != null }) {
                GradientZoneGroup(
                    label = stringResource(R.string.gradient_stick_left),
                    zones = zones.filter { it.stick == GradientStick.LEFT },
                    state = state,
                    actions = actions,
                )
                GradientZoneGroup(
                    label = stringResource(R.string.gradient_stick_right),
                    zones = zones.filter { it.stick == GradientStick.RIGHT },
                    state = state,
                    actions = actions,
                )
            } else {
                SectionLabel(stringResource(R.string.gradient_stops))
                ZoneRow(zones, state, actions)
            }
        }
    }
}

@Composable
private fun GradientZoneGroup(
    label: String,
    zones: List<GradientEditorZone>,
    state: ColoresUiState,
    actions: GradientActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(label)
        ZoneRow(zones, state, actions)
    }
}

@Composable
private fun ZoneRow(
    zones: List<GradientEditorZone>,
    state: ColoresUiState,
    actions: GradientActions,
) {
    val projection = state.ledColorProjection
    LazyRow(
        modifier = Modifier.fillMaxWidth().focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(zones, key = { it.index }) { zone ->
            StopTile(
                label = zoneShortLabel(zone),
                color = projection.display(state.gradient.stops[zone.index]),
                selected = state.gradient.selectedStopIndex == zone.index,
                onClick = { actions.onStopChange(zone.index) },
            )
        }
    }
}

@Composable
private fun GradientColorPane(
    state: ColoresUiState,
    actions: GradientActions,
    modifier: Modifier,
) {
    var saveName by rememberSaveable { mutableStateOf("") }
    val selectedZone =
        gradientEditorZones(state.detected?.id, state.gradient.stops.size)
            .getOrNull(state.gradient.selectedStopIndex)
    val color = state.editingColor
    val shownColor = state.ledColorProjection.display(color)
    val saturation = color.toHsvColor().saturation
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.gradient_editor_selected_zone),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = selectedZone?.let { zoneLongLabel(it) }.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = color.toHexString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        modifier = Modifier.size(34.dp),
                        color = shownColor.toComposeColor(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    ) {}
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(188.dp),
                contentAlignment = Alignment.Center,
            ) {
                HsvColorWheel(
                    color = color,
                    enabled = state.canWrite,
                    projection = state.ledColorProjection,
                    contentDescription = stringResource(R.string.color_wheel_description),
                    onColorChange = actions.onColorChange,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.saturation_title), style = MaterialTheme.typography.labelMedium)
                Text("${(saturation * 100f).roundToInt()}%", fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = saturation,
                onValueChange = actions.onSaturationChange,
                valueRange = 0f..1f,
                enabled = state.canWrite,
            )
            Row(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = actions.onReverse, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.gradient_reverse))
                }
                OutlinedButton(
                    onClick = actions.onRestore,
                    enabled = state.gradient.selectedPresetId != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.gradient_restore))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                    label = { Text(stringResource(R.string.gradient_name)) },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        actions.onSave(saveName)
                        saveName = ""
                    },
                    enabled = saveName.isNotBlank(),
                    modifier = Modifier.height(56.dp),
                ) {
                    Text(stringResource(R.string.gradient_save))
                }
            }
        }
    }
}

@Composable
private fun zoneShortLabel(zone: GradientEditorZone): String =
    zone.position?.let { positionLabel(it) } ?: stringResource(R.string.gradient_zone_number, zone.index + 1)

@Composable
private fun zoneLongLabel(zone: GradientEditorZone): String {
    val position = zone.position?.let { positionLabel(it) } ?: return stringResource(R.string.gradient_zone_number, zone.index + 1)
    val stick =
        when (zone.stick) {
            GradientStick.LEFT -> stringResource(R.string.gradient_stick_left)
            GradientStick.RIGHT -> stringResource(R.string.gradient_stick_right)
            null -> return position
        }
    return "$stick · $position"
}

@Composable
private fun positionLabel(position: GradientZonePosition): String =
    when (position) {
        GradientZonePosition.TOP -> stringResource(R.string.gradient_position_top)
        GradientZonePosition.LEFT -> stringResource(R.string.gradient_position_left)
        GradientZonePosition.BOTTOM -> stringResource(R.string.gradient_position_bottom)
        GradientZonePosition.RIGHT -> stringResource(R.string.gradient_position_right)
    }
