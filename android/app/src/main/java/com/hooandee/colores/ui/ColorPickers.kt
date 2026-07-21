package com.hooandee.colores.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hooandee.colores.led.RgbColor
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RingColorPicker(
    color: RgbColor,
    enabled: Boolean,
    projection: LedColorProjection,
    contentDescription: String,
    onColorChange: (RgbColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hsv = color.toHsvColor()
    val saturation by rememberUpdatedState(hsv.saturation)
    val pulse by rememberInfiniteTransition(label = "ringPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700), RepeatMode.Reverse),
        label = "pulse",
    )
    Canvas(
        modifier =
            modifier
                .sizeIn(maxWidth = 236.dp, maxHeight = 236.dp)
                .aspectRatio(1f)
                .semantics { this.contentDescription = contentDescription }
                .focusable(enabled)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    fun update(position: Offset) {
                        val hue = ((atan2(position.y - size.height / 2f, position.x - size.width / 2f) * 180f / PI.toFloat()) + 360f) % 360f
                        onColorChange(HsvColor(hue, saturation, 1f).toRgbColor())
                    }
                    detectDragGestures(onDragStart = ::update, onDrag = { c, _ -> c.consume(); update(c.position) })
                },
    ) {
        val radius = size.minDimension / 2f
        val center = this.center
        val alpha = if (enabled) 1f else 0.4f

        val outlineRadius = radius - 2f
        drawCircle(
            color = Color.White.copy(alpha = if (enabled) 0.16f else 0.06f),
            radius = outlineRadius,
            center = center,
            style = Stroke(width = 1.5f),
        )

        val band = radius * 0.185f
        val bandOuter = outlineRadius - radius * 0.05f - 3f
        val ringRadius = bandOuter - band / 2f
        val segments = 120
        for (i in 0 until segments) {
            val angle = i * 360f / segments
            drawArc(
                color = projection.display(HsvColor(angle, 1f, 1f).toRgbColor()).toComposeColor().copy(alpha = alpha),
                startAngle = angle,
                sweepAngle = 360f / segments + 1.2f,
                useCenter = false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                size = Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(width = band),
            )
        }

        val orbColor = projection.display(color).toComposeColor()
        val orbRadius = radius * 0.24f
        val haloRadius = orbRadius * (1.5f + 0.55f * pulse)
        drawCircle(
            brush = Brush.radialGradient(listOf(orbColor.copy(alpha = 0.55f * (0.5f + 0.5f * pulse)), Color.Transparent), center, haloRadius),
            radius = haloRadius,
            center = center,
        )
        drawCircle(orbColor, orbRadius, center)
        drawCircle(Color.White.copy(alpha = 0.14f), orbRadius, center, style = Stroke(width = 1.5f))

        val handleAngle = hsv.hue * PI.toFloat() / 180f
        val handle = Offset(center.x + cos(handleAngle) * ringRadius, center.y + sin(handleAngle) * ringRadius)
        drawCircle(Color.Black.copy(alpha = 0.3f), band * 0.42f, handle.copy(y = handle.y + 2f))
        drawCircle(projection.display(HsvColor(hsv.hue, 1f, 1f).toRgbColor()).toComposeColor(), band * 0.38f, handle)
        drawCircle(Color.White, band * 0.38f, handle, style = Stroke(width = 5f))
    }
}
