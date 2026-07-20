package com.hooandee.colores.ui

import android.graphics.Color.RGBToHSV
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.hooandee.colores.led.RgbColor
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun HsvColorWheel(
    color: RgbColor,
    enabled: Boolean,
    onColorChange: (RgbColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hsv = FloatArray(3).also { RGBToHSV(color.red, color.green, color.blue, it) }
    Canvas(
        modifier =
            modifier
                .aspectRatio(1f)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    fun update(position: Offset) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = minOf(size.width, size.height) / 2f
                        val x = position.x - center.x
                        val y = position.y - center.y
                        val saturation = (hypot(x, y) / radius).coerceIn(0f, 1f)
                        val hue = ((atan2(y, x) * 180f / PI.toFloat()) + 360f) % 360f
                        val picked = Color.hsv(hue, saturation, 1f)
                        onColorChange(
                            RgbColor(
                                (picked.red * 255).roundToInt(),
                                (picked.green * 255).roundToInt(),
                                (picked.blue * 255).roundToInt(),
                            ),
                        )
                    }
                    detectDragGestures(
                        onDragStart = ::update,
                        onDrag = { change, _ ->
                            change.consume()
                            update(change.position)
                        },
                    )
                },
    ) {
        val radius = size.minDimension / 2f
        val center = this.center
        drawCircle(
            brush =
                Brush.sweepGradient(
                    listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red,
                    ),
                    center,
                ),
            radius = radius,
            center = center,
            alpha = if (enabled) 1f else 0.4f,
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius),
            radius = radius,
            center = center,
            alpha = if (enabled) 1f else 0.4f,
        )
        val angle = hsv[0] * PI.toFloat() / 180f
        val thumb =
            Offset(
                x = center.x + cos(angle) * radius * hsv[1],
                y = center.y + sin(angle) * radius * hsv[1],
            )
        drawCircle(Color.Black.copy(alpha = 0.65f), 12f, thumb)
        drawCircle(Color.White, 9f, thumb, style = Stroke(width = 4f))
    }
}
