package com.hooandee.colores.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hooandee.colores.R
import com.hooandee.colores.device.LedGridCell
import com.hooandee.colores.led.RgbColor

@Composable
fun DeviceScene(
    frame: List<RgbColor>,
    layout: List<LedGridCell>?,
    selectedTarget: EditTarget,
    power: Boolean,
    enabled: Boolean,
    perZone: Boolean,
    projection: LedColorProjection,
    onLedPreviewChange: (Boolean) -> Unit,
    onTargetChange: (EditTarget) -> Unit,
    showBoth: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val ledPreviewLabel = stringResource(R.string.led_preview_toggle)
    val previewStyle = LocalLedPreviewStyle.current
    val preview = devicePreviewGroups(frame, layout)
    Surface(
        modifier = modifier,
        color = previewStyle.sceneBackground,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(32.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxHeight < 320.dp
            val ringSize = if (compact) (maxHeight - 150.dp).coerceIn(40.dp, 112.dp) else 112.dp
            val scenePadding = if (compact) 14.dp else 22.dp
            Column(
                modifier = Modifier.fillMaxSize().padding(scenePadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                stringResource(
                                    if (preview.representsSticks) R.string.preview_title else R.string.preview_lights_title,
                                ),
                            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!compact) {
                            Text(
                                text = stringResource(R.string.preview_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (projection.available) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = ledPreviewLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Switch(
                                checked = projection.active,
                                onCheckedChange = onLedPreviewChange,
                                modifier = Modifier.semantics { contentDescription = ledPreviewLabel },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                GlassPreviewCapsule {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = if (compact) 12.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        preview.groups.forEachIndexed { index, segments ->
                            val target = if (index == 0) EditTarget.LEFT else EditTarget.RIGHT
                            StickTarget(
                                label = previewModuleLabel(preview, index),
                                segments = segments,
                                selected = selectedTarget == target,
                                power = power,
                                enabled = enabled && perZone,
                                diameter = ringSize,
                                showLabel = !compact,
                                projection = projection,
                                onClick = { onTargetChange(target) },
                            )
                        }
                    }
                }
                if (showBoth) {
                    Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
                    Surface(
                        onClick = { onTargetChange(EditTarget.BOTH) },
                        enabled = enabled,
                        modifier = Modifier.heightIn(min = if (compact) 36.dp else 48.dp),
                        color =
                            if (selectedTarget == EditTarget.BOTH) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                previewStyle.capsuleMiddle
                            },
                        contentColor =
                            if (selectedTarget == EditTarget.BOTH) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        shape = RoundedCornerShape(999.dp),
                        border =
                            BorderStroke(
                                width = if (selectedTarget == EditTarget.BOTH) 2.dp else 1.dp,
                                color =
                                    if (selectedTarget == EditTarget.BOTH) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        previewStyle.capsuleOutline
                                    },
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.target_both),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = if (compact) 6.dp else 10.dp),
                            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StickTarget(
    label: String,
    segments: List<DeviceRingSegment>,
    selected: Boolean,
    power: Boolean,
    enabled: Boolean,
    diameter: Dp,
    showLabel: Boolean,
    projection: LedColorProjection,
    onClick: () -> Unit,
) {
    val glowAlpha by
        animateFloatAsState(
            targetValue = projection.glowAlpha,
            animationSpec = tween(durationMillis = 180),
            label = "LED preview glow",
        )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier =
                Modifier
                    .size(diameter)
                    .semantics {
                        contentDescription = label
                        this.selected = selected
                    },
            color = Color.Transparent,
            shape = CircleShape,
        ) {
            GlassLedRing(
                segments =
                    segments.map { segment ->
                        LedPreviewRingSegment(
                            color = projection.display(segment.color).toComposeColor(),
                            startAngle = segment.startAngle,
                            sweepAngle = segment.sweepAngle,
                        )
                    },
                power = power,
                glowAlpha = glowAlpha,
                selected = selected,
                selectedOutline = MaterialTheme.colorScheme.primary,
            )
        }
        if (showLabel) {
            Text(
                text = label,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

internal fun RgbColor.toComposeColor(): Color = Color(red, green, blue)
