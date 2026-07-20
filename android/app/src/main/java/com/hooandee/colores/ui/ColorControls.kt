package com.hooandee.colores.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hooandee.colores.R
import com.hooandee.colores.gradient.LightingMode
import com.hooandee.colores.led.RgbColor
import kotlin.math.roundToInt

private val QUICK_COLORS =
    listOf(
        RgbColor(255, 255, 255),
        RgbColor(255, 60, 86),
        RgbColor(255, 153, 0),
        RgbColor(255, 221, 0),
        RgbColor(38, 214, 108),
        RgbColor(0, 194, 255),
        RgbColor(93, 81, 255),
    )

@Composable
fun ColorControlPanel(
    state: ColoresUiState,
    perZone: Boolean,
    colorEnabled: Boolean,
    brightnessEnabled: Boolean,
    onTargetChange: (EditTarget) -> Unit,
    onColorChange: (RgbColor) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    gradientActions: GradientActions,
    modifier: Modifier = Modifier,
) {
    val projection = state.ledColorProjection
    val editingHsv = state.editingColor.toHsvColor()
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(32.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val fontScale = LocalDensity.current.fontScale
            val colorAreaHeight = if (maxHeight < 380.dp && fontScale <= 1.15f) 120.dp else 170.dp
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                if (colorEnabled) {
                    if (state.gradientAvailable) {
                        LightingModeSelector(
                            mode = state.gradient.mode,
                            enabled = state.canWrite,
                            onModeChange = gradientActions.onModeChange,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    if (state.gradient.mode == LightingMode.GRADIENT) {
                        GradientControls(state = state, actions = gradientActions)
                    } else {
                        TargetSelector(
                            target = state.editTarget,
                            perZone = perZone,
                            enabled = state.canWrite,
                            onTargetChange = onTargetChange,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().height(colorAreaHeight),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.weight(0.9f).fillMaxHeight(),
                                contentAlignment = Alignment.Center,
                            ) {
                                HsvColorWheel(
                                    color = state.editingColor,
                                    enabled = state.canWrite,
                                    projection = projection,
                                    contentDescription = stringResource(R.string.color_wheel_description),
                                    onColorChange = onColorChange,
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1.1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CurrentColor(state, projection)
                                LabeledSlider(
                                    label = stringResource(R.string.saturation_title),
                                    valueLabel = "${(editingHsv.saturation * 100f).roundToInt()}%",
                                    value = editingHsv.saturation,
                                    onValueChange = onSaturationChange,
                                    valueRange = 0f..1f,
                                    enabled = state.canWrite,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        QuickColors(
                            current = state.editingColor,
                            mixed = state.mixedTarget,
                            enabled = state.canWrite,
                            projection = projection,
                            onColorChange = onColorChange,
                        )
                    }
                }
                if (brightnessEnabled) {
                    if (colorEnabled) Spacer(Modifier.height(4.dp))
                    LabeledSlider(
                        label = stringResource(R.string.brightness_title),
                        valueLabel = stringResource(R.string.brightness_value, state.ledState.brightness),
                        value = state.ledState.brightness.toFloat(),
                        onValueChange = { onBrightnessChange(it.roundToInt()) },
                        valueRange = 0f..100f,
                        enabled = state.canWrite,
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetSelector(
    target: EditTarget,
    perZone: Boolean,
    enabled: Boolean,
    onTargetChange: (EditTarget) -> Unit,
) {
    val targets =
        if (perZone) {
            EditTarget.entries
        } else {
            listOf(EditTarget.BOTH)
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.target_title),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            targets.forEachIndexed { index, item ->
                SegmentedButton(
                    modifier = Modifier.height(48.dp),
                    selected = target == item,
                    onClick = { onTargetChange(item) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, targets.size),
                    label = { Text(targetLabel(item)) },
                )
            }
        }
    }
}

@Composable
private fun targetLabel(target: EditTarget): String =
    when (target) {
        EditTarget.BOTH -> stringResource(R.string.target_both)
        EditTarget.LEFT -> stringResource(R.string.target_left)
        EditTarget.RIGHT -> stringResource(R.string.target_right)
    }

@Composable
private fun CurrentColor(
    state: ColoresUiState,
    projection: LedColorProjection,
) {
    val shownColor = projection.display(state.editingColor)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(R.string.color_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text =
                    if (state.mixedTarget) {
                        stringResource(R.string.target_mixed)
                    } else {
                        stringResource(R.string.rgb_sent_value, state.editingColor.toHexString())
                    },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Surface(
            modifier = Modifier.size(34.dp),
            color = shownColor.toComposeColor(),
            shape = CircleShape,
            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.72f)),
        ) {}
    }
}

@Composable
private fun QuickColors(
    current: RgbColor,
    mixed: Boolean,
    enabled: Boolean,
    projection: LedColorProjection,
    onColorChange: (RgbColor) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.quick_colors),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            QUICK_COLORS.forEach { color ->
                val selected = !mixed && current == color
                val shownColor = projection.display(color)
                val description = stringResource(R.string.hex_color, color.toHexString())
                Surface(
                    onClick = { onColorChange(color) },
                    enabled = enabled,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = description
                                this.selected = selected
                            },
                    color = Color.Transparent,
                    shape = CircleShape,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            color = shownColor.toComposeColor(),
                            shape = CircleShape,
                            border =
                                BorderStroke(
                                    width = if (selected) 2.dp else 1.dp,
                                    color =
                                        if (selected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            Color.White.copy(alpha = 0.18f)
                                        },
                                ),
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
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
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
        )
    }
}
