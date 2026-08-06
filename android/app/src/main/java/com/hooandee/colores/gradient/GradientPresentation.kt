package com.hooandee.colores.gradient

import com.hooandee.colores.device.DeviceCapabilities

enum class GradientPresentation {
    SPATIAL,
    ANIMATED,
}

fun GradientPresentation.editorStopCount(zones: Int): Int =
    when (this) {
        GradientPresentation.SPATIAL -> zones.coerceAtLeast(2)
        GradientPresentation.ANIMATED -> 2
    }

fun DeviceCapabilities.gradientPresentation(deviceSupportsPerZone: Boolean): GradientPresentation? {
    if (!color || zones < 1) return null
    return if (perZone && zones >= 2 && deviceSupportsPerZone) {
        GradientPresentation.SPATIAL
    } else {
        GradientPresentation.ANIMATED
    }
}
