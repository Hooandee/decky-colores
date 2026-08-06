package com.hooandee.colores.effects

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hooandee.colores.control.LightingPreferences

class EffectsBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (LightingPreferences(context).shouldRestoreInBackground()) EffectsService.restore(context)
    }
}
