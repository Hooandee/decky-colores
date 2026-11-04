package com.hooandee.colores.profiles

import com.hooandee.colores.control.AppMode
import com.hooandee.colores.led.RgbColor
import org.json.JSONArray
import org.json.JSONObject

data class StyleEffect(
    val id: String,
    val speed: Int,
    val useGradient: Boolean,
)

data class SavedStyle(
    val name: String,
    val mode: AppMode? = null,
    val colors: List<RgbColor>? = null,
    val gradient: List<RgbColor>? = null,
    val gradientSpeed: Int? = null,
    val brightness: Int? = null,
    val effect: StyleEffect? = null,
    val ambientVividness: Int? = null,
    val ambientSmoothing: Int? = null,
    val ambientAudio: Boolean? = null,
    val batteryBreathe: Boolean? = null,
    val temperatureBreathe: Boolean? = null,
) {
    fun applyTo(profile: LightingProfile): LightingProfile =
        profile.copy(
            mode = mode ?: profile.mode,
            staticColors = colors ?: profile.staticColors,
            solidColor = colors?.firstOrNull() ?: profile.solidColor,
            gradientStops = gradient ?: profile.gradientStops,
            gradientSpeed = gradientSpeed ?: profile.gradientSpeed,
            brightness = brightness ?: profile.brightness,
            effectId = effect?.id ?: profile.effectId,
            speed = effect?.speed ?: profile.speed,
            effectUsesGradient = effect?.useGradient ?: profile.effectUsesGradient,
            batteryBreathe = batteryBreathe ?: profile.batteryBreathe,
            temperatureBreathe = temperatureBreathe ?: profile.temperatureBreathe,
        )
}

data class StyleMerge(
    val styles: List<SavedStyle>,
    val added: Int,
    val replaced: Int,
)

object SavedStyles {
    const val FORMAT = "colores-styles"
    const val VERSION = 1
    const val NAME_MAX = 40
    const val MAX_STYLES = 50

    private val modes =
        mapOf(
            "color" to AppMode.COLOR,
            "gradient" to AppMode.GRADIENT,
            "effect" to AppMode.EFFECT,
            "ambilight" to AppMode.AMBIENT,
            "audio" to AppMode.AUDIO,
            "battery" to AppMode.BATTERY,
            "temperature" to AppMode.TEMPERATURE,
            "performance" to AppMode.PERFORMANCE,
            "clock" to AppMode.CLOCK,
        )
    private val modeNames = modes.entries.associate { (name, mode) -> mode to name }

