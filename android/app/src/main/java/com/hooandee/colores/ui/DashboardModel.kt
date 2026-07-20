package com.hooandee.colores.ui

import com.hooandee.colores.led.LedState
import com.hooandee.colores.led.RgbColor
import kotlin.math.abs
import kotlin.math.roundToInt

enum class EditTarget {
    BOTH,
    LEFT,
    RIGHT,
}

val LedState.hasMixedColors: Boolean
    get() = zoneColors.distinct().size > 1

fun LedState.colorForEditing(target: EditTarget): RgbColor {
    val first = zoneColors.firstOrNull() ?: RgbColor(93, 81, 255)
    return when (target) {
        EditTarget.BOTH, EditTarget.LEFT -> first
        EditTarget.RIGHT -> zoneColors.getOrElse(1) { first }
    }
}

fun LedState.withTargetColor(
    target: EditTarget,
    color: RgbColor,
): LedState {
    val zones = zoneColors.ifEmpty { listOf(RgbColor(93, 81, 255), RgbColor(93, 81, 255)) }
    val changed =
        when (target) {
            EditTarget.BOTH -> List(zones.size) { color }
            EditTarget.LEFT -> zones.mapIndexed { index, current -> if (index == 0) color else current }
            EditTarget.RIGHT -> zones.mapIndexed { index, current -> if (index == 1) color else current }
        }
    return copy(zoneColors = changed)
}

fun LedState.withTargetSaturation(
    target: EditTarget,
    saturation: Float,
): LedState {
    val color = colorForEditing(target).toHsvColor().copy(saturation = saturation.coerceIn(0f, 1f)).toRgbColor()
    return withTargetColor(target, color)
}

data class HsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

enum class ColorDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN,
}

fun HsvColor.adjustForDirection(direction: ColorDirection): HsvColor =
    when (direction) {
        ColorDirection.LEFT -> copy(hue = (hue - 3f + 360f) % 360f)
        ColorDirection.RIGHT -> copy(hue = (hue + 3f) % 360f)
        ColorDirection.UP -> copy(saturation = (saturation + 0.03f).coerceIn(0f, 1f))
        ColorDirection.DOWN -> copy(saturation = (saturation - 0.03f).coerceIn(0f, 1f))
    }

fun RgbColor.toHsvColor(): HsvColor {
    val normalizedRed = red.coerceIn(0, 255) / 255f
    val normalizedGreen = green.coerceIn(0, 255) / 255f
    val normalizedBlue = blue.coerceIn(0, 255) / 255f
    val max = maxOf(normalizedRed, normalizedGreen, normalizedBlue)
    val min = minOf(normalizedRed, normalizedGreen, normalizedBlue)
    val delta = max - min
    val hue =
        when {
            delta == 0f -> 0f
            max == normalizedRed -> 60f * (((normalizedGreen - normalizedBlue) / delta) % 6f)
            max == normalizedGreen -> 60f * (((normalizedBlue - normalizedRed) / delta) + 2f)
            else -> 60f * (((normalizedRed - normalizedGreen) / delta) + 4f)
        }.let { (it + 360f) % 360f }
    return HsvColor(
        hue = hue,
        saturation = if (max == 0f) 0f else delta / max,
        value = max,
    )
}

fun HsvColor.toRgbColor(): RgbColor {
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    val normalizedSaturation = saturation.coerceIn(0f, 1f)
    val normalizedValue = value.coerceIn(0f, 1f)
    val chroma = normalizedValue * normalizedSaturation
    val x = chroma * (1f - abs((normalizedHue / 60f) % 2f - 1f))
    val offset = normalizedValue - chroma
    val channels =
        when {
            normalizedHue < 60f -> Triple(chroma, x, 0f)
            normalizedHue < 120f -> Triple(x, chroma, 0f)
            normalizedHue < 180f -> Triple(0f, chroma, x)
            normalizedHue < 240f -> Triple(0f, x, chroma)
            normalizedHue < 300f -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
    return RgbColor(
        red = ((channels.first + offset) * 255f).roundToInt().coerceIn(0, 255),
        green = ((channels.second + offset) * 255f).roundToInt().coerceIn(0, 255),
        blue = ((channels.third + offset) * 255f).roundToInt().coerceIn(0, 255),
    )
}
