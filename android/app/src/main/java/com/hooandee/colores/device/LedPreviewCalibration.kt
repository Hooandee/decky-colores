package com.hooandee.colores.device

data class LedPreviewCalibration(
    val saturationScale: Float,
    val whiteMix: Float,
    val redGain: Float,
    val greenGain: Float,
    val blueGain: Float,
    val valueGamma: Float,
    val glowAlpha: Float,
)
