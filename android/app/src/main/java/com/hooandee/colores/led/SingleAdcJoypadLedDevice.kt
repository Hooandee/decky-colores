package com.hooandee.colores.led

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

data class SingleAdcJoypadDescriptor(
    val basePath: String = DEFAULT_BASE_PATH,
) : LedDescriptor {
    companion object {
        const val DEFAULT_BASE_PATH = "/sys/bus/platform/devices/singleadc-joypad"
    }
}

class SingleAdcJoypadLedDevice internal constructor(
    private val descriptor: SingleAdcJoypadDescriptor,
    private val access: SysfsAccess,
    scope: CoroutineScope,
) : LedDevice {
    constructor(
        descriptor: SingleAdcJoypadDescriptor,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ) : this(descriptor, FileSysfsAccess, scope)

    private val writer = ConflatedLedWriter(scope, WRITE_INTERVAL_MS, write = ::writeState)

    override val available: Boolean
        get() = access.canWrite(node("custum_rgb_r")) && access.canWrite(node("led_set"))

    override val supportsPerZone: Boolean = false

    override suspend fun readState(): LedState {
        val color =
            RgbColor(
                red = readInt("custum_rgb_r", 0),
                green = readInt("custum_rgb_g", 0),
                blue = readInt("custum_rgb_b", 0),
            )
        return LedState(
            zoneColors = listOf(color),
            brightness = readInt("led_level", 100).coerceIn(0, 100),
            power = readInt("led_switch", 1) != 0,
        )
    }

    override suspend fun applyZones(
        colors: List<RgbColor>,
        brightness: Int,
        power: Boolean,
    ): Boolean = writer.submit(LedState(listOf(colors.firstOrNull() ?: RgbColor(255, 255, 255)), brightness.coerceIn(0, 100), power))

    override suspend fun applySolid(
        color: RgbColor,
        brightness: Int,
        power: Boolean,
    ): Boolean = applyZones(listOf(color), brightness, power)

    override fun invalidate() = Unit

    private fun writeState(state: LedState): Boolean {
        val color = state.zoneColors.first()
        var succeeded = true
        fun put(
            name: String,
            value: Int,
        ) {
            if (!access.write(node(name), "$value\n")) succeeded = false
        }
        if (state.power) {
            put("custum_rgb_r", color.red.coerceIn(0, 255))
            put("custum_rgb_g", color.green.coerceIn(0, 255))
            put("custum_rgb_b", color.blue.coerceIn(0, 255))
            put("led_level", state.brightness)
            put("led_mode", STATIC_MODE)
            put("led_switch", 1)
        } else {
            put("led_switch", 0)
        }
        put("led_set", 1)
        return succeeded
    }

    private fun readInt(
        name: String,
        fallback: Int,
    ): Int = access.read(node(name))?.trim()?.toIntOrNull() ?: fallback

    private fun node(name: String): String = "${descriptor.basePath}/$name"

    private companion object {
        const val WRITE_INTERVAL_MS = 80L
        const val STATIC_MODE = 1
    }
}
