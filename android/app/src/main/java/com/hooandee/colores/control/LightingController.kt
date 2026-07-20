package com.hooandee.colores.control

import com.hooandee.colores.engine.BandSet
import com.hooandee.colores.engine.ClockRenderer
import com.hooandee.colores.engine.EffectCatalog
import com.hooandee.colores.engine.EffectNeed
import com.hooandee.colores.engine.EffectPalette
import com.hooandee.colores.engine.EffectRenderer
import com.hooandee.colores.engine.IndicatorRenderer
import com.hooandee.colores.engine.PerformanceRenderer
import com.hooandee.colores.engine.Renderer
import com.hooandee.colores.engine.StatusTargets
import com.hooandee.colores.gradient.GradientInterpolator
import com.hooandee.colores.led.LedDevice
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.sensor.BatterySource
import com.hooandee.colores.sensor.PerformanceMetric
import com.hooandee.colores.sensor.PerformanceSource
import com.hooandee.colores.sensor.TemperatureSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppMode {
    COLOR,
    GRADIENT,
    EFFECT,
    BATTERY,
    TEMPERATURE,
    PERFORMANCE,
    CLOCK,
    ;

    val isDynamic: Boolean
        get() = this != COLOR && this != GRADIENT
}

data class LightingIntent(
    val mode: AppMode = AppMode.COLOR,
    val staticColors: List<RgbColor> = listOf(RgbColor(93, 81, 255), RgbColor(93, 81, 255)),
    val solidColor: RgbColor = RgbColor(93, 81, 255),
    val gradientStops: List<RgbColor> = listOf(RgbColor(93, 81, 255), RgbColor(93, 81, 255)),
    val effectId: String = "breathing",
    val speed: Int = 50,
    val brightness: Int = 100,
    val power: Boolean = true,
    val chargerOnly: Boolean = false,
    val batteryBreathe: Boolean = true,
)

data class LightingBinding(
    val deviceId: String,
    val device: LedDevice,
    val zones: Int,
    val catalog: EffectCatalog,
    val bands: BandSet,
    val battery: BatterySource,
    val temperature: TemperatureSource?,
    val performance: PerformanceSource?,
)

data class LightingSnapshot(
    val bound: Boolean = false,
    val deviceId: String? = null,
    val mode: AppMode = AppMode.COLOR,
    val effectId: String = "breathing",
    val speed: Int = 50,
    val brightness: Int = 100,
    val powerRequested: Boolean = true,
    val effectivePower: Boolean = true,
    val chargerOnly: Boolean = false,
    val batteryBreathe: Boolean = true,
    val charging: Boolean = true,
    val batteryPresent: Boolean = true,
    val batteryLevelPercent: Int? = null,
    val temperatureCelsius: Double? = null,
    val temperatureAvailable: Boolean = false,
    val performanceMetric: PerformanceMetric? = null,
    val currentFrame: List<RgbColor> = emptyList(),
)

interface ServiceGate {
    fun start()

    fun stop()
}

object NoopServiceGate : ServiceGate {
    override fun start() {}

    override fun stop() {}
}

/**
 * The single logical owner of an [LedDevice]. Every hardware write flows through
 * here, so nothing else races the device. State mutations and reconciliation run on
 * one command coroutine (single-writer), while the render and watch coroutines only
 * read an immutable [intent] snapshot and hand frames back to the device writer.
 */
