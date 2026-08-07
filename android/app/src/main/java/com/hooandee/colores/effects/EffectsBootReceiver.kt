package com.hooandee.colores.effects

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hooandee.colores.apps.UsageAccess
import com.hooandee.colores.control.LightingPreferences
import com.hooandee.colores.profiles.LightingProfileStore

internal fun shouldRestoreRuntimeAtBoot(
    lightingRequired: Boolean,
    automationEnabled: Boolean,
    usageAccessGranted: Boolean,
): Boolean = lightingRequired || automationEnabled && usageAccessGranted

class EffectsBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (
            shouldRestoreRuntimeAtBoot(
                lightingRequired = LightingPreferences(context).shouldRestoreInBackground(),
                automationEnabled = LightingProfileStore(context).isAutomationEnabled(),
                usageAccessGranted = UsageAccess(context).isGranted(),
            )
        ) {
            EffectsService.restore(context)
        }
    }
}
