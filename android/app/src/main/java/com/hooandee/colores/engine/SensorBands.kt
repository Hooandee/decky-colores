package com.hooandee.colores.engine

import com.hooandee.colores.led.RgbColor
import org.json.JSONObject

enum class SensorKind(
    val maximum: Int,
) {
    BATTERY(100),
    TEMPERATURE(120),
}

data class BandSet(
    val battery: List<SensorBand>,
    val temperature: List<SensorBand>,
) {
    fun bands(kind: SensorKind): List<SensorBand> =
        when (kind) {
            SensorKind.BATTERY -> battery
            SensorKind.TEMPERATURE -> temperature
        }

    fun replace(
        kind: SensorKind,
        bands: List<SensorBand>,
    ): BandSet? {
        if (!bands.isValidFor(kind)) return null
        return when (kind) {
            SensorKind.BATTERY -> copy(battery = bands)
            SensorKind.TEMPERATURE -> copy(temperature = bands)
        }
    }

    companion object {
        private val BATTERY_FALLBACK =
            listOf(
                SensorBand(81.0, RgbColor(0, 120, 255)),
                SensorBand(61.0, RgbColor(0, 200, 60)),
                SensorBand(41.0, RgbColor(255, 200, 0)),
                SensorBand(21.0, RgbColor(255, 110, 0)),
                SensorBand(0.0, RgbColor(255, 30, 20)),
            )
        private val TEMPERATURE_FALLBACK =
            listOf(
                SensorBand(90.0, RgbColor(255, 30, 20)),
                SensorBand(80.0, RgbColor(255, 110, 0)),
                SensorBand(68.0, RgbColor(255, 200, 0)),
                SensorBand(55.0, RgbColor(0, 200, 60)),
                SensorBand(0.0, RgbColor(0, 120, 255)),
            )

        val FALLBACK = BandSet(BATTERY_FALLBACK, TEMPERATURE_FALLBACK)

        fun parse(json: String): BandSet =
            runCatching {
                val root = JSONObject(json)
                BandSet(
                    battery = root.getJSONObject("battery").bands().ifEmpty { BATTERY_FALLBACK },
                    temperature = root.getJSONObject("temperature").bands().ifEmpty { TEMPERATURE_FALLBACK },
                )
            }.getOrElse { FALLBACK }

        private fun JSONObject.bands(): List<SensorBand> {
            val bands = getJSONArray("bands")
            return (0 until bands.length()).map { index ->
                val band = bands.getJSONObject(index)
                val color = band.getJSONObject("color")
                SensorBand(
                    min = band.getDouble("min"),
                    color = RgbColor(color.getInt("r"), color.getInt("g"), color.getInt("b")),
                )
            }
        }
    }
}

private fun List<SensorBand>.isValidFor(kind: SensorKind): Boolean =
    size == 5 &&
        last().min == 0.0 &&
        zipWithNext().all { (higher, lower) -> higher.min > lower.min } &&
        all { band ->
            band.min.isFinite() &&
                band.min >= 0.0 &&
                band.min <= kind.maximum &&
                band.min % 1.0 == 0.0 &&
                band.color.red in 0..255 &&
                band.color.green in 0..255 &&
                band.color.blue in 0..255
        }
