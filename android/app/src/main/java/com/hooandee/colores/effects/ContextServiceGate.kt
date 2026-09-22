package com.hooandee.colores.effects

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.hooandee.colores.control.ServiceGate
import com.hooandee.colores.control.ServiceOwner

class ServiceOwnerLease(
    private val onStart: () -> Boolean,
    private val onStop: () -> Unit,
) {
    private enum class Phase {
        IDLE,
        STARTING,
        RUNNING,
    }

    private val owners = mutableSetOf<ServiceOwner>()
    private var phase = Phase.IDLE

    @Synchronized
    fun setRequired(
        owner: ServiceOwner,
        required: Boolean,
    ) {
        if (required) {
            if (owner in owners) return
            if (ensureStarted()) owners += owner
        } else {
            if (!owners.remove(owner)) return
            if (owners.isEmpty() && phase == Phase.RUNNING) {
                phase = Phase.IDLE
                onStop()
            }
        }
    }

    @get:Synchronized
    val active: Boolean
        get() = phase != Phase.IDLE

    @Synchronized
    fun hasOwners(): Boolean = owners.isNotEmpty()

    @Synchronized
    fun onServiceStarted() {
        phase = Phase.RUNNING
    }

    @Synchronized
    fun releaseIfUnowned(): Boolean {
        if (owners.isNotEmpty()) {
            phase = Phase.RUNNING
            return false
        }
        phase = Phase.IDLE
        return true
    }

    @Synchronized
    fun onServiceStopped() {
        phase = Phase.IDLE
        owners -= ServiceOwner.CAPTURE
        if (owners.isNotEmpty() && !ensureStarted()) owners.clear()
    }

    private fun ensureStarted(): Boolean {
        if (phase != Phase.IDLE) return true
        if (!onStart()) return false
        phase = Phase.STARTING
        return true
    }
}

class ContextServiceGate(
    private val context: Context,
) : ServiceGate {
    private val lease =
        ServiceOwnerLease(
            onStart = ::startService,
            onStop = ::stopService,
        )

    val active: Boolean
        get() = lease.active

    override fun start() = setRequired(ServiceOwner.EFFECTS, true)

    override fun stop() = setRequired(ServiceOwner.EFFECTS, false)

    override fun setRequired(
        owner: ServiceOwner,
        required: Boolean,
    ) = lease.setRequired(owner, required)

    fun hasOwners(): Boolean = lease.hasOwners()

    fun onServiceStarted() {
        Log.d(TAG, "started")
        lease.onServiceStarted()
    }

    fun releaseIfUnowned(): Boolean = lease.releaseIfUnowned().also { if (it) Log.d(TAG, "released") }

    fun onServiceStopped() {
        Log.d(TAG, "stopped")
        lease.onServiceStopped()
    }

    private fun startService(): Boolean {
        Log.d(TAG, "start")
        val intent = Intent(context, EffectsService::class.java)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        }.onFailure { Log.w(TAG, "start refused", it) }.getOrDefault(false)
    }

    private fun stopService() {
        Log.d(TAG, "stop")
        runCatching { context.stopService(Intent(context, EffectsService::class.java)) }
    }

    private companion object {
        const val TAG = "ColoresServiceGate"
    }
}
