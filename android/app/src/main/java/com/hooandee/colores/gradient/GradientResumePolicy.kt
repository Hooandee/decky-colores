package com.hooandee.colores.gradient

import com.hooandee.colores.led.RgbColor

internal object GradientResumePolicy {
    fun shouldReapply(
        mode: LightingMode,
        stops: List<RgbColor>,
        gradientAvailable: Boolean,
        canWrite: Boolean,
    ): Boolean =
        mode == LightingMode.GRADIENT &&
            stops.isNotEmpty() &&
            gradientAvailable &&
            canWrite
}
