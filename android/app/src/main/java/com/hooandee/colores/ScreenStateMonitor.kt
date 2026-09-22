package com.hooandee.colores

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun interactiveAfter(
    action: String?,
    current: Boolean,
): Boolean =
    when (action) {
        Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> true
        Intent.ACTION_SCREEN_OFF -> false
        else -> current
    }

internal fun reassertsLighting(action: String?): Boolean = action == Intent.ACTION_SCREEN_ON

class ScreenStateMonitor(
    private val context: Context,
    private val onScreenOn: () -> Unit,
) {
    private val mutableInteractive = MutableStateFlow(true)
    val interactive: StateFlow<Boolean> = mutableInteractive.asStateFlow()

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                val action = intent?.action
                mutableInteractive.value = interactiveAfter(action, mutableInteractive.value)
                if (reassertsLighting(action)) onScreenOn()
            }
        }

    @Suppress("DEPRECATION")
    fun register() {
        mutableInteractive.value = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }
}
