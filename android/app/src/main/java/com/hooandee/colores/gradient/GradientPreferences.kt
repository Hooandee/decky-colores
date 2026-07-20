package com.hooandee.colores.gradient

import android.content.Context
import android.content.SharedPreferences
import com.hooandee.colores.led.RgbColor
import org.json.JSONArray
import org.json.JSONObject

enum class LightingMode {
    COLOR,
    GRADIENT,
}

data class SavedGradient(
    val name: String,
    val stops: List<RgbColor>,
)

data class DeviceGradientPreferences(
    val mode: LightingMode = LightingMode.COLOR,
    val currentStops: List<RgbColor> = emptyList(),
    val lastPresetId: String? = null,
    val savedGradients: List<SavedGradient> = emptyList(),
)

class GradientPreferences(
    private val read: (String) -> String?,
    private val write: (String, String) -> Unit,
) {
    private constructor(preferences: SharedPreferences) : this(
        read = { key -> preferences.getString(key, null) },
        write = { key, value -> preferences.edit().putString(key, value).apply() },
    )

    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    fun load(deviceId: String): DeviceGradientPreferences =
        read(key(deviceId))?.let(::decode) ?: DeviceGradientPreferences()

    fun save(
        deviceId: String,
        value: DeviceGradientPreferences,
    ) {
        write(key(deviceId), encode(value).toString())
    }

    fun upsert(
        deviceId: String,
        name: String,
        stops: List<RgbColor>,
    ): DeviceGradientPreferences {
        val current = load(deviceId)
        val saved =
            (current.savedGradients.filterNot { it.name == name } + SavedGradient(name, stops.sanitized()))
                .takeLast(MAX_SAVED)
        return current.copy(savedGradients = saved).also { save(deviceId, it) }
    }

    fun delete(
        deviceId: String,
        name: String,
    ): DeviceGradientPreferences {
        val updated = load(deviceId).let { it.copy(savedGradients = it.savedGradients.filterNot { saved -> saved.name == name }) }
        save(deviceId, updated)
        return updated
    }

    private fun decode(raw: String): DeviceGradientPreferences =
        runCatching {
            val root = JSONObject(raw)
            DeviceGradientPreferences(
                mode = runCatching { LightingMode.valueOf(root.optString("mode", LightingMode.COLOR.name)) }.getOrDefault(LightingMode.COLOR),
                currentStops = root.optJSONArray("currentStops")?.colors().orEmpty(),
                lastPresetId = root.optString("lastPresetId").takeIf { it.isNotBlank() },
                savedGradients =
                    root.optJSONArray("savedGradients")?.let { saved ->
                        (0 until saved.length()).mapNotNull { index ->
                            runCatching {
                                val entry = saved.getJSONObject(index)
                                SavedGradient(entry.getString("name"), entry.getJSONArray("stops").colors())
                            }.getOrNull()
                        }
                    }.orEmpty().takeLast(MAX_SAVED),
            )
        }.getOrDefault(DeviceGradientPreferences())

    private fun encode(value: DeviceGradientPreferences) =
        JSONObject()
            .put("mode", value.mode.name)
            .put("currentStops", value.currentStops.toJson())
            .put("lastPresetId", value.lastPresetId ?: "")
            .put(
                "savedGradients",
                JSONArray().apply {
                    value.savedGradients.takeLast(MAX_SAVED).forEach { saved ->
                        put(JSONObject().put("name", saved.name).put("stops", saved.stops.toJson()))
                    }
                },
            )

    private fun key(deviceId: String) = "gradient:$deviceId"

    private companion object {
        const val FILE_NAME = "gradients"
        const val MAX_SAVED = 50
    }
}

private fun JSONArray.colors(): List<RgbColor> =
    (0 until length()).mapNotNull { index -> runCatching { getJSONObject(index).toRgbColor() }.getOrNull() }

private fun List<RgbColor>.sanitized() =
    map { color ->
        RgbColor(
            red = color.red.coerceIn(0, 255),
            green = color.green.coerceIn(0, 255),
            blue = color.blue.coerceIn(0, 255),
        )
    }

private fun List<RgbColor>.toJson() =
    JSONArray().apply {
        sanitized().forEach { color ->
            put(JSONObject().put("r", color.red).put("g", color.green).put("b", color.blue))
        }
    }
