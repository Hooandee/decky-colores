package com.hooandee.colores.engine

import com.hooandee.colores.led.RgbColor
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class ColorCalibration(
    val yellow: Int = 0,
    val orange: Int = 0,
    val white: Int = 0,
) {
    val isNeutral: Boolean
        get() = this == NEUTRAL

    private val tables: List<IntArray> by lazy {
        val gains = doubleArrayOf(100.0 - max(0, yellow), 100.0 + min(0, yellow), 100.0)
        if (white < 0) {
            gains[2] += white
        } else {
            gains[0] *= (100 - white) / 100.0
            gains[1] *= (100 - white) / 100.0
        }
        val gammas = doubleArrayOf(1.0, ORANGE_GAMMA_BASE.pow(-orange / 100.0), 1.0)
        List(3) { channel ->
            IntArray(256) { value -> floor(255 * gains[channel] / 100 * (value / 255.0).pow(gammas[channel]) + 0.5).toInt() }
        }
    }

    fun apply(color: RgbColor): RgbColor =
        if (isNeutral) {
            color
        } else {
            RgbColor(
                tables[0][color.red.coerceIn(0, 255)],
                tables[1][color.green.coerceIn(0, 255)],
                tables[2][color.blue.coerceIn(0, 255)],
            )
        }

    fun apply(colors: List<RgbColor>): List<RgbColor> = if (isNeutral) colors else colors.map(::apply)

    enum class Step(
        val sample: RgbColor,
        val limit: Int,
    ) {
        YELLOW(RgbColor(255, 255, 0), 60),
        ORANGE(RgbColor(255, 128, 0), 100),
        WHITE(RgbColor(255, 255, 255), 60),
    }

    fun valueOf(step: Step): Int =
        when (step) {
            Step.YELLOW -> yellow
            Step.ORANGE -> orange
            Step.WHITE -> white
        }

    fun with(
        step: Step,
        value: Int,
    ): ColorCalibration =
        when (step) {
            Step.YELLOW -> of(value, orange, white)
            Step.ORANGE -> of(yellow, value, white)
            Step.WHITE -> of(yellow, orange, value)
        }

    companion object {
        private const val ORANGE_GAMMA_BASE = 2.5
        val NEUTRAL = ColorCalibration()

        fun of(
            yellow: Int,
            orange: Int,
            white: Int,
        ): ColorCalibration =
            ColorCalibration(
                yellow.coerceIn(-Step.YELLOW.limit, Step.YELLOW.limit),
                orange.coerceIn(-Step.ORANGE.limit, Step.ORANGE.limit),
                white.coerceIn(-Step.WHITE.limit, Step.WHITE.limit),
            )
    }
}
