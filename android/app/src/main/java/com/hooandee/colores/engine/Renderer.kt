package com.hooandee.colores.engine

import com.hooandee.colores.engine.FrameMath.breatheFactor
import com.hooandee.colores.engine.FrameMath.clamp8
import com.hooandee.colores.engine.FrameMath.lerp
import com.hooandee.colores.led.RgbColor
import kotlin.math.abs

data class RenderTick(
    val colors: List<RgbColor>,
    val nextDelayMs: Long,
)

interface Renderer {
    fun render(nowSeconds: Double): RenderTick
}

data class EffectPalette(
    val base: List<RgbColor>,
    val stops: List<RgbColor>,
)

class EffectRenderer(
    private val effectId: String,
    private val zones: Int,
    private val frameIntervalMs: Long,
    private val speed: () -> Int,
    private val palette: () -> EffectPalette,
) : Renderer {
    override fun render(nowSeconds: Double): RenderTick {
        val resolved = palette()
        return RenderTick(
            Effects.frame(effectId, nowSeconds, speed(), zones, resolved.base, resolved.stops),
            frameIntervalMs,
        )
    }
}

class IndicatorRenderer(
    private val zones: Int,
    private val frameIntervalMs: Long,
    private val idleIntervalMs: Long,
    private val breatheSpeed: Int = 22,
    private val target: () -> RgbColor?,
    private val breathing: () -> Boolean,
) : Renderer {
    private var displayed: Triple<Double, Double, Double>? = null
    private var startSeconds: Double? = null
    private var lastFrame: List<RgbColor> = List(zones) { RgbColor(0, 0, 0) }

    override fun render(nowSeconds: Double): RenderTick {
        val start = startSeconds ?: nowSeconds.also { startSeconds = it }
        val tgt = target() ?: return RenderTick(lastFrame, idleIntervalMs)
        val current = displayed ?: Triple(tgt.red.toDouble(), tgt.green.toDouble(), tgt.blue.toDouble()).also { displayed = it }
        val isBreathing = breathing()

        val settled =
            !isBreathing &&
                maxOf(
                    abs(current.first - tgt.red),
                    abs(current.second - tgt.green),
                    abs(current.third - tgt.blue),
                ) <= CONVERGE_EPS
        if (settled) {
            displayed = Triple(tgt.red.toDouble(), tgt.green.toDouble(), tgt.blue.toDouble())
            lastFrame = List(zones) { tgt }
            return RenderTick(lastFrame, idleIntervalMs)
        }

        val eased =
            Triple(
                lerp(current.first, tgt.red.toDouble(), EASE),
                lerp(current.second, tgt.green.toDouble(), EASE),
                lerp(current.third, tgt.blue.toDouble(), EASE),
            )
        displayed = eased
        val factor = if (isBreathing) breatheFactor(nowSeconds - start, breatheSpeed) else 1.0
        val frame = RgbColor(clamp8(eased.first * factor), clamp8(eased.second * factor), clamp8(eased.third * factor))
        lastFrame = List(zones) { frame }
        return RenderTick(lastFrame, frameIntervalMs)
    }

    private companion object {
        const val EASE = 0.12
        const val CONVERGE_EPS = 1.0
    }
}

class PerformanceRenderer(
    private val zones: Int,
    private val frameIntervalMs: Long,
    private val idleIntervalMs: Long,
    private val value: () -> Double?,
) : Renderer {
    private var displayed = 0.0

    override fun render(nowSeconds: Double): RenderTick {
        val target = value()?.div(100.0)?.coerceIn(0.0, 1.0) ?: 0.0
        displayed += (target - displayed) * SMOOTH
        val frame = Effects.meter(displayed, zones)
        val delay = if (abs(target - displayed) < SETTLE_EPS && target == 0.0) idleIntervalMs else frameIntervalMs
        return RenderTick(frame, delay)
    }

    private companion object {
        const val SMOOTH = 0.25
        const val SETTLE_EPS = 0.004
    }
}

class ClockRenderer(
    private val zones: Int,
    private val intervalMs: Long,
    private val hour: () -> Double,
) : Renderer {
    override fun render(nowSeconds: Double): RenderTick {
        val color = Effects.clockColor(hour())
        return RenderTick(List(zones) { color }, intervalMs)
    }
}
