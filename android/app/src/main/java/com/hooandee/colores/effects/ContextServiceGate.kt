package com.hooandee.colores.effects

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.hooandee.colores.control.ServiceGate
import java.util.concurrent.atomic.AtomicBoolean

class ContextServiceGate(
    private val context: Context,
) : ServiceGate {
    private val running = AtomicBoolean(false)

    override fun start() {
        if (!running.compareAndSet(false, true)) return
        Log.d(TAG, "start")
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
        Log.d(TAG, "stop")
        runCatching { context.stopService(Intent(context, EffectsService::class.java)) }
    }

    fun onServiceStarted() {
        Log.d(TAG, "started")
        running.set(true)
    }

    fun onServiceStopped() {
        Log.d(TAG, "stopped")
        running.set(false)
    }

    private companion object {
        const val TAG = "ColoresServiceGate"
    }
}
