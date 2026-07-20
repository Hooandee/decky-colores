package com.hooandee.colores.ui

import android.content.Context

internal class LedPreviewPreferences(
    private val read: (String) -> Boolean?,
    private val write: (String, Boolean) -> Unit,
) {
    constructor(context: Context) : this(
        read = { key ->
            val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            if (preferences.contains(key)) preferences.getBoolean(key, false) else null
        },
        write = { key, enabled ->
            context
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key, enabled)
                .apply()
        },
    )

    fun isEnabled(deviceId: String): Boolean = read(key(deviceId)) ?: false

    fun setEnabled(
        deviceId: String,
        enabled: Boolean,
    ) = write(key(deviceId), enabled)

    private fun key(deviceId: String) = "enabled:$deviceId"

    private companion object {
        const val FILE_NAME = "led_preview"
    }
}
