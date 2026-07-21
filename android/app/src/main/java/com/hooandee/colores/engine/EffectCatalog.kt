package com.hooandee.colores.engine

import com.hooandee.colores.led.RgbColor
import org.json.JSONArray

enum class EffectNeed {
    COLOR,
    GRADIENT,
    NONE,
    ;

    companion object {
        fun from(raw: String): EffectNeed =
            when (raw.trim().lowercase()) {
                "gradient" -> GRADIENT
                "none" -> NONE
                else -> COLOR
            }
    }
}

data class EffectPreset(
    val id: String,
    val need: EffectNeed,
    val defaultSpeed: Int,
    val colors: List<RgbColor>,
)

class EffectCatalog(
    val presets: List<EffectPreset>,
) {
    fun byId(id: String): EffectPreset? = presets.firstOrNull { it.id == id }

    val defaultEffectId: String
        get() = presets.firstOrNull()?.id ?: "breathing"

    companion object {
        fun parse(json: String): EffectCatalog =
            runCatching {
                val root = JSONArray(json)
                val presets =
                    (0 until root.length()).mapNotNull { index ->
                        runCatching {
                            val entry = root.getJSONObject(index)
                            val colors = entry.getJSONArray("colors")
                            EffectPreset(
                                id = entry.getString("id"),
                                need = EffectNeed.from(entry.optString("needs", "color")),
                                defaultSpeed = entry.optInt("defaultSpeed", 50).coerceIn(0, 100),
                                colors =
                                    (0 until colors.length()).map { colorIndex ->
                                        colors.getJSONObject(colorIndex).let {
                                            RgbColor(it.getInt("r"), it.getInt("g"), it.getInt("b"))
                                        }
                                    },
                            )
                        }.getOrNull()
                    }
                EffectCatalog(presets)
            }.getOrElse { EffectCatalog(emptyList()) }
    }
}
