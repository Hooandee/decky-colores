package com.hooandee.colores.control

import android.content.Context
import android.content.SharedPreferences
import com.hooandee.colores.led.RgbColor
import org.json.JSONObject

data class StoredLighting(
    val mode: AppMode = AppMode.COLOR,
    val effectId: String = "breathing",
    val speed: Int = 50,
    val solidColor: RgbColor = RgbColor(93, 81, 255),
    val chargerOnly: Boolean = false,
    val batteryBreathe: Boolean = true,
)

class LightingPreferences(
    private val read: (String) -> String?,
    private val write: (String, String) -> Unit,
) {
    private constructor(preferences: SharedPreferences) : this(
        read = { key -> preferences.getString(key, null) },
        write = { key, value -> preferences.edit().putString(key, value).apply() },
    )

    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    fun load(deviceId: String): StoredLighting = read(key(deviceId))?.let(::decode) ?: StoredLighting()

    fun save(
        deviceId: String,
        value: StoredLighting,
    ) {
        write(key(deviceId), encode(value).toString())
    }

    private fun decode(raw: String): StoredLighting =
        runCatching {
            val root = JSONObject(raw)
            StoredLighting(
                mode = runCatching { AppMode.valueOf(root.optString("mode", AppMode.COLOR.name)) }.getOrDefault(AppMode.COLOR),
                effectId = root.optString("effectId", "breathing").ifBlank { "breathing" },
                speed = root.optInt("speed", 50).coerceIn(0, 100),
                solidColor =
                    root.optJSONObject("solidColor")?.let {
                        RgbColor(it.optInt("r", 93), it.optInt("g", 81), it.optInt("b", 255))
                    } ?: RgbColor(93, 81, 255),
                chargerOnly = root.optBoolean("chargerOnly", false),
                batteryBreathe = root.optBoolean("batteryBreathe", true),
            )
        }.getOrDefault(StoredLighting())

    private fun encode(value: StoredLighting): JSONObject =
        JSONObject()
            .put("mode", value.mode.name)
            .put("effectId", value.effectId)
            .put("speed", value.speed)
            .put(
                "solidColor",
                JSONObject()
                    .put("r", value.solidColor.red)
                    .put("g", value.solidColor.green)
                    .put("b", value.solidColor.blue),
            )
            .put("chargerOnly", value.chargerOnly)
            .put("batteryBreathe", value.batteryBreathe)

    private fun key(deviceId: String) = "lighting:$deviceId"

    private companion object {
        const val FILE_NAME = "lighting"
    }
}
