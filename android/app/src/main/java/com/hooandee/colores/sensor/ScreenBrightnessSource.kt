package com.hooandee.colores.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.hooandee.colores.engine.ScreenBrightness
import com.hooandee.colores.report.ColoresLog

class ScreenBrightnessSource(
    context: Context,
    private val onLevel: (Float?) -> Unit,
) {
    private val resolver = context.contentResolver
    private val range = settingRange()
    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                onLevel(read())
            }
        }

    fun register() {
        runCatching {
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer)
        }.onFailure { ColoresLog.w(TAG, "screen brightness observer unavailable", it) }
        ColoresLog.state("screen_brightness_range", "${range.first}..${range.last}")
        onLevel(read())
    }

    fun read(): Float? =
        runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) }
            .getOrNull()
            ?.let { ScreenBrightness.sliderLevel(it, range.first, range.last) }

    private companion object {
        const val TAG = "ScreenBrightness"
        const val SETTING_MIN = 1
        const val SETTING_MAX = 255

        fun settingRange(): IntRange {
            val minimum = systemInt("config_screenBrightnessSettingMinimum") ?: systemFloatSetting("config_screenBrightnessSettingMinimumFloat")
            val maximum = systemInt("config_screenBrightnessSettingMaximum") ?: systemFloatSetting("config_screenBrightnessSettingMaximumFloat")
            val low = (minimum ?: SETTING_MIN).coerceIn(SETTING_MIN, SETTING_MAX - 1)
            val high = (maximum ?: SETTING_MAX).coerceIn(low + 1, SETTING_MAX)
            return low..high
        }

        @SuppressLint("DiscouragedApi")
        private fun systemInt(name: String): Int? =
            runCatching {
                val resources = Resources.getSystem()
                val id = resources.getIdentifier(name, "integer", "android")
                if (id == 0) null else resources.getInteger(id)
            }.getOrNull()?.takeIf { it in SETTING_MIN..SETTING_MAX }

        @SuppressLint("DiscouragedApi")
        private fun systemFloatSetting(name: String): Int? =
            runCatching {
                val resources = Resources.getSystem()
                val id = resources.getIdentifier(name, "dimen", "android")
                if (id == 0) null else resources.getFloat(id)
            }.getOrNull()
                ?.takeIf { it in 0f..1f }
                ?.let { (it * (SETTING_MAX - 1) + 1).toInt() }
    }
}
