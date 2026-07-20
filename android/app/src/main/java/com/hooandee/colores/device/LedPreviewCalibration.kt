package com.hooandee.colores.device

data class LedPreviewCalibration(
    val saturationScale: Float,
    val whiteMix: Float,
    val redGain: Float,
    val greenGain: Float,
    val blueGain: Float,
    val valueGamma: Float,
    val glowAlpha: Float,
    val hueMap: List<LedPreviewHuePoint> = emptyList(),
) {
    val normalizedHueMap: List<LedPreviewHuePoint> =
        hueMap
            .map {
                it.copy(
                    input = it.input.normalizedHue(),
                    output = it.output.normalizedHue(),
                )
            }
            .distinctBy { it.input }
            .sortedBy { it.input }
}

data class LedPreviewHuePoint(
    val input: Float,
    val output: Float,
)

private fun Float.normalizedHue(): Float = ((this % 360f) + 360f) % 360f
