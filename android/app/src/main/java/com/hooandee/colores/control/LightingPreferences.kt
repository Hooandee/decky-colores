package com.hooandee.colores.control

import android.content.Context
import android.content.SharedPreferences
import com.hooandee.colores.ambient.AmbientSamplingMode
import com.hooandee.colores.ambient.normalizedAmbientCaptureFps
import com.hooandee.colores.engine.BandSet
import com.hooandee.colores.engine.AudioScale
import com.hooandee.colores.engine.AudioSensitivity
import com.hooandee.colores.engine.SensorBand
import com.hooandee.colores.engine.SensorKind
import com.hooandee.colores.led.RgbColor
import org.json.JSONArray
import org.json.JSONObject

data class StoredLighting(
    val mode: AppMode = AppMode.COLOR,
    val effectId: String = "breathing",
    val speed: Int = 50,
    val gradientSpeed: Int = 30,
    val effectUsesGradient: Boolean = false,
    val solidColor: RgbColor = RgbColor(93, 81, 255),
    val brightness: Int? = null,
    val power: Boolean? = null,
    val chargerOnly: Boolean = false,
    val batteryBreathe: Boolean = true,
    val temperatureBreathe: Boolean = true,
    val audioScale: AudioScale = AudioScale.DEFAULT,
    val audioSensitivityDb: Int = AudioSensitivity.NORMAL_DB,
    val ambientCaptureFps: Int = 10,
    val ambientSamplingMode: AmbientSamplingMode = AmbientSamplingMode.FULL_SCENE,
    val ambientVividness: Int = 35,
    val ambientSmoothing: Int = 45,
    val sensorBands: BandSet = BandSet.FALLBACK,
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

    fun load(
        deviceId: String,
        defaults: BandSet = BandSet.FALLBACK,
    ): StoredLighting = read(key(deviceId))?.let { decode(it, defaults) } ?: StoredLighting(sensorBands = defaults)

    fun save(
        deviceId: String,
        value: StoredLighting,
    ) {
        write(key(deviceId), encode(value).toString())
        write(ACTIVE_DEVICE_KEY, deviceId)
    }

    fun activeDeviceId(): String? = read(ACTIVE_DEVICE_KEY)?.takeIf { it.isNotBlank() }

    fun shouldRestoreInBackground(): Boolean {
        val deviceId = activeDeviceId() ?: return false
        val stored = load(deviceId)
        return stored.power != false && (stored.mode != AppMode.COLOR || stored.chargerOnly)
    }

    private fun decode(
        raw: String,
        defaults: BandSet,
    ): StoredLighting =
        runCatching {
            val root = JSONObject(raw)
            StoredLighting(
                mode = runCatching { AppMode.valueOf(root.optString("mode", AppMode.COLOR.name)) }.getOrDefault(AppMode.COLOR),
                effectId = root.optString("effectId", "breathing").ifBlank { "breathing" },
                speed = root.optInt("speed", 50).coerceIn(0, 100),
                gradientSpeed = root.optInt("gradientSpeed", 30).coerceIn(0, 100),
                effectUsesGradient = root.optBoolean("effectUsesGradient", false),
                solidColor =
                    root.optJSONObject("solidColor")?.let {
                        RgbColor(it.optInt("r", 93), it.optInt("g", 81), it.optInt("b", 255))
                    } ?: RgbColor(93, 81, 255),
                brightness = root.optNullableInt("brightness")?.coerceIn(0, 100),
                power = root.optNullableBoolean("power"),
                chargerOnly = root.optBoolean("chargerOnly", false),
                batteryBreathe = root.optBoolean("batteryBreathe", true),
                temperatureBreathe = root.optBoolean("temperatureBreathe", true),
                audioScale = decodeAudioScale(root.optJSONObject("audioScale")),
                audioSensitivityDb =
                    root.optInt("audioSensitivityDb", AudioSensitivity.NORMAL_DB)
                        .coerceIn(AudioSensitivity.MIN_DB, AudioSensitivity.MAX_DB),
                ambientCaptureFps = root.optInt("ambientCaptureFps", 10).normalizedAmbientCaptureFps(),
                ambientSamplingMode =
                    runCatching {
                        AmbientSamplingMode.valueOf(
                            root.optString("ambientSamplingMode", AmbientSamplingMode.FULL_SCENE.name),
                        )
                    }.getOrDefault(AmbientSamplingMode.FULL_SCENE),
                ambientVividness = root.optInt("ambientVividness", 35).coerceIn(0, 100),
                ambientSmoothing = root.optInt("ambientSmoothing", 45).coerceIn(0, 100),
                sensorBands = decodeBands(root.optJSONObject("sensorBands"), defaults),
            )
        }.getOrDefault(StoredLighting(sensorBands = defaults))

    private fun encode(value: StoredLighting): JSONObject =
        JSONObject()
            .put("mode", value.mode.name)
            .put("effectId", value.effectId)
            .put("speed", value.speed)
            .put("gradientSpeed", value.gradientSpeed)
            .put("effectUsesGradient", value.effectUsesGradient)
            .put(
                "solidColor",
                JSONObject()
                    .put("r", value.solidColor.red)
                    .put("g", value.solidColor.green)
                    .put("b", value.solidColor.blue),
            )
            .apply {
                value.brightness?.let { put("brightness", it.coerceIn(0, 100)) }
                value.power?.let { put("power", it) }
            }
            .put("chargerOnly", value.chargerOnly)
            .put("batteryBreathe", value.batteryBreathe)
            .put("temperatureBreathe", value.temperatureBreathe)
            .put("audioScale", encodeAudioScale(value.audioScale))
            .put(
                "audioSensitivityDb",
                value.audioSensitivityDb.coerceIn(AudioSensitivity.MIN_DB, AudioSensitivity.MAX_DB),
            )
            .put("ambientCaptureFps", value.ambientCaptureFps.normalizedAmbientCaptureFps())
            .put("ambientSamplingMode", value.ambientSamplingMode.name)
            .put("ambientVividness", value.ambientVividness.coerceIn(0, 100))
            .put("ambientSmoothing", value.ambientSmoothing.coerceIn(0, 100))
            .put(
                "sensorBands",
                JSONObject()
                    .put("battery", encodeBands(value.sensorBands.battery))
                    .put("temperature", encodeBands(value.sensorBands.temperature)),
            )

    private fun decodeAudioScale(root: JSONObject?): AudioScale {
        if (root == null) return AudioScale.DEFAULT
        return runCatching {
            AudioScale(
                lowColor = root.getJSONObject("low").toRgbColor(),
                mediumColor = root.getJSONObject("medium").toRgbColor(),
                peakColor = root.getJSONObject("peak").toRgbColor(),
                mediumAt = root.getInt("mediumAt"),
                peakAt = root.getInt("peakAt"),
            )
        }.getOrDefault(AudioScale.DEFAULT)
    }

    private fun encodeAudioScale(scale: AudioScale): JSONObject =
        JSONObject()
            .put("low", scale.lowColor.toJson())
            .put("medium", scale.mediumColor.toJson())
            .put("peak", scale.peakColor.toJson())
            .put("mediumAt", scale.mediumAt)
            .put("peakAt", scale.peakAt)

    private fun JSONObject.toRgbColor(): RgbColor =
        RgbColor(
            getInt("r").coerceIn(0, 255),
            getInt("g").coerceIn(0, 255),
            getInt("b").coerceIn(0, 255),
        )

    private fun RgbColor.toJson(): JSONObject =
        JSONObject()
            .put("r", red.coerceIn(0, 255))
            .put("g", green.coerceIn(0, 255))
            .put("b", blue.coerceIn(0, 255))

    private fun decodeBands(
        root: JSONObject?,
        defaults: BandSet,
    ): BandSet {
        if (root == null) return defaults
        return SensorKind.entries.fold(defaults) { result, kind ->
            val parsed = runCatching { root.getJSONArray(kind.key()).toBands() }.getOrNull()
            parsed?.let { result.replace(kind, it) } ?: result
        }
    }

    private fun JSONArray.toBands(): List<SensorBand> =
        (0 until length()).map { index ->
            val entry = getJSONObject(index)
            val color = entry.getJSONObject("color")
            SensorBand(
                min = entry.getDouble("min"),
                color = RgbColor(color.getInt("r"), color.getInt("g"), color.getInt("b")),
            )
        }

    private fun encodeBands(bands: List<SensorBand>): JSONArray =
        JSONArray().apply {
            bands.forEach { band ->
                put(
                    JSONObject()
                        .put("min", band.min.toInt())
                        .put(
                            "color",
                            JSONObject()
                                .put("r", band.color.red)
                                .put("g", band.color.green)
                                .put("b", band.color.blue),
                        ),
                )
            }
        }

    private fun SensorKind.key(): String = name.lowercase()

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun JSONObject.optNullableBoolean(name: String): Boolean? =
        if (has(name) && !isNull(name)) optBoolean(name) else null

    private fun key(deviceId: String) = "lighting:$deviceId"

    private companion object {
        const val FILE_NAME = "lighting"
        const val ACTIVE_DEVICE_KEY = "active_device"
    }
}
