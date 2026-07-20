package com.hooandee.colores.sensor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class BatteryReading(
    val levelPercent: Int?,
    val charging: Boolean,
    val present: Boolean,
)

interface BatterySource {
    fun read(): BatteryReading
}

class AndroidBatterySource(
    private val context: Context,
) : BatterySource {
    override fun read(): BatteryReading {
        val status =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val present = status?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) ?: true
        val plugged = status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val state = status?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging =
            plugged != 0 ||
                state == BatteryManager.BATTERY_STATUS_CHARGING ||
                state == BatteryManager.BATTERY_STATUS_FULL
        val level =
            if (!present) {
                null
            } else {
                val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val raw = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                when {
                    raw >= 0 && scale > 0 -> (raw * 100 / scale).coerceIn(0, 100)
                    else ->
                        (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
                            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                            ?.takeIf { it in 0..100 }
                }
            }
        return BatteryReading(levelPercent = level, charging = charging, present = present)
    }
}
