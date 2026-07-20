package com.hooandee.colores.effects

import android.app.Service
import android.content.Intent
import android.os.IBinder

class EffectsService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    internal suspend fun runLoop(): Nothing =
        TODO("Run the foreground effect loop without applying LED frames yet")
}
