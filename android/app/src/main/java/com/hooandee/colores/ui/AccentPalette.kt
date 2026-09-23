package com.hooandee.colores.ui

import com.hooandee.colores.led.RgbColor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class AccentRoles(
    val primary: RgbColor,
    val onPrimary: RgbColor,
    val fill: RgbColor,
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

internal val DarkGlassSurface = RgbColor(44, 46, 56)
internal val LightGlassSurface = RgbColor(228, 230, 236)

fun accentRoles(
    accent: RgbColor,
    dark: Boolean,
): AccentRoles {
    val source = accent.sanitized()
    val surface = if (dark) DarkGlassSurface else LightGlassSurface
    val primary = visiblePrimary(source, surface, dark)
    val primaryContainer = blend(if (dark) RgbColor(18, 19, 25) else RgbColor(255, 255, 255), primary, if (dark) 0.28 else 0.18)
    return AccentRoles(
        primary = primary,
        onPrimary = tonalForeground(primary),
        fill = accentFill(source, dark),
        primaryContainer = primaryContainer,
        onPrimaryContainer = tonalForeground(primaryContainer),
    )
}

fun atmosphereRoles(
    accent: RgbColor,
    dark: Boolean,
): AtmosphereRoles {
    val source = accent.sanitized().toHsvColor()
    val saturation = if (source.saturation < 0.08f) 0f else source.saturation.coerceIn(0.18f, 0.72f)
    val cool = HsvColor(source.hue + 38f, saturation * 0.86f, if (dark) 0.72f else 0.78f).toRgbColor()
    val warm = HsvColor(source.hue - 34f, saturation * 0.74f, if (dark) 0.58f else 0.86f).toRgbColor()
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
    repeat(60) {
        val candidate = hsv.toRgbColor()
        if (contrastRatio(candidate, surface) >= 4.5) return candidate
        hsv =
            if (dark) {
                if (hsv.value < 1f) hsv.copy(value = (hsv.value + 0.04f).coerceAtMost(1f)) else hsv.copy(saturation = (hsv.saturation - 0.04f).coerceAtLeast(0f))
            } else {
                hsv.copy(value = (hsv.value - 0.04f).coerceAtLeast(0f))
            }
    }
    return hsv.toRgbColor()
}

private fun accentFill(
    source: RgbColor,
    dark: Boolean,
): RgbColor {
    val band = if (dark) 0.19..0.3 else 0.06..0.22
    val hsv = source.toHsvColor().let { if (it.saturation < 0.08f) it.copy(saturation = 0f) else it }
    for (step in 100 downTo 0) {
        val candidate = hsv.copy(value = step / 100f).toRgbColor()
        val luminance = candidate.relativeLuminance()
        if (luminance <= band.endInclusive) {
            if (luminance >= band.start) return candidate
            break
        }
    }
    for (step in (hsv.saturation * 100).toInt() downTo 0) {
        val candidate = hsv.copy(saturation = step / 100f, value = 1f).toRgbColor()
        if (candidate.relativeLuminance() >= band.start) return candidate
    }
    return RgbColor(128, 128, 128)
}

private fun tonalForeground(background: RgbColor): RgbColor {
    val readable = readableForeground(background)
    if (readable == RgbColor(255, 255, 255)) return readable
    val hsv = background.toHsvColor()
    val ink = HsvColor(hsv.hue, hsv.saturation.coerceAtMost(0.7f), 0.16f).toRgbColor()
    return if (contrastRatio(background, ink) >= 4.5) ink else readable
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
