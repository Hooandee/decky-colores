package com.hooandee.colores.sensor

import java.io.File

enum class PerformanceMetric {
    GPU,
    CPU,
}

interface PerformanceSource {
    val metric: PerformanceMetric
    val available: Boolean

    /** Load as a percentage 0..100, or null until a delta / valid reading exists. */
    fun read(): Double?
}

/**
 * Total CPU load from /proc/stat deltas. Universal on Android/Linux and readable
 * without root, so it is the explicit fallback when no GPU busy node is exposed.
 * The first read seeds the baseline and returns null.
 */
class CpuStatPerformanceSource(
    private val readStat: () -> String? = { runCatching { File("/proc/stat").readText() }.getOrNull() },
) : PerformanceSource {
    override val metric = PerformanceMetric.CPU

    private var previous: Pair<Long, Long>? = null

    override val available: Boolean
        get() = parse(readStat()) != null

    override fun read(): Double? {
        val current = parse(readStat()) ?: return null
        val prev = previous
        previous = current
        if (prev == null) return null
        val totalDelta = current.first - prev.first
        val idleDelta = current.second - prev.second
        if (totalDelta <= 0) return null
        val busy = (totalDelta - idleDelta).toDouble() / totalDelta.toDouble()
        return (busy * 100.0).coerceIn(0.0, 100.0)
    }

    private fun parse(stat: String?): Pair<Long, Long>? {
        val line =
            stat?.lineSequence()?.firstOrNull { it.startsWith("cpu ") || it.startsWith("cpu\t") }
                ?: return null
        val fields = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (fields.size < 4) return null
        val idle = fields[3] + (fields.getOrNull(4) ?: 0L)
        val total = fields.sum()
        return total to idle
    }
}

/**
 * Adreno/kgsl GPU busy percentage. Treated strictly as a probe: it is only used
 * when the node exists and reads a plausible value, never assumed present. Nothing
 * here hardcodes a Qualcomm path as universal truth.
 */
class KgslGpuPerformanceSource(
    private val readBusy: () -> String? = {
        KGSL_NODES.asSequence()
            .mapNotNull { runCatching { File(it).readText() }.getOrNull() }
            .firstOrNull()
    },
) : PerformanceSource {
    override val metric = PerformanceMetric.GPU

    override val available: Boolean
        get() = parse(readBusy()) != null

    override fun read(): Double? = parse(readBusy())

    private fun parse(raw: String?): Double? {
        val token = raw?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.toDoubleOrNull() ?: return null
        return token.takeIf { it in 0.0..100.0 }
    }

    private companion object {
        val KGSL_NODES =
            listOf(
                "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
                "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
            )
    }
}

object PerformanceSources {
    fun detect(
        gpu: PerformanceSource = KgslGpuPerformanceSource(),
        cpu: PerformanceSource = CpuStatPerformanceSource(),
    ): PerformanceSource? =
        when {
            gpu.available -> gpu
            cpu.available -> cpu
            else -> null
        }
}
