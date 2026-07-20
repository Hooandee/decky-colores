package com.hooandee.colores.ui

import com.hooandee.colores.device.LedPreviewCalibration
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
