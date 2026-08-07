package com.hooandee.colores.apps

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process

data class RawLaunchableApp(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null,
)

data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null,
)

fun normalizeApps(
    apps: List<RawLaunchableApp>,
    ownPackage: String,
    configured: Set<String> = emptySet(),
): List<LaunchableApp> =
    apps
        .asSequence()
        .filter { it.packageName.isNotBlank() && it.packageName != ownPackage }
        .distinctBy(RawLaunchableApp::packageName)
        .map {
            LaunchableApp(
                packageName = it.packageName,
                label = it.label.trim().ifEmpty { it.packageName },
                icon = it.icon,
            )
        }.sortedWith(
            compareByDescending<LaunchableApp> { it.packageName in configured }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
        ).toList()

fun filterApps(
    apps: List<LaunchableApp>,
    query: String,
): List<LaunchableApp> {
    val needle = query.trim()
    if (needle.isEmpty()) return apps
    return apps.filter {
        it.label.contains(needle, ignoreCase = true) ||
            it.packageName.contains(needle, ignoreCase = true)
    }
}

class LaunchableAppCatalog(
    private val context: Context,
) {
    fun load(configured: Set<String> = emptySet()): List<LaunchableApp> =
        normalizeApps(queryLauncherApps().ifEmpty(::queryPackageManager), context.packageName, configured)

    private fun queryLauncherApps(): List<RawLaunchableApp> =
        runCatching {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            launcherApps.getActivityList(null, Process.myUserHandle()).map {
                RawLaunchableApp(
                    packageName = it.applicationInfo.packageName,
                    label = it.label?.toString().orEmpty(),
                    icon = it.getBadgedIcon(0),
                )
            }
        }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    private fun queryPackageManager(): List<RawLaunchableApp> {
        val manager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return manager.queryIntentActivities(intent, 0).map {
            RawLaunchableApp(
                packageName = it.activityInfo.packageName,
                label = it.loadLabel(manager).toString(),
                icon = runCatching { it.loadIcon(manager) }.getOrNull(),
            )
        }
    }
}
