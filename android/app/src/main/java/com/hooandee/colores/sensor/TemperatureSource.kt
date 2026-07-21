package com.hooandee.colores.sensor

import java.io.File

data class ThermalZoneRaw(
    val type: String,
    val raw: String?,
)

interface TemperatureSource {
    val available: Boolean

    fun readCelsius(): Double?
}

class SysfsThermalSource(
    private val readZones: () -> List<ThermalZoneRaw>,
) : TemperatureSource {
    constructor() : this(androidThermalZoneReader())

    override val available: Boolean
        get() = readCelsius() != null

    override fun readCelsius(): Double? {
        val readings =
            readZones().mapNotNull { zone ->
                normalize(zone.raw)?.let { zone.type.lowercase() to it }
            }
        if (readings.isEmpty()) return null
        readings.firstOrNull { (type, _) -> PREFERRED.any { type.contains(it) } }?.let { return it.second }
        return readings.maxOf { it.second }
    }

    private fun normalize(raw: String?): Double? {
        val value = raw?.trim()?.toDoubleOrNull() ?: return null
        val celsius = if (kotlin.math.abs(value) >= 1000.0) value / 1000.0 else value
        return celsius.takeIf { it in PLAUSIBLE_MIN..PLAUSIBLE_MAX }
    }

    private companion object {
        val PREFERRED = listOf("cpu", "soc", "tsens", "apu", "bcpu", "mtktscpu")
        const val PLAUSIBLE_MIN = -40.0
        const val PLAUSIBLE_MAX = 200.0

        fun androidThermalZoneReader(): () -> List<ThermalZoneRaw> {
            var zones: List<Pair<String, File>>? = null
            fun discover(): List<Pair<String, File>> =
                runCatching {
                    File("/sys/class/thermal").listFiles { file -> file.name.startsWith("thermal_zone") }
                        ?.sortedBy { it.name }
                        ?.map { zone ->
                            runCatching { File(zone, "type").readText().trim() }.getOrDefault("") to File(zone, "temp")
                        }
                        .orEmpty()
                }.getOrDefault(emptyList())
            return {
                (zones ?: discover().also { zones = it })
                    .map { (type, tempFile) -> ThermalZoneRaw(type, runCatching { tempFile.readText().trim() }.getOrNull()) }
            }
        }
    }
}
