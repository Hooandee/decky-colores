package com.hooandee.colores.effects

import android.content.Context
import android.content.Intent
import android.os.Build
import com.hooandee.colores.control.ServiceGate
import java.util.concurrent.atomic.AtomicBoolean

class ContextServiceGate(
    private val context: Context,
) : ServiceGate {
    private val running = AtomicBoolean(false)

    override fun start() {
        if (!running.compareAndSet(false, true)) return
        val intent = Intent(context, EffectsService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onFailure { running.set(false) }
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { context.stopService(Intent(context, EffectsService::class.java)) }
    }
}
