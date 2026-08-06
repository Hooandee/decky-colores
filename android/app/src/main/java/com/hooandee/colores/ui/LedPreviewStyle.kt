package com.hooandee.colores.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
internal data class LedPreviewStyle(
    val sceneBackground: Color,
    val capsuleHighlight: Color,
    val capsuleMiddle: Color,
    val capsuleBase: Color,
    val capsuleOutline: Color,
    val discHighlight: Color,
    val discMiddle: Color,
    val discBase: Color,
    val discOutline: Color,
    val ringTrack: Color,
    val hubHighlight: Color,
    val hubMiddle: Color,
    val hubBase: Color,
    val hubOutline: Color,
    val ringGlowAlpha: Float,
    val disabledPowerAlpha: Float,
)

internal val SmokyGlassLedPreviewStyle =
    LedPreviewStyle(
        sceneBackground = Color(0xFF101116),
        capsuleHighlight = Color.White.copy(alpha = 0.075f),
        capsuleMiddle = Color(0xB01B1D25),
        capsuleBase = Color(0x99101218),
        capsuleOutline = Color.White.copy(alpha = 0.11f),
        discHighlight = Color.White.copy(alpha = 0.10f),
        discMiddle = Color(0x991C1F28),
        discBase = Color(0xB30A0B10),
        discOutline = Color.White.copy(alpha = 0.12f),
        ringTrack = Color.White.copy(alpha = 0.035f),
        hubHighlight = Color.White.copy(alpha = 0.14f),
        hubMiddle = Color(0xCC242732),
        hubBase = Color(0xF20A0B0F),
        hubOutline = Color.White.copy(alpha = 0.10f),
        ringGlowAlpha = 0.30f,
        disabledPowerAlpha = 0.18f,
    )

internal val LocalLedPreviewStyle = staticCompositionLocalOf { SmokyGlassLedPreviewStyle }

internal data class LedPreviewRingSegment(
    val color: Color,
    val startAngle: Float,
    val sweepAngle: Float,
)

@Composable
internal fun GlassPreviewCapsule(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = LocalLedPreviewStyle.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier =
            modifier
                .background(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    style.capsuleHighlight,
                                    style.capsuleMiddle,
                                    style.capsuleBase,
                                ),
                        ),
                    shape = shape,
                ).border(1.dp, style.capsuleOutline, shape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
internal fun GlassLedRing(
    segments: List<LedPreviewRingSegment>,
    power: Boolean,
    modifier: Modifier = Modifier,
    glowAlpha: Float = LocalLedPreviewStyle.current.ringGlowAlpha,
    selected: Boolean = false,
    selectedOutline: Color = Color.Unspecified,
) {
    val style = LocalLedPreviewStyle.current
    Canvas(modifier.fillMaxSize()) {
        val outerRadius = size.minDimension / 2f - 1.dp.toPx()
        val strokeWidth = size.minDimension * 0.135f
        val glowWidth = strokeWidth * 2f
        val radius = outerRadius - glowWidth / 2f - 3.dp.toPx()
        val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        val powerAlpha = if (power) 1f else style.disabledPowerAlpha
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(style.discHighlight, style.discMiddle, style.discBase),
                    center = Offset(size.width * 0.32f, size.height * 0.24f),
                    radius = outerRadius * 1.45f,
                ),
            radius = outerRadius,
        )
        drawCircle(
            color = if (selected && selectedOutline != Color.Unspecified) selectedOutline else style.discOutline,
            radius = outerRadius,
            style = Stroke(width = if (selected) 1.5.dp.toPx() else 1.dp.toPx()),
        )
        drawCircle(
            color = style.ringTrack,
            radius = radius,
            style = Stroke(width = strokeWidth),
        )
        segments.forEach { segment ->
            drawArc(
                color = segment.color,
                startAngle = segment.startAngle,
                sweepAngle = segment.sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                alpha = glowAlpha * powerAlpha,
                style = Stroke(width = glowWidth, cap = StrokeCap.Butt),
            )
            drawArc(
                color = segment.color,
                startAngle = segment.startAngle,
                sweepAngle = segment.sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                alpha = powerAlpha,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
        }
        val hubRadius = radius - strokeWidth * 0.72f
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(style.hubHighlight, style.hubMiddle, style.hubBase),
                    center = Offset(size.width * 0.38f, size.height * 0.32f),
                    radius = hubRadius * 1.35f,
                ),
            radius = hubRadius,
        )
        drawCircle(
            color = style.hubOutline,
            radius = hubRadius,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}
