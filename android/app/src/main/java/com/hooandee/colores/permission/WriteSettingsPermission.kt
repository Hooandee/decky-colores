package com.hooandee.colores.permission

import android.content.Context
import android.content.Intent

object WriteSettingsPermission {
    fun canWrite(context: Context): Boolean =
        TODO("Check Settings.System.canWrite for this package")

    fun createGrantIntent(context: Context): Intent =
        TODO("Create Settings.ACTION_MANAGE_WRITE_SETTINGS for this package")
}