    fun fromProfile(
        name: String,
        profile: LightingProfile,
        ambientVividness: Int,
        ambientSmoothing: Int,
        ambientAudio: Boolean? = null,
    ): SavedStyle? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > NAME_MAX) return null
        return SavedStyle(
            name = trimmed,
            mode = profile.mode,
            colors = profile.staticColors,
            gradient = profile.gradientStops,
            gradientSpeed = profile.gradientSpeed,
            brightness = profile.brightness,
            effect = StyleEffect(profile.effectId, profile.speed, profile.effectUsesGradient),
            ambientVividness = ambientVividness,
            ambientSmoothing = ambientSmoothing,
            ambientAudio = ambientAudio,
            batteryBreathe = profile.batteryBreathe,
            temperatureBreathe = profile.temperatureBreathe,
        )
    }

    fun parse(raw: JSONObject): SavedStyle? =
        runCatching {
            val name = raw.getString("name").trim()
            require(name.isNotEmpty() && name.length <= NAME_MAX)
            val effect =
                raw.optJSONObject("effect")?.let {
                    StyleEffect(
                        id = it.getString("id").also { id -> require(id.isNotEmpty()) },
                        speed = it.optInt("speed", 50).inRange(0, 100),
                        useGradient = it.optBoolean("useGradient", false),
                    )
                }
            val ambilight = raw.optJSONObject("ambilight")
            SavedStyle(
                name = name,
                mode = raw.optStringOrNull("mode")?.let { requireNotNull(modes[it]) },
                colors = raw.optJSONArray("colors")?.colors(1),
                gradient = raw.optJSONArray("gradient")?.colors(2),
                gradientSpeed = raw.optIntOrNull("gradientSpeed")?.inRange(0, 100),
                brightness = raw.optIntOrNull("brightness")?.inRange(0, 100),
                effect = effect,
                ambientVividness = ambilight?.optIntOrNull("vividness")?.inRange(0, 100),
                ambientSmoothing = ambilight?.optIntOrNull("smoothing")?.inRange(0, 100),
                ambientAudio = ambilight?.optBooleanOrNull("audio"),
                batteryBreathe = raw.optBooleanOrNull("batteryBreathe"),
                temperatureBreathe = raw.optBooleanOrNull("temperatureBreathe"),
            )
        }.getOrNull()

    fun toJson(style: SavedStyle): JSONObject =
        JSONObject().apply {
            put("name", style.name)
            style.mode?.let { put("mode", modeNames.getValue(it)) }
            style.colors?.let { put("colors", it.toJson()) }
            style.gradient?.let { put("gradient", it.toJson()) }
            style.gradientSpeed?.let { put("gradientSpeed", it) }
            style.brightness?.let { put("brightness", it) }
            style.effect?.let {
                put("effect", JSONObject().put("id", it.id).put("speed", it.speed).put("useGradient", it.useGradient))
            }
            if (style.ambientVividness != null || style.ambientSmoothing != null || style.ambientAudio != null) {
                put(
                    "ambilight",
                    JSONObject().apply {
                        style.ambientVividness?.let { put("vividness", it) }
                        style.ambientSmoothing?.let { put("smoothing", it) }
                        style.ambientAudio?.let { put("audio", it) }
                    },
                )
            }
            style.batteryBreathe?.let { put("batteryBreathe", it) }
            style.temperatureBreathe?.let { put("temperatureBreathe", it) }
        }

    fun document(styles: List<SavedStyle>): String =
        JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("styles", JSONArray().apply { styles.forEach { put(toJson(it)) } })
            .toString(2)

    fun importDocument(text: String): Pair<List<SavedStyle>, Int> {
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "not a Colores styles file" }
        require(root.optInt("version", 0) in 1..VERSION) { "unsupported styles version" }
        val entries = root.getJSONArray("styles")
        val styles = mutableListOf<SavedStyle>()
        var skipped = 0
        for (index in 0 until entries.length()) {
            val style = entries.optJSONObject(index)?.let(::parse)
            if (style == null) skipped++ else styles += style
        }
        return styles to skipped
    }

    fun merge(
        current: List<SavedStyle>,
        incoming: List<SavedStyle>,
    ): StyleMerge {
        val merged = current.toMutableList()
        var added = 0
        var replaced = 0
        for (style in incoming) {
            val index = merged.indexOfFirst { it.name.equals(style.name, ignoreCase = true) }
            when {
                index >= 0 -> {
                    merged[index] = style
                    replaced++
                }
                merged.size < MAX_STYLES -> {
                    merged += style
                    added++
                }
            }
        }
        return StyleMerge(merged, added, replaced)
    }

    fun remove(
        current: List<SavedStyle>,
        name: String,
    ): List<SavedStyle> = current.filterNot { it.name.equals(name, ignoreCase = true) }

    private fun JSONArray.colors(minimum: Int): List<RgbColor> {
        require(length() in minimum..32)
        return List(length()) { index ->
            val color = getJSONObject(index)
            RgbColor(color.channel("r"), color.channel("g"), color.channel("b"))
        }
    }

    private fun JSONObject.channel(name: String): Int = getInt(name).inRange(0, 255)

    private fun List<RgbColor>.toJson(): JSONArray =
        JSONArray().apply { forEach { put(JSONObject().put("r", it.red).put("g", it.green).put("b", it.blue)) } }

    private fun Int.inRange(
        low: Int,
        high: Int,
    ): Int = also { require(it in low..high) }

    private fun JSONObject.optStringOrNull(name: String): String? = if (has(name)) getString(name) else null

    private fun JSONObject.optIntOrNull(name: String): Int? = if (has(name)) getInt(name) else null

    private fun JSONObject.optBooleanOrNull(name: String): Boolean? = if (has(name)) getBoolean(name) else null
}
