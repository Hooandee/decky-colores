package com.hooandee.colores.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        PrismaticBackdrop(modifier = Modifier.fillMaxSize()) {
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
    val zones = state.editorZones()
    val projection = state.ledColorProjection
    val animated = state.gradientAnimated
    Surface(
        modifier = modifier.prismaticPanel(RoundedCornerShape(28.dp), strong = true),
        color = Color.Transparent,
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (animated) {
                AnimatedGradientJourney(state.gradient.stops.map(projection::display))
                Text(
                    text = stringResource(R.string.gradient_editor_paused_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                AnimatedStopSelector(state, actions)
            } else {
                GradientPreviewBar(state.gradient.stops.map(projection::display))
                Text(
                    text = stringResource(R.string.gradient_editor_choose_zone),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!animated && zones.any { it.stick != null }) {
                zones.groupBy { it.stick }.toSortedMap(compareBy { it ?: -1 }).forEach { (stick, stickZones) ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel(stickLabel(stick))
                        ZoneGrid(stickZones, state, actions, colorStops = false)
                    }
                }
            } else if (!animated) {
                SectionLabel(stringResource(R.string.gradient_stops))
                ZoneGrid(zones, state, actions, colorStops = false)
            }
        }
    }
}

@Composable
private fun AnimatedGradientJourney(colors: List<RgbColor>) {
    val shown = colors.ifEmpty { listOf(RgbColor(34, 35, 43), RgbColor(34, 35, 43)) }
    val first = shown.first().toComposeColor()
    val last = shown.last().toComposeColor()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GradientLightSample(first)
            Box(
                Modifier
                    .weight(1f)
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(shown.map(RgbColor::toComposeColor)))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            GradientLightSample(last)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.gradient_editor_start_color),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.gradient_editor_end_color),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GradientLightSample(color: Color) {
    Box(
        modifier =
            Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.92f), color, color.copy(alpha = 0.42f), Color.Transparent),
                    ),
                )
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

