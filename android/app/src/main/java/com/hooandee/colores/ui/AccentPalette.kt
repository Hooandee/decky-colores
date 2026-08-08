package com.hooandee.colores.ui

import com.hooandee.colores.led.RgbColor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class AccentRoles(
    val primary: RgbColor,
    val onPrimary: RgbColor,
    val primaryContainer: RgbColor,
    val onPrimaryContainer: RgbColor,
)

fun accentRoles(
    accent: RgbColor,
    dark: Boolean,
): AccentRoles {
    val background = if (dark) RgbColor(18, 19, 25) else RgbColor(255, 255, 255)
    val primary = visiblePrimary(accent.sanitized(), background, dark)
    val primaryContainer = blend(background, primary, if (dark) 0.28 else 0.18)
    return AccentRoles(
        primary = primary,
        onPrimary = readableForeground(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = readableForeground(primaryContainer),
    )
}

private fun visiblePrimary(
    source: RgbColor,
    surface: RgbColor,
    dark: Boolean,
): RgbColor {
    var hsv = source.toHsvColor()
    repeat(40) {
        val candidate = hsv.toRgbColor()
        if (contrastRatio(candidate, surface) >= 3.0) return candidate
        hsv =
            if (dark) {
                if (hsv.value < 1f) hsv.copy(value = (hsv.value + 0.05f).coerceAtMost(1f)) else hsv.copy(saturation = (hsv.saturation - 0.05f).coerceAtLeast(0f))
            } else {
                hsv.copy(value = (hsv.value - 0.05f).coerceAtLeast(0f))
            }
    }
    return hsv.toRgbColor()
}

fun contrastRatio(
    first: RgbColor,
    second: RgbColor,
): Double {
    val lighter = max(first.relativeLuminance(), second.relativeLuminance())
    val darker = min(first.relativeLuminance(), second.relativeLuminance())
    return (lighter + 0.05) / (darker + 0.05)
}

private fun readableForeground(background: RgbColor): RgbColor {
    val black = RgbColor(0, 0, 0)
    val white = RgbColor(255, 255, 255)
    return if (contrastRatio(background, black) >= contrastRatio(background, white)) black else white
}

private fun blend(
    background: RgbColor,
    foreground: RgbColor,
    amount: Double,
) =
    RgbColor(
        (background.red + (foreground.red - background.red) * amount).toInt(),
        (background.green + (foreground.green - background.green) * amount).toInt(),
        (background.blue + (foreground.blue - background.blue) * amount).toInt(),
    )

private fun RgbColor.relativeLuminance(): Double =
    0.2126 * red.linearChannel() + 0.7152 * green.linearChannel() + 0.0722 * blue.linearChannel()

private fun Int.linearChannel(): Double {
    val channel = coerceIn(0, 255) / 255.0
    return if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
}

private fun RgbColor.sanitized() =
    RgbColor(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))
