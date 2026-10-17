package com.hooandee.colores.engine

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ScreenBrightness {
    const val FLOOR = 0.10f

    private const val R = 0.5f
    private const val A = 0.17883277f
    private const val B = 0.28466892f
    private const val C = 0.55991073f

    fun sliderLevel(
        setting: Int,
        minimum: Int,
        maximum: Int,
    ): Float {
        if (maximum <= minimum) return 1f
        val normalized = ((setting - minimum).toFloat() / (maximum - minimum)).coerceIn(0f, 1f) * 12f
        val gamma = if (normalized <= 1f) sqrt(normalized) * R else A * ln(normalized - B) + C
        return gamma.coerceIn(0f, 1f)
    }

    fun ledBrightness(
        userBrightness: Int,
        factor: Float,
    ): Int {
        val user = userBrightness.coerceIn(0, 100)
        if (user == 0) return 0
        return (user * factor.coerceIn(FLOOR, 1f)).roundToInt().coerceAtLeast(1)
    }
}

class FactorRamp(
    private val durationMs: Long,
) {
    private data class State(
        val startValue: Float,
        val target: Float,
        val startAtMs: Long,
    )

    @Volatile
    private var state = State(1f, 1f, 0L)

    val target: Float
        get() = state.target

    fun snap(value: Float) {
        state = State(value, value, 0L)
    }

    fun setTarget(
        value: Float,
        nowMs: Long,
    ) {
        state = State(value(nowMs), value, nowMs)
    }

    fun value(nowMs: Long): Float {
        val current = state
        val progress = current.progress(nowMs)
        if (progress >= 1f) return current.target
        val eased = 1f - (1f - progress).pow(3)
        return current.startValue + (current.target - current.startValue) * eased
    }

    fun settled(nowMs: Long): Boolean {
        val current = state
        return current.startValue == current.target || current.progress(nowMs) >= 1f
    }

    private fun State.progress(nowMs: Long): Float =
        if (durationMs <= 0L) 1f else ((nowMs - startAtMs).toFloat() / durationMs).coerceAtLeast(0f)
}
