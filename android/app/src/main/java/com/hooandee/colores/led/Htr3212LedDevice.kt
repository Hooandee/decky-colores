package com.hooandee.colores.led

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class Htr3212LedDevice internal constructor(
    private val descriptor: SettingsProviderDescriptor,
    private val store: SystemSettingsStore,
    private val executor: PServerCommandExecutor,
    private val scope: CoroutineScope,
    private val settleVendor: suspend () -> Unit,
) : LedDevice {
    constructor(
        context: Context,
        descriptor: SettingsProviderDescriptor,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        executor: PServerCommandExecutor = AndroidPServerCommandExecutor(),
    ) : this(
        descriptor = descriptor,
        store = PServerSystemSettingsStore(context, executor),
        executor = executor,
        scope = scope,
        settleVendor = { delay(VENDOR_SETTLE_MS) },
    )

    private val hardware = descriptor.htr3212
    private val vendorDescriptor = descriptor.copy(driver = "settings_provider", zones = STICKS, htr3212 = null)
    private val writer = ConflatedLedWriter(scope, WRITE_INTERVAL_MS, write = ::writeState)

    private val cacheLock = Any()
    private var cacheGeneration = 0L
    private var cachedVendorState: LedState? = null
    private var cachedLeft: List<RgbColor>? = null
    private var cachedRight: List<RgbColor>? = null
    private var latestRequestedState: LedState? = null
    private var directReassertToken = 0L

    override val available: Boolean
        get() =
            descriptor.driver == "htr3212" &&
                descriptor.colorFormat == "argb_hex_csv" &&
                descriptor.zones == TOTAL_ZONES &&
                hardware != null &&
                store.available &&
                executor.available

    override val supportsPerZone: Boolean = true

    override suspend fun readState(): LedState {
        val vendorState =
            SettingsProviderCodec.decode(
                colors = store.get(descriptor.colorKey),
                brightness = store.get(descriptor.brightnessKey),
                power = descriptor.enableKeys.mapNotNull(store::get).joinToString(",").ifBlank { null },
                descriptor = vendorDescriptor,
            )
        val expanded =
            vendorState.copy(
                zoneColors =
                    List(ZONES_PER_STICK) { vendorState.zoneColors[0] } +
                        List(ZONES_PER_STICK) { vendorState.zoneColors[1] },
            )
        synchronized(cacheLock) {
            cachedVendorState = vendorState
            cachedLeft = null
            cachedRight = null
            directReassertToken += 1
        }
        return expanded
    }

    override suspend fun applyZones(
        colors: List<RgbColor>,
        brightness: Int,
        power: Boolean,
    ): Boolean {
        val state = LedState(colors.fitHtrZones(), brightness.coerceIn(0, 100), power)
        synchronized(cacheLock) { latestRequestedState = state }
        return writer.submit(state)
    }

    override suspend fun applySolid(
        color: RgbColor,
        brightness: Int,
        power: Boolean,
    ): Boolean = applyZones(List(TOTAL_ZONES) { color }, brightness, power)

    override fun invalidate() {
        synchronized(cacheLock) {
            cacheGeneration += 1
            cachedVendorState = null
            cachedLeft = null
            cachedRight = null
            latestRequestedState = null
            directReassertToken += 1
        }
    }

    private suspend fun writeState(state: LedState): Boolean {
        val hardware = hardware ?: return false
        val cache = cacheSnapshot()
        synchronized(cacheLock) {
            if (cacheGeneration == cache.generation) latestRequestedState = state
        }
        val vendorState = state.toVendorState()
        val vendorResult = writeVendorState(vendorState, cache.vendorState)
        if (!vendorResult.succeeded) return false
        publishVendorState(cache.generation, vendorState)
        if (!state.power) {
            publishDirectState(cache.generation, null, null)
            return true
        }
        if (vendorResult.changed) settleVendor()

        val left = state.zoneColors.take(ZONES_PER_STICK)
        val right = state.zoneColors.drop(ZONES_PER_STICK).take(ZONES_PER_STICK)
        val previousLeft = cache.left.takeUnless { vendorResult.changed }
        val previousRight = cache.right.takeUnless { vendorResult.changed }
        val leftSucceeded = writeStick(hardware.leftBus, hardware.address, left, hardware.leftOrder, previousLeft)
        val rightSucceeded = writeStick(hardware.rightBus, hardware.address, right, hardware.rightOrder, previousRight)
        publishDirectState(
            generation = cache.generation,
            left = left.takeIf { leftSucceeded },
            right = right.takeIf { rightSucceeded },
        )
        val succeeded = leftSucceeded && rightSucceeded
        if (vendorResult.changed) scheduleDirectReassert(cache.generation)
        return succeeded
    }

    private fun scheduleDirectReassert(generation: Long) {
        val token = synchronized(cacheLock) { ++directReassertToken }
        scope.launch {
            delay(VENDOR_REASSERT_MS)
            val latest =
                synchronized(cacheLock) {
                    if (cacheGeneration != generation || directReassertToken != token) return@synchronized null
                    cachedLeft = null
                    cachedRight = null
                    latestRequestedState
                } ?: return@launch
            writer.submit(latest)
        }
    }

    private fun writeVendorState(
        state: LedState,
        previous: LedState?,
    ): VendorWriteResult {
        var succeeded = true
        var changed = false
        if (previous?.zoneColors != state.zoneColors) {
            changed = true
            if (!store.put(descriptor.colorKey, SettingsProviderCodec.encodeColors(state.zoneColors, STICKS))) {
                succeeded = false
            }
        }
        if (previous?.brightness != state.brightness) {
            changed = true
            if (!store.put(descriptor.brightnessKey, SettingsProviderCodec.encodeBrightness(state.brightness, vendorDescriptor))) {
                succeeded = false
            }
        }
        if (previous?.power != state.power) {
            changed = true
            val values = SettingsProviderCodec.encodePower(state.power, STICKS)
            descriptor.enableKeys.zip(values).forEach { (key, value) ->
                if (!store.put(key, value)) succeeded = false
            }
        }
        return VendorWriteResult(succeeded, changed)
    }

    private fun cacheSnapshot(): CacheSnapshot =
        synchronized(cacheLock) {
            CacheSnapshot(
                generation = cacheGeneration,
                vendorState = cachedVendorState,
                left = cachedLeft,
                right = cachedRight,
            )
        }

    private fun publishVendorState(
        generation: Long,
        state: LedState,
    ) {
        synchronized(cacheLock) {
            if (cacheGeneration == generation) cachedVendorState = state
        }
    }

    private fun publishDirectState(
        generation: Long,
        left: List<RgbColor>?,
        right: List<RgbColor>?,
    ) {
        synchronized(cacheLock) {
            if (cacheGeneration != generation) return
            cachedLeft = left
            cachedRight = right
        }
    }

    private fun writeStick(
        bus: Int,
        address: Int,
        colors: List<RgbColor>,
        logicalToDriverOrder: List<Int>,
        previous: List<RgbColor>?,
    ): Boolean =
        Htr3212Command.build(bus, address, colors, logicalToDriverOrder, previous, hardware?.rgbStartRegister ?: 0x01)
            ?.let(executor::execute)
            ?: true

    private fun LedState.toVendorState(): LedState =
        copy(zoneColors = listOf(zoneColors.first(), zoneColors[ZONES_PER_STICK]))

    private data class VendorWriteResult(
        val succeeded: Boolean,
        val changed: Boolean,
    )

    private data class CacheSnapshot(
        val generation: Long,
        val vendorState: LedState?,
        val left: List<RgbColor>?,
        val right: List<RgbColor>?,
    )

    private companion object {
        const val STICKS = 2
        const val ZONES_PER_STICK = 4
        const val TOTAL_ZONES = STICKS * ZONES_PER_STICK
        const val WRITE_INTERVAL_MS = 80L
        const val VENDOR_SETTLE_MS = 120L
        const val VENDOR_REASSERT_MS = 1_200L
    }
}

private fun List<RgbColor>.fitHtrZones(): List<RgbColor> {
    val fallback = firstOrNull() ?: RgbColor(255, 255, 255)
    return List(8) { index -> getOrNull(index) ?: fallback }
}
