package com.hooandee.colores.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object WriteSettingsPermission {
    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    fun createGrantIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )
}
