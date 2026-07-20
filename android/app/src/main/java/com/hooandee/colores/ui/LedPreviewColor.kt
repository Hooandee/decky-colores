package com.hooandee.colores.ui

import com.hooandee.colores.device.LedPreviewCalibration
import com.hooandee.colores.device.LedPreviewHuePoint
import com.hooandee.colores.led.RgbColor
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

fun RgbColor.forLedPreview(
    profile: LedPreviewCalibration?,
    enabled: Boolean,
): RgbColor = if (enabled && profile != null) applyPreviewCalibration(profile) else this

fun colorWheelDisplayAt(
    normalizedX: Float,
    normalizedY: Float,
    profile: LedPreviewCalibration?,
    enabled: Boolean,
): RgbColor? {
    val saturation = hypot(normalizedX, normalizedY)
    if (saturation > 1f) return null
    val hue = ((atan2(normalizedY, normalizedX) * 180f / PI.toFloat()) + 360f) % 360f
    return HsvColor(hue, saturation, 1f).toRgbColor().forLedPreview(profile, enabled)
}

fun calibratedColorWheelPixels(
    size: Int,
    profile: LedPreviewCalibration,
): IntArray {
    if (size <= 0) return IntArray(0)
    val center = (size - 1) / 2f
    val radius = size / 2f
    return IntArray(size * size) { index ->
        val x = index % size
        val y = index / size
        colorWheelDisplayAt(
            normalizedX = (x - center) / radius,
            normalizedY = (y - center) / radius,
            profile = profile,
            enabled = true,
        )?.let { color ->
            0xFF000000.toInt() or (color.red shl 16) or (color.green shl 8) or color.blue
        } ?: 0
    }
}

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
            hue = mapHue(hsv.hue, profile.normalizedHueMap),
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
    if (points.size < 2) return hue
    val normalized = hue.normalizedHue()
    val lowerIndex = points.indexOfLast { it.input <= normalized }
    val lower = if (lowerIndex >= 0) points[lowerIndex] else points.last()
    val upper = points[(lowerIndex + 1).mod(points.size)]
    val lowerInput = if (lowerIndex >= 0) lower.input else lower.input - 360f
    val upperInput = if (upper.input > lowerInput) upper.input else upper.input + 360f
    val adjustedHue = if (normalized >= lowerInput) normalized else normalized + 360f
    val progress = ((adjustedHue - lowerInput) / (upperInput - lowerInput)).coerceIn(0f, 1f)
    val outputDelta = ((upper.output - lower.output + 540f) % 360f) - 180f
    return (lower.output + outputDelta * progress).normalizedHue()
}

private fun Float.normalizedHue(): Float = ((this % 360f) + 360f) % 360f
