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

data class AtmosphereRoles(
    val backgroundStart: RgbColor,
    val backgroundMiddle: RgbColor,
    val backgroundEnd: RgbColor,
    val coolGlow: RgbColor,
    val warmGlow: RgbColor,
    val beam: RgbColor,
    val panelSurface: RgbColor,
    val panelSurfaceStrong: RgbColor,
    val panelOutline: RgbColor,
    val panelOutlineStrong: RgbColor,
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

fun atmosphereRoles(
    accent: RgbColor,
    dark: Boolean,
): AtmosphereRoles {
    val source = accent.sanitized().toHsvColor()
    val saturation = if (source.saturation < 0.08f) 0f else source.saturation.coerceIn(0.18f, 0.72f)
    val cool = HsvColor(source.hue + 22f, saturation * 0.82f, if (dark) 0.72f else 0.78f).toRgbColor()
    val warm = HsvColor(source.hue - 18f, saturation * 0.68f, if (dark) 0.58f else 0.86f).toRgbColor()
    val beam = HsvColor(source.hue, saturation * 0.72f, if (dark) 0.92f else 0.68f).toRgbColor()
    val darkNeutral = RgbColor(9, 10, 12)
    val lightNeutral = RgbColor(245, 247, 249)
    val neutral = if (dark) darkNeutral else lightNeutral
    val surfaceNeutral = if (dark) RgbColor(20, 22, 25) else RgbColor(249, 250, 251)
    return AtmosphereRoles(
        backgroundStart = blend(neutral, cool, if (dark) 0.08 else 0.075),
        backgroundMiddle = blend(neutral, accent.sanitized(), if (dark) 0.045 else 0.045),
        backgroundEnd = blend(if (dark) RgbColor(6, 7, 9) else RgbColor(241, 240, 244), warm, if (dark) 0.05 else 0.055),
        coolGlow = cool,
        warmGlow = warm,
        beam = beam,
        panelSurface = blend(surfaceNeutral, accent.sanitized(), if (dark) 0.07 else 0.035),
        panelSurfaceStrong = blend(surfaceNeutral, accent.sanitized(), if (dark) 0.11 else 0.055),
        panelOutline = blend(if (dark) RgbColor(202, 217, 225) else RgbColor(87, 105, 115), cool, 0.22),
        panelOutlineStrong = blend(if (dark) RgbColor(225, 233, 238) else RgbColor(70, 88, 99), beam, 0.28),
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