@Composable
private fun AnimatedStopSelector(
    state: ColoresUiState,
    actions: GradientActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth().focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.gradient.stops.take(2).forEachIndexed { index, stop ->
            val selected = state.gradient.selectedStopIndex == index
            val label =
                stringResource(
                    if (index == 0) R.string.gradient_editor_start_color else R.string.gradient_editor_end_color,
                )
            var focused by remember { mutableStateOf(false) }
            Surface(
                onClick = { actions.onStopChange(index) },
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 92.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .semantics {
                            this.selected = selected
                            this.role = Role.RadioButton
                            contentDescription = label
                        },
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(20.dp),
                border =
                    BorderStroke(
                        if (selected || focused) 2.dp else 1.dp,
                        if (focused || selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        color = state.ledColorProjection.display(stop).toComposeColor(),
                        shape = CircleShape,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                    ) {}
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            stop.toHexString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneGrid(
    zones: List<GradientEditorZone>,
    state: ColoresUiState,
    actions: GradientActions,
    colorStops: Boolean,
) {
    val rows = (zones.maxOfOrNull { it.row } ?: 0) + 1
    val cols = (zones.maxOfOrNull { it.col } ?: 0) + 1
    Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(cols) { col ->
                    val zone = zones.firstOrNull { it.row == row && it.col == col }
                    if (zone != null) {
                        ZoneCell(zone, state, actions, colorStops, Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneCell(
    zone: GradientEditorZone,
    state: ColoresUiState,
    actions: GradientActions,
    colorStop: Boolean,
    modifier: Modifier,
) {
    val selected = state.gradient.selectedStopIndex == zone.index
    val color = state.ledColorProjection.display(state.gradient.stops[zone.index])
    val description = if (colorStop) stringResource(R.string.gradient_color_number, zone.index + 1) else zoneCellDescription(zone)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = { actions.onStopChange(zone.index) },
        modifier =
            modifier
                .heightIn(min = 76.dp)
                .onFocusChanged { focused = it.isFocused }
                .semantics {
                    this.selected = selected
                    this.role = Role.RadioButton
                    contentDescription = description
                },
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border =
            BorderStroke(
                if (selected || focused) 2.dp else 1.dp,
                if (focused || selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                modifier = Modifier.size(30.dp),
                color = color.toComposeColor(),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
            ) {}
            Text(
                text = if (colorStop) stringResource(R.string.gradient_color_number, zone.index + 1) else zoneShortLabel(zone),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun stickName(stick: Int?): String? =
    when (stick) {
        0 -> stringResource(R.string.gradient_stick_left)
        1 -> stringResource(R.string.gradient_stick_right)
        else -> null
    }

@Composable
private fun stickLabel(stick: Int?): String = stickName(stick) ?: stringResource(R.string.gradient_stops)

@Composable
private fun zoneCellDescription(zone: GradientEditorZone): String {
    val stick = stickName(zone.stick)
    val position = zone.position?.let { positionLabel(it) } ?: stringResource(R.string.gradient_zone_number, zone.index + 1)
    return if (stick != null) "$stick · $position" else position
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GradientColorPane(
    state: ColoresUiState,
    actions: GradientActions,
    modifier: Modifier,
) {
    var saveName by rememberSaveable { mutableStateOf("") }
    val selectedZone = state.editorZones().getOrNull(state.gradient.selectedStopIndex)
    val color = state.editingColor
    val saturation = color.toHsvColor().saturation
    var saturationSlider by remember(state.gradient.selectedStopIndex, saturation) {
        mutableStateOf(DeferredIntSliderState((saturation * 100f).roundToInt()))
    }
    Surface(
        modifier = modifier.prismaticPanel(RoundedCornerShape(28.dp), strong = true),
        color = Color.Transparent,
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(54.dp),
                    color = state.ledColorProjection.display(color).toComposeColor(),
                    shape = CircleShape,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                ) {}
                Column {
                    Text(
                        text = stringResource(
                            if (state.gradientAnimated) R.string.gradient_editor_selected_color else R.string.gradient_editor_selected_zone,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text =
                            selectedZone?.let {
                                if (state.gradientAnimated) {
                                    stringResource(
                                        if (it.index == 0) R.string.gradient_editor_start_color else R.string.gradient_editor_end_color,
                                    )
                                } else {
                                    zoneLongLabel(it)
                                }
                            }.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(236.dp),
                contentAlignment = Alignment.Center,
            ) {
                RingColorPicker(
                    color = color,
                    enabled = state.canWrite,
                    projection = state.ledColorProjection,
                    contentDescription = stringResource(R.string.color_wheel_description),
                    onColorChange = actions.onColorChange,
                )
            }
            Text(
                text = color.toHexString(),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.saturation_title), style = MaterialTheme.typography.labelMedium)
                Text("${saturationSlider.stagedValue}%", fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = saturationSlider.stagedValue / 100f,
                onValueChange = {
                    saturationSlider =
                        reduceDeferredIntSlider(
                            saturationSlider,
                            DeferredIntSliderEvent.Drag((it * 100f).roundToInt()),
                        ).state
                },
                onValueChangeFinished = {
                    val update = reduceDeferredIntSlider(saturationSlider, DeferredIntSliderEvent.Finish)
                    saturationSlider = update.state
                    update.commitValue?.let { actions.onSaturationChange(it / 100f) }
                },
                valueRange = 0f..1f,
                enabled = state.canWrite,
                track = {
                    val hue = color.toHsvColor().hue
                    val projection = state.ledColorProjection
                    val start = projection.display(HsvColor(hue, 0f, 1f).toRgbColor()).toComposeColor()
                    val end = projection.display(HsvColor(hue, 1f, 1f).toRgbColor()).toComposeColor()
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(start, end))),
                    )
                },
            )
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
            )
            SectionLabel(stringResource(R.string.gradient_save_title))
            Row(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    modifier = Modifier.weight(1f).widthIn(min = 160.dp),
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
            OutlinedButton(
                onClick = actions.onRestore,
                enabled = state.gradient.selectedPresetId != null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.gradient_restore))
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
    val stick = stickName(zone.stick) ?: return position
    return "$stick · $position"
}

private fun ColoresUiState.editorZones(): List<GradientEditorZone> =
    gradientEditorZones(
        layout = if (gradientAnimated) null else detected?.gridLayout,
        count = gradient.stops.size,
    )

@Composable
private fun positionLabel(position: GradientZonePosition): String =
    when (position) {
        GradientZonePosition.TOP -> stringResource(R.string.gradient_position_top)
        GradientZonePosition.LEFT -> stringResource(R.string.gradient_position_left)
        GradientZonePosition.BOTTOM -> stringResource(R.string.gradient_position_bottom)
        GradientZonePosition.RIGHT -> stringResource(R.string.gradient_position_right)
        GradientZonePosition.TOP_LEFT -> stringResource(R.string.gradient_position_top_left)
        GradientZonePosition.TOP_RIGHT -> stringResource(R.string.gradient_position_top_right)
        GradientZonePosition.BOTTOM_LEFT -> stringResource(R.string.gradient_position_bottom_left)
        GradientZonePosition.BOTTOM_RIGHT -> stringResource(R.string.gradient_position_bottom_right)
    }
