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
    CHANNEL_NODES,
    COMPOSITE,
}

data class SysfsRgbDescriptor(
    val nodePath: String,
    val zones: Int,
    val maxBrightness: Int,
    val kind: SysfsColorKind,
    val multiIndex: List<String> = emptyList(),
    val channelNodes: List<String> = emptyList(),
    val members: List<SysfsRgbDescriptor> = emptyList(),
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

    private val colorPaths = SysfsRgbFrames.colorPaths(descriptor)
    private val brightnessPath = SysfsRgbFrames.brightnessPaths(descriptor).firstOrNull()

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

    override suspend fun close() = writer.close()

    override fun invalidate() = Unit

    private fun writeState(state: LedState): Boolean {
        var succeeded = true
        SysfsRgbFrames.writes(descriptor, state.zoneColors, state.brightness, state.power).forEach { (path, value) ->
            if (!access.write(path, value)) succeeded = false
        }
        return succeeded
    }

    private fun readBrightnessPercent(): Int {
        val path = brightnessPath ?: return 100
        val raw = access.read(path)?.toIntOrNull() ?: return 100
        if (descriptor.maxBrightness <= 0) return 100
        return ((raw.toDouble() / descriptor.maxBrightness) * 100).roundToInt().coerceIn(0, 100)
    }

    private companion object {
        const val WRITE_INTERVAL_MS = 80L
    }
}
