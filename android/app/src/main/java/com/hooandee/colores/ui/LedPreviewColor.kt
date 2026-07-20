package com.hooandee.colores.ui

import com.hooandee.colores.device.LedPreviewCalibration
import com.hooandee.colores.device.LedPreviewHuePoint
import com.hooandee.colores.led.RgbColor
import kotlin.math.pow
import kotlin.math.roundToInt

fun RgbColor.applyPreviewCalibration(profile: LedPreviewCalibration?): RgbColor {
    if (profile == null) return this
    val gained =
        RgbColor(
            red = (red * profile.redGain).roundToInt().coerceIn(0, 255),
            green = (green * profile.greenGain).roundToInt().coerceIn(0, 255),
            blue = (blue * profile.blueGain).roundToInt().coerceIn(0, 255),
        )
    val hsv = gained.toHsvColor()
    val corrected =
        hsv.copy(
            hue = mapHue(hsv.hue, profile.hueMap),
            saturation = (hsv.saturation * profile.saturationScale).coerceIn(0f, 1f),
            value = hsv.value.pow(profile.valueGamma).coerceIn(0f, 1f),
        ).toRgbColor()
    return RgbColor(
        red = corrected.red.mixWithWhite(profile.whiteMix),
        green = corrected.green.mixWithWhite(profile.whiteMix),
        blue = corrected.blue.mixWithWhite(profile.whiteMix),
    )
}

private fun Int.mixWithWhite(amount: Float): Int =
    (this + (255 - this) * amount.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)

private fun mapHue(
    hue: Float,
    points: List<LedPreviewHuePoint>,
): Float {
    val sorted =
        points
            .map { it.copy(input = it.input.normalizedHue(), output = it.output.normalizedHue()) }
            .distinctBy { it.input }
            .sortedBy { it.input }
    if (sorted.size < 2) return hue
    val normalized = hue.normalizedHue()
    val lowerIndex = sorted.indexOfLast { it.input <= normalized }
    val lower = if (lowerIndex >= 0) sorted[lowerIndex] else sorted.last()
    val upper = sorted[(lowerIndex + 1).mod(sorted.size)]
    val lowerInput = if (lowerIndex >= 0) lower.input else lower.input - 360f
    val upperInput = if (upper.input > lowerInput) upper.input else upper.input + 360f
    val adjustedHue = if (normalized >= lowerInput) normalized else normalized + 360f
    val progress = ((adjustedHue - lowerInput) / (upperInput - lowerInput)).coerceIn(0f, 1f)
    val outputDelta = ((upper.output - lower.output + 540f) % 360f) - 180f
    return (lower.output + outputDelta * progress).normalizedHue()
}

private fun Float.normalizedHue(): Float = ((this % 360f) + 360f) % 360f
