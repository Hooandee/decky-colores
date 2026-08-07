package com.hooandee.colores.effects

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.hooandee.colores.control.ServiceGate
import com.hooandee.colores.control.ServiceOwner
import java.util.concurrent.atomic.AtomicBoolean

class ServiceOwnerLease(
    private val onStart: () -> Boolean,
    private val onStop: () -> Unit,
) {
    private val owners = mutableSetOf<ServiceOwner>()

    @Synchronized
    fun setRequired(
        owner: ServiceOwner,
        required: Boolean,
    ) {
        if (required) {
            if (owner in owners) return
            if (owners.isNotEmpty() || onStart()) owners += owner
        } else {
            if (!owners.remove(owner)) return
            if (owners.isEmpty()) onStop()
        }
    }
}

class ContextServiceGate(
    private val context: Context,
) : ServiceGate {
    private val running = AtomicBoolean(false)
    private val lease =
        ServiceOwnerLease(
            onStart = ::startService,
            onStop = ::stopService,
        )

    override fun start() = setRequired(ServiceOwner.EFFECTS, true)

    override fun stop() = setRequired(ServiceOwner.EFFECTS, false)

    override fun setRequired(
        owner: ServiceOwner,
        required: Boolean,
    ) = lease.setRequired(owner, required)

    private fun startService(): Boolean {
        if (!running.compareAndSet(false, true)) return true
        Log.d(TAG, "start")
        val intent = Intent(context, EffectsService::class.java)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        }.onFailure { running.set(false) }.getOrDefault(false)
    }

    private fun stopService() {
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