class LightingController(
    private val scope: CoroutineScope,
    private val serviceGate: ServiceGate = NoopServiceGate,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val localHour: () -> Double = { defaultLocalHour() },
) {
    private val commands = Channel<Command>(Channel.UNLIMITED)

    @Volatile
    private var intent = LightingIntent()

    @Volatile
    private var charging = true

    @Volatile
    private var batteryLevel: Int? = null

    @Volatile
    private var batteryPresent = true

    private var binding: LightingBinding? = null
    private var renderJob: Job? = null
    private var watchJob: Job? = null
    private var rendererSignature: Pair<AppMode, String>? = null
    private var generation = 0L
    private var temperatureCelsius: Double? = null
    private var lastFrame: List<RgbColor> = emptyList()

    private val mutableSnapshot = MutableStateFlow(LightingSnapshot())
    val snapshot: StateFlow<LightingSnapshot> = mutableSnapshot.asStateFlow()

    init {
        scope.launch {
            for (command in commands) handle(command)
        }
    }

    fun bind(
        binding: LightingBinding,
        intent: LightingIntent,
    ) = send(Command.Bind(binding, intent))

    fun unbind() = send(Command.Unbind)

    /** Force a full hardware resend, e.g. after screen-on when the vendor service
     *  may have flattened the LEDs. Invalidates the device cache so the next write
     *  re-sends every zone. */
    fun reassert() = send(Command.Reassert)

    fun setMode(mode: AppMode) = send(Command.SetMode(mode))

    fun setEffect(effectId: String) = send(Command.SetEffect(effectId))

    fun setSpeed(speed: Int) = send(Command.SetSpeed(speed))

    fun setStaticFrame(colors: List<RgbColor>) = send(Command.SetStaticFrame(colors))

    fun setPaletteSources(
        solid: RgbColor,
        stops: List<RgbColor>,
    ) = send(Command.SetPalette(solid, stops))

    fun setBrightness(brightness: Int) = send(Command.SetBrightness(brightness))

    fun setPower(power: Boolean) = send(Command.SetPower(power))

    fun setChargerOnly(chargerOnly: Boolean) = send(Command.SetChargerOnly(chargerOnly))

    fun setBatteryBreathe(enabled: Boolean) = send(Command.SetBatteryBreathe(enabled))

    private fun send(command: Command) {
        commands.trySend(command)
    }

    private suspend fun handle(command: Command) {
        when (command) {
            is Command.Bind -> onBind(command)
            Command.Unbind -> onUnbind()
            Command.Reassert -> onReassert()
            is Command.SetMode -> mutateIntent { it.copy(mode = command.mode) }
            is Command.SetEffect -> mutateIntent { it.copy(effectId = command.effectId) }
            is Command.SetSpeed -> mutateIntent { it.copy(speed = command.speed.coerceIn(0, 100)) }
            is Command.SetStaticFrame -> mutateIntent { it.copy(staticColors = command.colors, solidColor = command.colors.firstOrNull() ?: it.solidColor) }
            is Command.SetPalette -> mutateIntent { it.copy(solidColor = command.solid, gradientStops = command.stops) }
            is Command.SetBrightness -> mutateIntent { it.copy(brightness = command.brightness.coerceIn(0, 100)) }
            is Command.SetPower -> mutateIntent { it.copy(power = command.power) }
            is Command.SetChargerOnly -> mutateIntent { it.copy(chargerOnly = command.chargerOnly) }
            is Command.SetBatteryBreathe -> mutateIntent { it.copy(batteryBreathe = command.enabled) }
            is Command.WatchReading -> onReading(command)
        }
    }

    private suspend fun onBind(command: Command.Bind) {
        stopRenderJob()
        watchJob?.cancel()
        binding = command.binding
        intent = command.intent.copy(staticColors = command.intent.staticColors.fit(command.binding.zones))
        temperatureCelsius = command.binding.temperature?.readCelsius()
        rendererSignature = null
        val level = runCatching { command.binding.battery.read() }.getOrNull()
        if (level != null) {
            charging = level.charging
            batteryLevel = level.levelPercent
            batteryPresent = level.present
        }
        watchJob = scope.launch { watchLoop(command.binding) }
        reconcile()
    }

    private fun onUnbind() {
        stopRenderJob()
        watchJob?.cancel()
        watchJob = null
        binding = null
        publishSnapshot()
    }

    private suspend fun onReassert() {
        val binding = binding ?: return
        runCatching { binding.device.invalidate() }
        if (!intent.mode.isDynamic) applyStatic()
    }

    private suspend fun onReading(command: Command.WatchReading) {
        val previousEffective = effectivePower()
        charging = command.charging
        batteryLevel = command.levelPercent
        batteryPresent = command.present
        temperatureCelsius = command.temperatureCelsius
        if (effectivePower() != previousEffective && !intent.mode.isDynamic) {
            applyStatic()
        }
        publishSnapshot()
    }

    private suspend fun mutateIntent(transform: (LightingIntent) -> LightingIntent) {
        intent = transform(intent)
        reconcile()
    }

    private fun effectivePower(): Boolean = intent.power && (!intent.chargerOnly || charging)

    private suspend fun reconcile() {
        val binding = binding ?: run { publishSnapshot(); return }
        updateService()
        if (intent.mode.isDynamic) {
            ensureRenderJob(binding)
        } else {
            stopRenderJob()
            applyStatic()
        }
        publishSnapshot()
    }

    private fun updateService() {
        val needsService = binding != null && (intent.mode.isDynamic || (intent.chargerOnly && intent.power))
        if (needsService) serviceGate.start() else serviceGate.stop()
    }

    private fun ensureRenderJob(binding: LightingBinding) {
        val signature = intent.mode to intent.effectId
        if (renderJob?.isActive == true && rendererSignature == signature) return
        stopRenderJob()
        rendererSignature = signature
        val myGeneration = ++generation
        val renderer = buildRenderer(binding)
        renderJob = scope.launch { renderLoop(binding, myGeneration, renderer) }
    }

    private fun stopRenderJob() {
        renderJob?.cancel()
        renderJob = null
        rendererSignature = null
        generation++
    }

    private suspend fun applyStatic() {
        val binding = binding ?: return
        val colors = intent.staticColors.fit(binding.zones)
        val effective = effectivePower()
        runCatching { binding.device.applyZones(colors, intent.brightness, effective) }
        lastFrame = if (effective) colors else List(binding.zones) { RgbColor(0, 0, 0) }
        publishSnapshot()
    }

    private suspend fun renderLoop(
        binding: LightingBinding,
        myGeneration: Long,
        renderer: Renderer,
    ) {
        val startMs = clockMs()
        var offApplied = false
        while (scope.isActive) {
            if (myGeneration != generation) return
            if (!effectivePower()) {
                if (!offApplied) {
                    runCatching { binding.device.applyZones(binding.offFrame(), intent.brightness, false) }
                    lastFrame = binding.offFrame()
                    publishSnapshot()
                    offApplied = true
                }
                delay(POWER_OFF_IDLE_MS)
                continue
            }
            offApplied = false
            val nowSeconds = (clockMs() - startMs) / 1000.0
            val tick = renderer.render(nowSeconds)
            runCatching { binding.device.applyZones(tick.colors, intent.brightness, true) }
            lastFrame = tick.colors
            publishSnapshot()
            delay(tick.nextDelayMs.coerceAtLeast(1L))
        }
    }

    private fun buildRenderer(binding: LightingBinding): Renderer {
        val zones = binding.zones
        val interval = binding.device.recommendedFrameIntervalMs
        return when (intent.mode) {
            AppMode.EFFECT ->
                EffectRenderer(
                    effectId = intent.effectId,
                    zones = zones,
                    frameIntervalMs = interval,
                    speed = { intent.speed },
                    palette = { resolvePalette(binding) },
                )
            AppMode.BATTERY ->
                IndicatorRenderer(
                    zones = zones,
                    frameIntervalMs = interval,
                    idleIntervalMs = INDICATOR_IDLE_MS,
                    target = { StatusTargets.batteryTarget(batteryLevel, binding.bands.battery) },
                    breathing = { StatusTargets.batteryBreathing(charging, intent.batteryBreathe, batteryLevel) },
                )
            AppMode.TEMPERATURE ->
                IndicatorRenderer(
                    zones = zones,
                    frameIntervalMs = interval,
                    idleIntervalMs = INDICATOR_IDLE_MS,
                    target = {
                        val celsius = binding.temperature?.readCelsius().also { temperatureCelsius = it }
                        StatusTargets.temperatureTarget(celsius, binding.bands.temperature)
                    },
                    breathing = { StatusTargets.temperatureBreathing(true, temperatureCelsius) },
                )
            AppMode.PERFORMANCE ->
                PerformanceRenderer(
                    zones = zones,
                    frameIntervalMs = interval,
                    idleIntervalMs = INDICATOR_IDLE_MS,
                    value = { binding.performance?.read() },
                )
            AppMode.CLOCK ->
                ClockRenderer(
                    zones = zones,
                    intervalMs = CLOCK_INTERVAL_MS,
                    hour = localHour,
                )
            AppMode.COLOR, AppMode.GRADIENT ->
                EffectRenderer(intent.effectId, zones, interval, { intent.speed }, { resolvePalette(binding) })
        }
    }

    private fun resolvePalette(binding: LightingBinding): EffectPalette {
        val zones = binding.zones
        val need = binding.catalog.byId(intent.effectId)?.need ?: EffectNeed.COLOR
        return when (need) {
            EffectNeed.GRADIENT ->
                EffectPalette(GradientInterpolator.interpolate(intent.gradientStops, zones), intent.gradientStops)
            else ->
                EffectPalette(List(zones) { intent.solidColor }, intent.gradientStops)
        }
    }

    private suspend fun watchLoop(binding: LightingBinding) {
        while (scope.isActive) {
            val reading = runCatching { binding.battery.read() }.getOrNull()
            val temperature = binding.temperature?.let { runCatching { it.readCelsius() }.getOrNull() }
            commands.trySend(
                Command.WatchReading(
                    charging = reading?.charging ?: charging,
                    levelPercent = reading?.levelPercent ?: batteryLevel,
                    present = reading?.present ?: batteryPresent,
                    temperatureCelsius = temperature,
                ),
            )
            delay(WATCH_INTERVAL_MS)
        }
    }

    private fun publishSnapshot() {
        val binding = binding
        mutableSnapshot.value =
            LightingSnapshot(
                bound = binding != null,
                deviceId = binding?.deviceId,
                mode = intent.mode,
                effectId = intent.effectId,
                speed = intent.speed,
                brightness = intent.brightness,
                powerRequested = intent.power,
                effectivePower = effectivePower(),
                chargerOnly = intent.chargerOnly,
                batteryBreathe = intent.batteryBreathe,
                charging = charging,
                batteryPresent = batteryPresent,
                batteryLevelPercent = batteryLevel,
                temperatureCelsius = temperatureCelsius,
                temperatureAvailable = binding?.temperature?.available == true,
                performanceMetric = binding?.performance?.metric,
                currentFrame = lastFrame,
            )
    }

    private fun LightingBinding.offFrame(): List<RgbColor> = List(zones) { RgbColor(0, 0, 0) }

    private fun List<RgbColor>.fit(zones: Int): List<RgbColor> {
        val fallback = firstOrNull() ?: RgbColor(93, 81, 255)
        return List(zones.coerceAtLeast(1)) { getOrNull(it) ?: fallback }
    }

    private sealed interface Command {
        data class Bind(val binding: LightingBinding, val intent: LightingIntent) : Command

        data object Unbind : Command

        data object Reassert : Command

        data class SetMode(val mode: AppMode) : Command

        data class SetEffect(val effectId: String) : Command

        data class SetSpeed(val speed: Int) : Command

        data class SetStaticFrame(val colors: List<RgbColor>) : Command

        data class SetPalette(val solid: RgbColor, val stops: List<RgbColor>) : Command

        data class SetBrightness(val brightness: Int) : Command

        data class SetPower(val power: Boolean) : Command

        data class SetChargerOnly(val chargerOnly: Boolean) : Command

        data class SetBatteryBreathe(val enabled: Boolean) : Command

        data class WatchReading(
            val charging: Boolean,
            val levelPercent: Int?,
            val present: Boolean,
            val temperatureCelsius: Double?,
        ) : Command
    }

    companion object {
        const val POWER_OFF_IDLE_MS = 500L
        const val INDICATOR_IDLE_MS = 500L
        const val CLOCK_INTERVAL_MS = 30_000L
        const val WATCH_INTERVAL_MS = 3_000L

        private fun defaultLocalHour(): Double {
            val calendar = java.util.Calendar.getInstance()
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = calendar.get(java.util.Calendar.MINUTE)
            return hour + minute / 60.0
        }
    }
}
