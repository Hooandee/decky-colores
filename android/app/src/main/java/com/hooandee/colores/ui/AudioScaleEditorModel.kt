package com.hooandee.colores.ui

import com.hooandee.colores.engine.AudioScale
import com.hooandee.colores.led.RgbColor

enum class AudioScaleStop {
    LOW,
    MEDIUM,
    PEAK,
}

data class AudioScaleEditorModel(
    val scale: AudioScale,
    val selected: AudioScaleStop = AudioScaleStop.LOW,
) {
    val selectedColor: RgbColor
        get() =
            when (selected) {
                AudioScaleStop.LOW -> scale.lowColor
                AudioScaleStop.MEDIUM -> scale.mediumColor
                AudioScaleStop.PEAK -> scale.peakColor
            }

    val threshold: Int?
        get() =
            when (selected) {
                AudioScaleStop.LOW -> null
                AudioScaleStop.MEDIUM -> scale.mediumAt
                AudioScaleStop.PEAK -> scale.peakAt
            }

    val thresholdRange: IntRange?
        get() =
            when (selected) {
                AudioScaleStop.LOW -> null
                AudioScaleStop.MEDIUM -> 1 until scale.peakAt
                AudioScaleStop.PEAK -> (scale.mediumAt + 1)..100
            }

    fun select(stop: AudioScaleStop): AudioScaleEditorModel = copy(selected = stop)

    fun updateColor(color: RgbColor): AudioScaleEditorModel =
        copy(
            scale =
                when (selected) {
                    AudioScaleStop.LOW -> scale.copy(lowColor = color)
                    AudioScaleStop.MEDIUM -> scale.copy(mediumColor = color)
                    AudioScaleStop.PEAK -> scale.copy(peakColor = color)
                },
        )

    fun updateThreshold(value: Int): AudioScaleEditorModel {
        val range = thresholdRange ?: return this
        val clamped = value.coerceIn(range)
        return copy(
            scale =
                when (selected) {
                    AudioScaleStop.MEDIUM -> scale.copy(mediumAt = clamped)
                    AudioScaleStop.PEAK -> scale.copy(peakAt = clamped)
                    AudioScaleStop.LOW -> scale
                },
        )
    }

    fun reset(): AudioScaleEditorModel = copy(scale = AudioScale.DEFAULT)
}
