package com.hooandee.colores.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hooandee.colores.R
import com.hooandee.colores.device.LedGridCell
import com.hooandee.colores.led.RgbColor

@Composable
fun DeviceScene(
    frame: List<RgbColor>,
    layout: List<LedGridCell>?,
    singleOutputMirrorsBothSticks: Boolean,
    selectedTarget: EditTarget,
    power: Boolean,
    enabled: Boolean,
    perZone: Boolean,
    projection: LedColorProjection,
    onTargetChange: (EditTarget) -> Unit,
    modifier: Modifier = Modifier,
    showBoth: Boolean = true,
    wrapContent: Boolean = false,
) {
    val previewStyle = LocalLedPreviewStyle.current
    val lightPreview = previewStyle.sceneBackground.luminance() > 0.5f
    val preview = devicePreviewGroups(frame, layout, singleOutputMirrorsBothSticks)
    Surface(
        modifier = modifier.prismaticPanel(RoundedCornerShape(32.dp), strong = true),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(32.dp),
    ) {
        BoxWithConstraints(modifier = if (wrapContent) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
            val compact = !wrapContent && maxHeight < 320.dp
            val scenePadding = if (compact) 14.dp else 22.dp
            val capsuleHorizontalPadding = 18.dp
            val ringSpacing = 18.dp
            val compactChrome = if (showBoth) 172.dp else 118.dp
            val preferredRingSize = if (compact) (maxHeight - compactChrome).coerceIn(40.dp, 112.dp) else 112.dp
            val ringSize =
                previewRingDiameter(
                    availableWidth = maxWidth - scenePadding * 2 - capsuleHorizontalPadding * 2,
                    groupCount = preview.groups.size,
                    spacing = ringSpacing,
                    preferredDiameter = preferredRingSize,
                )
            Column(
                modifier = (if (wrapContent) Modifier.fillMaxWidth() else Modifier.fillMaxSize()).padding(scenePadding),
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
                }
                if (wrapContent) Spacer(Modifier.height(16.dp)) else Spacer(Modifier.weight(1f))
                GlassPreviewCapsule {
                    Row(
                        modifier = Modifier.padding(horizontal = capsuleHorizontalPadding, vertical = if (compact) 12.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(ringSpacing),
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
                                projection = projection,
                                onClick = { onTargetChange(target) },
                            )
                        }
                    }
                }
                if (preview.groups.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = capsuleHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(ringSpacing),
                    ) {
                        preview.groups.indices.forEach { index ->
                            val target = if (index == 0) EditTarget.LEFT else EditTarget.RIGHT
                            val selected = selectedTarget == target
                            Text(
                                text = previewShortLabel(preview, index),
                                modifier = Modifier.width(ringSize).clearAndSetSemantics {},
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (showBoth) {
                    Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
                    Surface(
                        onClick = { onTargetChange(EditTarget.BOTH) },
                        enabled = enabled,
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .fillMaxWidth(if (compact) 0.64f else 0.72f)
                                .height(if (compact) 44.dp else 48.dp)
                                .semantics {
                                    role = Role.RadioButton
                                    selected = selectedTarget == EditTarget.BOTH
                                },
                        color =
                            if (selectedTarget == EditTarget.BOTH) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else if (lightPreview) {
                                MaterialTheme.colorScheme.surfaceContainer
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
                                    } else if (lightPreview) {
                                        MaterialTheme.colorScheme.outlineVariant
                                    } else {
                                        previewStyle.capsuleOutline
                                    },
                            ),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.target_both),
                                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (!wrapContent) Spacer(Modifier.weight(1f))
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
    projection: LedColorProjection,
    onClick: () -> Unit,
) {
    val glowAlpha by
        animateFloatAsState(
            targetValue = projection.glowAlpha,
            animationSpec = tween(durationMillis = 180),
            label = "LED preview glow",
        )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .size(diameter)
                .semantics {
                    contentDescription = label
                    this.selected = selected
                    role = Role.RadioButton
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
}

@Composable
private fun previewShortLabel(
    preview: DevicePreviewGroups,
    index: Int,
): String =
    if (preview.representsSticks) {
        stringResource(if (index == 0) R.string.target_left else R.string.target_right)
    } else {
        previewModuleLabel(preview, index)
    }

internal fun RgbColor.toComposeColor(): Color = Color(red, green, blue)
