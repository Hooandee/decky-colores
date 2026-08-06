package com.hooandee.colores.ui

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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hooandee.colores.R
import com.hooandee.colores.device.LedGridCell
import com.hooandee.colores.engine.AudioScale
import com.hooandee.colores.led.RgbColor

@Composable
internal fun AudioDeviceScene(
    frame: List<RgbColor>,
    layout: List<LedGridCell>?,
    level: Double,
    capturing: Boolean,
    scale: AudioScale,
    power: Boolean,
    projection: LedColorProjection,
    modifier: Modifier = Modifier,
) {
    val preview = devicePreviewGroups(frame, layout)
    val previewStyle = LocalLedPreviewStyle.current
    Surface(
        modifier = modifier,
        color = previewStyle.sceneBackground,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(32.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 340.dp
            val ringSize = if (compact) 94.dp else 118.dp
            val audioBarsHeight = if (compact) 72.dp else 88.dp
            Column(
                modifier = Modifier.fillMaxSize().padding(if (compact) 14.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text =
                        stringResource(
                            if (preview.representsSticks) R.string.preview_title else R.string.preview_lights_title,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassPreviewCapsule(
                        modifier = Modifier.wrapContentWidth().height(ringSize + 34.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            preview.groups.forEachIndexed { index, segments ->
                                AudioLightModule(
                                    label = previewModuleLabel(preview, index),
                                    segments = segments,
                                    diameter = ringSize,
                                    power = power,
                                    projection = projection,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
                AudioLevelBars(
                    level = level,
                    active = capturing,
                    scale = scale,
                    height = audioBarsHeight,
                )
            }
        }
    }
}

@Composable
private fun AudioLightModule(
    label: String,
    segments: List<DeviceRingSegment>,
    diameter: Dp,
    power: Boolean,
    projection: LedColorProjection,
) {
    Box(
        modifier = Modifier.size(diameter).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
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
        )
    }
}

@Composable
internal fun previewModuleLabel(
    preview: DevicePreviewGroups,
    index: Int,
): String =
    if (preview.representsSticks) {
        stringResource(if (index == 0) R.string.stick_left else R.string.stick_right)
    } else if (preview.groups.size == 1) {
        stringResource(R.string.light_single)
    } else {
        stringResource(R.string.light_number, index + 1)
    }
