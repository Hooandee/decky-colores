package com.hooandee.colores.gradient

import android.content.Context
import com.hooandee.colores.led.RgbColor
import org.json.JSONObject

data class GradientPreset(
    val id: String,
    val stops: List<RgbColor>,
)

class GradientPresetRepository(
    private val readJson: () -> String,
) {
    constructor(context: Context) : this({ context.assets.open("gradients.json").bufferedReader().use { it.readText() } })

    fun load(): List<GradientPreset> =
        runCatching {
            val root = JSONObject(readJson())
            require(root.getInt("schemaVersion") == 1)
            val presets = root.getJSONArray("presets")
            (0 until presets.length()).map { index ->
                val preset = presets.getJSONObject(index)
                val colors = preset.getJSONArray("colors")
                GradientPreset(
                    id = preset.getString("id").also { require(it.isNotBlank()) },
                    stops =
                        (0 until colors.length()).map { colorIndex ->
                            colors.getJSONObject(colorIndex).toRgbColor()
                        }.also { require(it.isNotEmpty()) },
                )
            }
        }.getOrDefault(emptyList())
}

internal fun JSONObject.toRgbColor() =
    RgbColor(
        red = getInt("r").coerceIn(0, 255),
        green = getInt("g").coerceIn(0, 255),
        blue = getInt("b").coerceIn(0, 255),
    )
