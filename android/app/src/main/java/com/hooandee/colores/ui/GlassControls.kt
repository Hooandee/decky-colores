package com.hooandee.colores.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun glassSegmentedColors(): SegmentedButtonColors {
    val scheme = MaterialTheme.colorScheme
    val light = LocalPrismaticStyle.current.light
    return SegmentedButtonDefaults.colors(
        activeContainerColor = scheme.primary.copy(alpha = if (light) 0.16f else 0.26f),
        activeContentColor = scheme.onSurface,
        activeBorderColor = scheme.primary.copy(alpha = 0.62f),
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = scheme.onSurfaceVariant,
        inactiveBorderColor = scheme.outline,
        disabledActiveContainerColor = scheme.onSurface.copy(alpha = 0.08f),
        disabledActiveContentColor = scheme.onSurface.copy(alpha = 0.38f),
        disabledActiveBorderColor = scheme.outline.copy(alpha = 0.5f),
        disabledInactiveContainerColor = Color.Transparent,
        disabledInactiveContentColor = scheme.onSurface.copy(alpha = 0.38f),
        disabledInactiveBorderColor = scheme.outline.copy(alpha = 0.5f),
    )
}

@Composable
internal fun glassSwitchColors(): SwitchColors {
    val scheme = MaterialTheme.colorScheme
    val light = LocalPrismaticStyle.current.light
    return SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = LocalPrismaticStyle.current.accentFill,
        checkedBorderColor = Color.Transparent,
        checkedIconColor = LocalPrismaticStyle.current.accentFill,
        uncheckedThumbColor = if (light) Color.White else scheme.onSurfaceVariant,
        uncheckedTrackColor = if (light) Color(0x1F1B2A3A) else Color.White.copy(alpha = 0.1f),
        uncheckedBorderColor = scheme.outline,
        uncheckedIconColor = if (light) scheme.onSurfaceVariant else scheme.surface,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        thumb = { GlassSliderThumb(interactionSource, enabled) },
        track = { GlassSliderTrack(it, enabled) },
    )
}

@Composable
private fun GlassSliderThumb(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    val active = pressed || dragged
    val primary = LocalPrismaticStyle.current.accentFill
    val light = LocalPrismaticStyle.current.light
    val ring = if (enabled) primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(if (active) 26.dp else 22.dp)
                .shadow(
                    elevation = if (enabled) 8.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = primary,
                    spotColor = primary,
                ),
        ) {
            val radius = size.minDimension / 2f
            drawCircle(Color.White.copy(alpha = if (enabled) 1f else 0.6f), radius)
            drawCircle(
                brush = Brush.verticalGradient(listOf(Color.White, if (light) Color(0xFFE6EBF0) else Color(0xFFD9DEE8))),
                radius = radius * 0.92f,
            )
            drawCircle(ring, radius * 0.34f)
            if (focused) {
                drawCircle(ring, radius - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
        }
        if (focused) {
            Canvas(Modifier.size(34.dp)) {
                drawCircle(ring.copy(alpha = 0.28f), size.minDimension / 2f)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassSliderTrack(
    state: SliderState,
    enabled: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val style = LocalPrismaticStyle.current
    val span = state.valueRange.endInclusive - state.valueRange.start
    val fraction = if (span > 0f) ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f) else 0f
    val fill = if (enabled) style.accentFill else scheme.onSurface.copy(alpha = 0.3f)
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val height = size.height
        val radius = CornerRadius(height / 2f)
        drawRoundRect(
            color = if (style.light) Color(0x1A1B2A3A) else Color.White.copy(alpha = 0.08f),
            cornerRadius = radius,
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (style.light) 0.06f else 0.24f), Color.Transparent)),
            size = Size(size.width, height * 0.6f),
            cornerRadius = radius,
        )
        val filled = (size.width * fraction).coerceAtLeast(height)
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(fill.copy(alpha = 0.55f), fill), startX = 0f, endX = filled),
            size = Size(filled, height),
            cornerRadius = radius,
        )
        drawRoundRect(
            color = Color.White.copy(alpha = if (enabled) 0.3f else 0.1f),
            topLeft = Offset(height / 2f, 1.dp.toPx()),
            size = Size((filled - height).coerceAtLeast(0f), 1.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}
