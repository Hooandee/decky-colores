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

/**
 * Reads the standard Linux/Android thermal-zone interface. This is NOT the x86
 * hwmon layout from the Decky backend; it only trusts `/sys/class/thermal` zones
 * that read a plausible Celsius value, and reports unavailable otherwise so the UI
 * can hide the mode instead of showing a fabricated temperature.
 */
class SysfsThermalSource(
    private val readZones: () -> List<ThermalZoneRaw>,
) : TemperatureSource {
    constructor() : this({ scanThermalZones() })

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

        fun scanThermalZones(): List<ThermalZoneRaw> =
            runCatching {
                val root = File("/sys/class/thermal")
                root.listFiles { file -> file.name.startsWith("thermal_zone") }
                    ?.sortedBy { it.name }
                    ?.map { zone ->
                        ThermalZoneRaw(
                            type = runCatching { File(zone, "type").readText().trim() }.getOrDefault(""),
                            raw = runCatching { File(zone, "temp").readText().trim() }.getOrNull(),
                        )
                    }
                    .orEmpty()
            }.getOrDefault(emptyList())
    }
}
