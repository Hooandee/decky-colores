package com.hooandee.colores.apps

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings

class UsageAccess(
    private val context: Context,
) {
    fun isGranted(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode =
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
}
