package com.hooandee.colores.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.hooandee.colores.led.RgbColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppAppearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: RgbColor = DEFAULT_ACCENT,
)

class AppPreferences(
    private val read: (String) -> String?,
    private val write: (String, String) -> Unit,
) {
    private constructor(preferences: SharedPreferences) : this(
        read = { key -> preferences.getString(key, null) },
        write = { key, value -> preferences.edit { putString(key, value) } },
    )

    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    private val mutableAppearance = MutableStateFlow(load())
    val appearance: StateFlow<AppAppearance> = mutableAppearance.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        write(THEME_MODE_KEY, mode.name)
        mutableAppearance.value = mutableAppearance.value.copy(themeMode = mode)
    }

    fun setAccent(color: RgbColor) {
        val sanitized = color.sanitized()
        write(ACCENT_KEY, sanitized.toHex())
        mutableAppearance.value = mutableAppearance.value.copy(accent = sanitized)
    }

    private fun load() =
        AppAppearance(
            themeMode = read(THEME_MODE_KEY)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            accent = read(ACCENT_KEY)?.toRgbColor() ?: DEFAULT_ACCENT,
        )

    private companion object {
        const val FILE_NAME = "app_preferences"
        const val THEME_MODE_KEY = "theme_mode"
        const val ACCENT_KEY = "accent"
    }
}

val DEFAULT_ACCENT = RgbColor(141, 131, 255)

private fun String.toRgbColor(): RgbColor? {
    if (!matches(Regex("#[0-9A-Fa-f]{6}"))) return null
    return RgbColor(
        substring(1, 3).toInt(16),
        substring(3, 5).toInt(16),
        substring(5, 7).toInt(16),
    )
}

private fun RgbColor.toHex(): String = "#%02X%02X%02X".format(red, green, blue)

private fun RgbColor.sanitized() =
    RgbColor(
        red.coerceIn(0, 255),
        green.coerceIn(0, 255),
        blue.coerceIn(0, 255),
    )
