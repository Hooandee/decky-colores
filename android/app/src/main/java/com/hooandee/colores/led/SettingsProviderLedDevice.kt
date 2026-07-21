package com.hooandee.colores.led

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface LedDescriptor

data class SettingsProviderDescriptor(
    val driver: String,
    val transport: String = "direct",
    val colorKey: String,
    val colorFormat: String,
    val brightnessKey: String,
    val brightnessRange: ClosedFloatingPointRange<Float>,
    val enableKeys: List<String>,
    val zones: Int,
    val requiresPermission: String?,
    val vendorService: String,
    val htr3212: Htr3212Descriptor? = null,
) : LedDescriptor

data class Htr3212Descriptor(
    val leftBus: Int,
    val rightBus: Int,
    val address: Int,
    val leftOrder: List<Int>,
    val rightOrder: List<Int>,
    val rgbStartRegister: Int = 0x01,
)

class SettingsProviderLedDevice internal constructor(
    private val descriptor: SettingsProviderDescriptor,
    private val store: SystemSettingsStore,
    scope: CoroutineScope,
) : LedDevice {
    constructor(
        context: Context,
        descriptor: SettingsProviderDescriptor,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ) : this(descriptor, buildSystemSettingsStore(context, descriptor), scope)

    @Volatile
    private var cachedState: LedState? = null

    private val writer = ConflatedLedWriter(scope, WRITE_INTERVAL_MS, write = ::writeState)

    override val available: Boolean
        get() =
            descriptor.driver == "settings_provider" &&
                descriptor.colorFormat == "argb_hex_csv" &&
                descriptor.zones > 0 &&
                store.available

    override val supportsPerZone: Boolean
        get() = descriptor.zones > 1

    override suspend fun readState(): LedState {
        val state =
            SettingsProviderCodec.decode(
                colors = store.get(descriptor.colorKey),
                brightness = store.get(descriptor.brightnessKey),
                power = descriptor.enableKeys.mapNotNull(store::get).joinToString(",").ifBlank { null },
                descriptor = descriptor,
            )
        cachedState = state
        return state
    }

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

    override fun invalidate() {
        cachedState = null
    }

    private suspend fun writeState(state: LedState): Boolean {
        val previous = cachedState
        var succeeded = true
        if (previous?.zoneColors != state.zoneColors) {
            if (!store.put(descriptor.colorKey, SettingsProviderCodec.encodeColors(state.zoneColors, descriptor.zones))) {
                succeeded = false
            }
        }
        if (previous?.brightness != state.brightness) {
            if (!store.put(descriptor.brightnessKey, SettingsProviderCodec.encodeBrightness(state.brightness, descriptor))) {
                succeeded = false
            }
        }
        if (previous?.power != state.power) {
            val values = SettingsProviderCodec.encodePower(state.power, descriptor.zones)
            descriptor.enableKeys.zip(values).forEach { (key, value) ->
                if (!store.put(key, value)) succeeded = false
            }
        }
        cachedState = state.takeIf { succeeded }
        return succeeded
    }

    private companion object {
        const val WRITE_INTERVAL_MS = 80L
    }
}

interface SystemSettingsStore {
    val available: Boolean

    fun get(key: String): String?

    fun put(key: String, value: String): Boolean
}

private class AndroidSystemSettingsStore(
    context: Context,
) : SystemSettingsStore {
    private val resolver = context.contentResolver

    override val available: Boolean = true

    override fun get(key: String): String? = Settings.System.getString(resolver, key)

    override fun put(key: String, value: String): Boolean = Settings.System.putString(resolver, key, value)
}

private fun buildSystemSettingsStore(
    context: Context,
    descriptor: SettingsProviderDescriptor,
): SystemSettingsStore =
    when (descriptor.transport) {
        "pserver" -> PServerSystemSettingsStore(context)
        else -> AndroidSystemSettingsStore(context)
    }

internal object SettingsProviderCodec {
    fun encodeColors(colors: List<RgbColor>, zones: Int): String =
        colors.fitZones(zones).joinToString(",") { color ->
            "#FF%02X%02X%02X".format(
                color.red.coerceIn(0, 255),
                color.green.coerceIn(0, 255),
                color.blue.coerceIn(0, 255),
            )
        }

    fun encodeBrightness(
        brightness: Int,
        descriptor: SettingsProviderDescriptor,
    ): String {
        val normalized = brightness.coerceIn(0, 100) / 100f
        val mapped = descriptor.brightnessRange.start + normalized * (descriptor.brightnessRange.endInclusive - descriptor.brightnessRange.start)
        return mapped.toString()
    }

    fun encodePower(
        power: Boolean,
        zones: Int,
    ): List<String> {
        val value = if (power) "1" else "0"
        return listOf(List(zones) { value }.joinToString(","), value, value)
    }

    fun decode(
        colors: String?,
        brightness: String?,
        power: String?,
        descriptor: SettingsProviderDescriptor,
    ): LedState {
        val parsedColors =
            colors
                ?.split(',')
                ?.mapNotNull(::parseColor)
                .orEmpty()
                .fitZones(descriptor.zones)
        val rawBrightness = brightness?.toFloatOrNull()
        val span = descriptor.brightnessRange.endInclusive - descriptor.brightnessRange.start
        val percent =
            if (rawBrightness == null || span <= 0f) {
                100
            } else {
                (((rawBrightness - descriptor.brightnessRange.start) / span) * 100f).roundToInt().coerceIn(0, 100)
            }
        return LedState(
            zoneColors = parsedColors,
            brightness = percent,
            power = power?.split(',')?.any { it.trim() == "1" } ?: true,
        )
    }

    private fun parseColor(value: String): RgbColor? {
        val hex = value.trim().removePrefix("#")
        if (hex.length != 8 || !hex.startsWith("FF", ignoreCase = true)) return null
        return runCatching {
            RgbColor(
                red = hex.substring(2, 4).toInt(16),
                green = hex.substring(4, 6).toInt(16),
                blue = hex.substring(6, 8).toInt(16),
            )
        }.getOrNull()
    }
}

internal class ConflatedLedWriter<T>(
    scope: CoroutineScope,
    private val intervalMs: Long,
    private val retryIntervalMs: Long = 500L,
    write: suspend (T) -> Boolean,
) {
    private val channel = Channel<T>(Channel.CONFLATED)

    init {
        scope.launch {
            for (value in channel) {
                var pending = value
                while (true) {
                    val succeeded = runCatching { write(pending) }.getOrDefault(false)
                    delay(if (succeeded) intervalMs else retryIntervalMs)
                    val newer = channel.tryReceive().getOrNull()
                    if (newer != null) {
                        pending = newer
                    } else if (succeeded) {
                        break
                    }
                }
            }
        }
    }

    fun submit(value: T): Boolean = channel.trySend(value).isSuccess
}

private fun List<RgbColor>.fitZones(zones: Int): List<RgbColor> {
    val fallback = firstOrNull() ?: RgbColor(255, 255, 255)
    return List(zones.coerceAtLeast(1)) { index -> getOrNull(index) ?: fallback }
}
