package com.hooandee.colores.led

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import kotlin.math.roundToInt

/**
 * Single-zone RGB behind three separate sysfs brightness channels
 * (`red`/`green`/`blue`), the aw2013-style surface the design anticipated. On the
 * AYN Thor these nodes are world-writable, so no root is required. The channel
 * writes go through a seam so the device is unit-testable without a filesystem.
 */
class SysfsPerChannelRgbDevice(
    private val descriptor: SettingsProviderDescriptor,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val hardware: SysfsRgbDescriptor = requireNotNull(descriptor.sysfs),
    private val writeChannel: (String, Int) -> Boolean = { path, value ->
        runCatching { File(path).writeText(value.toString()); true }.getOrDefault(false)
    },
    private val readChannel: (String) -> Int? = { path ->
        runCatching { File(path).readText().trim().toInt() }.getOrNull()
    },
    private val channelWritable: (String) -> Boolean = { path ->
        runCatching { File(path).canWrite() }.getOrDefault(false)
    },
) : LedDevice {
    @Volatile
    private var cachedState: LedState? = null

    private val writer = ConflatedLedWriter(scope, WRITE_INTERVAL_MS, write = ::writeState)

    override val available: Boolean
        get() =
            descriptor.driver == "sysfs_rgb" &&
                hardware.maxBrightness > 0 &&
                channelWritable(path(hardware.red)) &&
                channelWritable(path(hardware.green)) &&
                channelWritable(path(hardware.blue))

    override val supportsPerZone: Boolean = false

    override val recommendedFrameIntervalMs: Long = 33L

    override suspend fun readState(): LedState {
        val red = readChannel(path(hardware.red)) ?: 0
        val green = readChannel(path(hardware.green)) ?: 0
        val blue = readChannel(path(hardware.blue)) ?: 0
        val color = RgbColor(toByte(red), toByte(green), toByte(blue))
        val state = LedState(listOf(color), 100, red > 0 || green > 0 || blue > 0)
        cachedState = state
        return state
    }

    override suspend fun applyZones(
        colors: List<RgbColor>,
        brightness: Int,
        power: Boolean,
    ): Boolean {
        val color = colors.firstOrNull() ?: RgbColor(0, 0, 0)
        return writer.submit(LedState(listOf(color), brightness.coerceIn(0, 100), power))
    }

    override suspend fun applySolid(
        color: RgbColor,
        brightness: Int,
        power: Boolean,
    ): Boolean = applyZones(listOf(color), brightness, power)

    override fun invalidate() {
        cachedState = null
    }

    private fun writeState(state: LedState): Boolean {
        val previous = cachedState
        if (previous == state) return true
        val color = state.zoneColors.firstOrNull() ?: RgbColor(0, 0, 0)
        val scale = if (state.power) state.brightness.coerceIn(0, 100) / 100.0 else 0.0
        val red = scaled(color.red, scale)
        val green = scaled(color.green, scale)
        val blue = scaled(color.blue, scale)
        val ok =
            writeChannel(path(hardware.red), red) &&
                writeChannel(path(hardware.green), green) &&
                writeChannel(path(hardware.blue), blue)
        cachedState = state.takeIf { ok }
        return ok
    }

    private fun scaled(
        channel: Int,
        scale: Double,
    ): Int = (channel.coerceIn(0, 255) / 255.0 * hardware.maxBrightness * scale).roundToInt().coerceIn(0, hardware.maxBrightness)

    private fun toByte(raw: Int): Int = (raw.coerceIn(0, hardware.maxBrightness) * 255.0 / hardware.maxBrightness).roundToInt().coerceIn(0, 255)

    private fun path(node: String): String = "${hardware.basePath.trimEnd('/')}/$node/brightness"

    private companion object {
        const val WRITE_INTERVAL_MS = 33L
    }
}
