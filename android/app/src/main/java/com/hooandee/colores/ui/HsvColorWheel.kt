package com.hooandee.colores.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hooandee.colores.led.RgbColor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun HsvColorWheel(
    color: RgbColor,
    enabled: Boolean,
    projection: LedColorProjection,
    contentDescription: String,
    onColorChange: (RgbColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hsv = color.toHsvColor()
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var wheelPixelSize by remember { mutableStateOf(0) }
    val calibratedWheel by
        produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, wheelPixelSize, projection) {
            value = null
            if (projection.active && wheelPixelSize > 0) {
                val pixels = renderColorWheelPixels(wheelPixelSize, projection)
                value =
                    Bitmap.createBitmap(
                        pixels,
                        wheelPixelSize,
                        wheelPixelSize,
                        Bitmap.Config.ARGB_8888,
                    ).asImageBitmap()
            }
        }
    Canvas(
        modifier =
            modifier
                .sizeIn(maxWidth = 236.dp, maxHeight = 236.dp)
                .aspectRatio(1f)
                .shadow(elevation = 12.dp, shape = CircleShape, clip = false)
                .onSizeChanged { wheelPixelSize = minOf(it.width, it.height) }
                .semantics { this.contentDescription = contentDescription }
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused) editing = false
                }
                .onPreviewKeyEvent { event ->
                    if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.ButtonA -> {
                            editing = !editing
                            return@onPreviewKeyEvent true
                        }
                        Key.Back, Key.ButtonB ->
                            if (editing) {
                                editing = false
                                return@onPreviewKeyEvent true
                            }
                    }
                    if (!editing) return@onPreviewKeyEvent false
                    val direction =
                        when (event.key) {
                            Key.DirectionLeft -> ColorDirection.LEFT
                            Key.DirectionRight -> ColorDirection.RIGHT
                            Key.DirectionUp -> ColorDirection.UP
                            Key.DirectionDown -> ColorDirection.DOWN
                            else -> return@onPreviewKeyEvent false
                        }
                    onColorChange(hsv.adjustForDirection(direction).toRgbColor())
                    true
                }
                .focusable(enabled)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    fun update(position: Offset) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = minOf(size.width, size.height) / 2f
                        val x = position.x - center.x
                        val y = position.y - center.y
                        val saturation = (hypot(x, y) / radius).coerceIn(0f, 1f)
                        val hue = ((atan2(y, x) * 180f / PI.toFloat()) + 360f) % 360f
                        onColorChange(HsvColor(hue, saturation, 1f).toRgbColor())
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
        val wheel = calibratedWheel
        if (wheel != null) {
            drawImage(
                image = wheel,
                topLeft = Offset(center.x - wheel.width / 2f, center.y - wheel.height / 2f),
                alpha = if (enabled) 1f else 0.4f,
            )
        } else {
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
        }
        // Subtle rim so the wheel reads as a raised disc rather than a flat fill.
        drawCircle(Color.White.copy(alpha = 0.16f), radius - 1f, center, style = Stroke(width = 2f))
        drawCircle(Color.Black.copy(alpha = 0.28f), radius, center, style = Stroke(width = 3f))

        val angle = hsv.hue * PI.toFloat() / 180f
        val thumb =
            Offset(
                x = center.x + cos(angle) * radius * hsv.saturation,
                y = center.y + sin(angle) * radius * hsv.saturation,
            )
        val thumbColor = projection.display(color).toComposeColor()
        drawCircle(Color.Black.copy(alpha = 0.30f), 22f, thumb.copy(y = thumb.y + 3f))
        drawCircle(thumbColor, 18f, thumb)
        drawCircle(Color.White, 18f, thumb, style = Stroke(width = 6f))
        drawCircle(Color.Black.copy(alpha = 0.22f), 21f, thumb, style = Stroke(width = 2f))
        if (focused) {
            drawCircle(
                color = if (editing) Color(0xFF8D83FF) else Color.White.copy(alpha = 0.58f),
                radius = radius + 8f,
                center = center,
                style = Stroke(width = if (editing) 6f else 3f),
            )
        }
    }
}

internal suspend fun renderColorWheelPixels(
    size: Int,
    projection: LedColorProjection,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): IntArray = withContext(dispatcher) { calibratedColorWheelPixels(size, projection) }
