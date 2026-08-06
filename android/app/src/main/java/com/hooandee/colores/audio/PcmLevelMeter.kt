package com.hooandee.colores.audio

import kotlin.math.log10
import kotlin.math.sqrt

object PcmLevelMeter {
    fun level(
        samples: ShortArray,
        count: Int,
    ): Double {
        val size = count.coerceIn(0, samples.size)
        if (size == 0) return 0.0
        var sumSquares = 0.0
        for (index in 0 until size) {
            val sample = samples[index].toDouble()
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / size)
        if (rms <= 0.0) return 0.0
        return (1.0 + 20.0 * log10(rms / FULL_SCALE) / DYNAMIC_RANGE_DB).coerceIn(0.0, 1.0)
    }

    fun ease(
        previous: Double,
        target: Double,
    ): Double {
        val from = previous.coerceIn(0.0, 1.0)
        val to = target.coerceIn(0.0, 1.0)
        val alpha = if (to > from) ATTACK else RELEASE
        return (from + (to - from) * alpha).coerceIn(0.0, 1.0)
    }

    private const val FULL_SCALE = 32_768.0
    private const val DYNAMIC_RANGE_DB = 40.0
    private const val ATTACK = 0.6
    private const val RELEASE = 0.2
}
