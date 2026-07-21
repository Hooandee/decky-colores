package com.hooandee.colores.led

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import kotlin.math.roundToInt

enum class SysfsColorKind {
    MULTI_INTENSITY_DECIMAL,
    MULTI_INTENSITY_HEX,
    RGB_CHANNELS,
}

data class SysfsRgbDescriptor(
    val nodePath: String,
    val zones: Int,
    val maxBrightness: Int,
    val kind: SysfsColorKind,
) : LedDescriptor

interface SysfsAccess {
    fun read(path: String): String?

    fun exists(path: String): Boolean

    fun canWrite(path: String): Boolean

    fun write(
        path: String,
        value: String,
    ): Boolean
}

internal object FileSysfsAccess : SysfsAccess {
    override fun read(path: String): String? = runCatching { File(path).readText().trim() }.getOrNull()

    override fun exists(path: String): Boolean = runCatching { File(path).exists() }.getOrDefault(false)

    override fun canWrite(path: String): Boolean = runCatching { File(path).canWrite() }.getOrDefault(false)

    override fun write(
        path: String,
        value: String,
    ): Boolean = runCatching { File(path).writeText(value); true }.getOrDefault(false)
}

class SysfsRgbDevice internal constructor(
    private val descriptor: SysfsRgbDescriptor,
    private val access: SysfsAccess,
    scope: CoroutineScope,
) : LedDevice {
    constructor(
        descriptor: SysfsRgbDescriptor,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ) : this(descriptor, FileSysfsAccess, scope)

    private val brightnessPath = "${descriptor.nodePath}/brightness"
    private val colorPaths =
        when (descriptor.kind) {
            SysfsColorKind.RGB_CHANNELS ->
                listOf("red", "green", "blue").map { "${descriptor.nodePath}/$it" }
            else -> listOf("${descriptor.nodePath}/multi_intensity")
        }

    private val writer = ConflatedLedWriter(scope, WRITE_INTERVAL_MS, write = ::writeState)

    override val available: Boolean
        get() = descriptor.zones > 0 && colorPaths.all(access::canWrite)

    override val supportsPerZone: Boolean
        get() = descriptor.zones > 1

    override suspend fun readState(): LedState =
        LedState(
            zoneColors = List(descriptor.zones) { RgbColor(0, 0, 0) },
            brightness = readBrightnessPercent(),
            power = true,
        )

    override suspend fun applyZones(
        colors: List<RgbColor>,
        brightness: Int,
        power: Boolean,
    ): Boolean = writer.submit(LedState(colors.fitZones(descriptor.zones), brightness.coerceIn(0, 100), power))

    override suspend fun applySolid(
        color: RgbColor,
        brightness: Int,
        power: Boolean,
    ): Boolean = applyZones(List(descriptor.zones) { color }, brightness, power)

    override fun invalidate() = Unit

    private fun writeState(state: LedState): Boolean {
        val colors = if (state.power) state.zoneColors else List(descriptor.zones) { RgbColor(0, 0, 0) }
        val colorWritten =
            when (descriptor.kind) {
                SysfsColorKind.RGB_CHANNELS -> writeChannels(colors.first())
                SysfsColorKind.MULTI_INTENSITY_DECIMAL ->
                    access.write(colorPaths.first(), colors.flatMap { listOf(it.red, it.green, it.blue) }.joinToString(" "))
                SysfsColorKind.MULTI_INTENSITY_HEX ->
                    access.write(colorPaths.first(), colors.joinToString(" ") { "0x%06X".format(packed(it)) })
            }
        val brightnessValue = if (state.power) scaleBrightness(state.brightness) else 0
        val brightnessWritten = access.write(brightnessPath, brightnessValue.toString())
        return colorWritten && brightnessWritten
    }

    private fun writeChannels(color: RgbColor): Boolean {
        val scaled = { channel: Int -> ((channel / 255.0) * descriptor.maxBrightness).roundToInt().coerceIn(0, descriptor.maxBrightness) }
        return access.write(colorPaths[0], scaled(color.red).toString()) &&
            access.write(colorPaths[1], scaled(color.green).toString()) &&
            access.write(colorPaths[2], scaled(color.blue).toString())
    }

    private fun scaleBrightness(percent: Int): Int =
        ((percent.coerceIn(0, 100) / 100.0) * descriptor.maxBrightness).roundToInt().coerceIn(0, descriptor.maxBrightness)

    private fun readBrightnessPercent(): Int {
        val raw = access.read(brightnessPath)?.toIntOrNull() ?: return 100
        if (descriptor.maxBrightness <= 0) return 100
        return ((raw.toDouble() / descriptor.maxBrightness) * 100).roundToInt().coerceIn(0, 100)
    }

    private fun packed(color: RgbColor): Int =
        (color.red.coerceIn(0, 255) shl 16) or (color.green.coerceIn(0, 255) shl 8) or color.blue.coerceIn(0, 255)

    private companion object {
        const val WRITE_INTERVAL_MS = 80L
    }
}
