package com.hooandee.colores.control

import com.hooandee.colores.audio.AudioLevelSource
import com.hooandee.colores.audio.AudioLevelState
import com.hooandee.colores.audio.MutableAudioLevelSource
import com.hooandee.colores.engine.AudioVuRenderer
import com.hooandee.colores.engine.AudioScale
import com.hooandee.colores.engine.AudioSensitivity
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
import com.hooandee.colores.gradient.GradientPresentation
import com.hooandee.colores.led.HardwareEffect
import com.hooandee.colores.led.LedDevice
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.sensor.BatterySource
import com.hooandee.colores.sensor.PerformanceMetric
import com.hooandee.colores.sensor.PerformanceSource
import com.hooandee.colores.sensor.TemperatureSource
import kotlinx.coroutines.CancellationException
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
    AUDIO,
    ;

    val isDynamic: Boolean
        get() = this != COLOR && this != GRADIENT

    val isSensor: Boolean
        get() = this == BATTERY || this == TEMPERATURE || this == PERFORMANCE
}

data class LightingIntent(
    val mode: AppMode = AppMode.COLOR,
    val staticColors: List<RgbColor> = listOf(RgbColor(93, 81, 255), RgbColor(93, 81, 255)),
    val solidColor: RgbColor = RgbColor(93, 81, 255),
    val gradientStops: List<RgbColor> = listOf(RgbColor(93, 81, 255), RgbColor(93, 81, 255)),
    val effectId: String = "breathing",
    val speed: Int = 50,
    val gradientSpeed: Int = 30,
    val gradientPresentation: GradientPresentation = GradientPresentation.SPATIAL,
    val effectUsesGradient: Boolean = false,
    val brightness: Int = 100,
    val power: Boolean = true,
    val chargerOnly: Boolean = false,
    val batteryBreathe: Boolean = true,
    val temperatureBreathe: Boolean = true,
    val audioScale: AudioScale = AudioScale.DEFAULT,
    val audioSensitivityDb: Int = AudioSensitivity.NORMAL_DB,
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
    val audio: AudioLevelSource = MutableAudioLevelSource(),
)

data class LightingSnapshot(
    val bound: Boolean = false,
    val deviceId: String? = null,
    val mode: AppMode = AppMode.COLOR,
    val effectId: String = "breathing",
    val speed: Int = 50,
    val gradientSpeed: Int = 30,
    val effectUsesGradient: Boolean = false,
    val brightness: Int = 100,
    val powerRequested: Boolean = true,
    val effectivePower: Boolean = true,
    val chargerOnly: Boolean = false,
    val batteryBreathe: Boolean = true,
    val temperatureBreathe: Boolean = true,
    val charging: Boolean = true,
    val batteryPresent: Boolean = true,
    val batteryLevelPercent: Int? = null,
    val temperatureCelsius: Double? = null,
    val temperatureAvailable: Boolean = false,
    val performanceMetric: PerformanceMetric? = null,
    val audio: AudioLevelState = AudioLevelState(),
    val audioScale: AudioScale = AudioScale.DEFAULT,
    val audioSensitivityDb: Int = AudioSensitivity.NORMAL_DB,
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

    @Volatile
    private var binding: LightingBinding? = null
    @Volatile
    private var sensorBands = BandSet.FALLBACK
    private var renderJob: Job? = null
    private var watchJob: Job? = null
    private var rendererSignature: Pair<AppMode, String>? = null

    @Volatile
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

    fun reassert() = send(Command.Reassert)

    fun boundDevice(deviceId: String): LedDevice? = binding?.takeIf { it.deviceId == deviceId }?.device

    fun setMode(mode: AppMode) = send(Command.SetMode(mode))

    fun setEffect(effectId: String) = send(Command.SetEffect(effectId))

    fun setSpeed(speed: Int) = send(Command.SetSpeed(speed))

    fun setGradientSpeed(speed: Int) = send(Command.SetGradientSpeed(speed))

    fun setEffectUsesGradient(enabled: Boolean) = send(Command.SetEffectUsesGradient(enabled))

    fun setStaticFrame(colors: List<RgbColor>) = send(Command.SetStaticFrame(colors))

    fun setPaletteSources(
        solid: RgbColor,
        stops: List<RgbColor>,
    ) = send(Command.SetPalette(solid, stops))

    fun setBrightness(brightness: Int) = send(Command.SetBrightness(brightness))

    fun setPower(power: Boolean) = send(Command.SetPower(power))

    fun setChargerOnly(chargerOnly: Boolean) = send(Command.SetChargerOnly(chargerOnly))

    fun setBatteryBreathe(enabled: Boolean) = send(Command.SetBatteryBreathe(enabled))

    fun setTemperatureBreathe(enabled: Boolean) = send(Command.SetTemperatureBreathe(enabled))

    fun setSensorBands(bands: BandSet) = send(Command.SetSensorBands(bands))

    fun setAudioScale(scale: AudioScale) = send(Command.SetAudioScale(scale))

    fun setAudioSensitivity(gainDb: Int) = send(Command.SetAudioSensitivity(gainDb))

    fun onAudioStateChanged() = send(Command.AudioStateChanged)

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
            is Command.SetGradientSpeed -> mutateIntent { it.copy(gradientSpeed = command.speed.coerceIn(0, 100)) }
            is Command.SetEffectUsesGradient -> mutateIntent { it.copy(effectUsesGradient = command.enabled) }
            is Command.SetStaticFrame -> mutateIntent { it.copy(staticColors = command.colors, solidColor = command.colors.firstOrNull() ?: it.solidColor) }
            is Command.SetPalette -> mutateIntent { it.copy(solidColor = command.solid, gradientStops = command.stops) }
            is Command.SetBrightness -> mutateIntent { it.copy(brightness = command.brightness.coerceIn(0, 100)) }
            is Command.SetPower -> mutateIntent { it.copy(power = command.power) }
            is Command.SetChargerOnly -> mutateIntent { it.copy(chargerOnly = command.chargerOnly) }
            is Command.SetBatteryBreathe -> mutateIntent { it.copy(batteryBreathe = command.enabled) }
            is Command.SetTemperatureBreathe -> mutateIntent { it.copy(temperatureBreathe = command.enabled) }
            is Command.SetAudioScale -> mutateIntent { it.copy(audioScale = command.scale) }
            is Command.SetAudioSensitivity ->
                mutateIntent {
                    it.copy(audioSensitivityDb = command.gainDb.coerceIn(AudioSensitivity.MIN_DB, AudioSensitivity.MAX_DB))
                }
            Command.AudioStateChanged -> reconcile()
            is Command.SetSensorBands -> {
                sensorBands = command.bands
                publishSnapshot()
            }
            is Command.WatchReading -> onReading(command)
        }
    }

    private suspend fun onBind(command: Command.Bind) {
        stopRenderJob()
        watchJob?.cancel()
        binding = command.binding
        sensorBands = command.binding.bands
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
        serviceGate.stop()
        publishSnapshot()
    }

    private suspend fun onReassert() {
        val binding = binding ?: return
        runCatching { binding.device.invalidate() }
        applyCurrent(binding, manageRenderJob = false)
    }

    private suspend fun onReading(command: Command.WatchReading) {
        val previousEffective = effectivePower()
        charging = command.charging
        batteryLevel = command.levelPercent
        batteryPresent = command.present
        temperatureCelsius = command.temperatureCelsius
        if (effectivePower() != previousEffective) {
            binding?.let { applyCurrent(it, manageRenderJob = false) }
        }
        publishSnapshot()
    }

    private suspend fun mutateIntent(transform: (LightingIntent) -> LightingIntent) {
        intent = transform(intent)
        reconcile()
    }

    private fun effectivePower(): Boolean = intent.power && (!intent.chargerOnly || charging)

    private fun hardwareEffect(binding: LightingBinding): HardwareEffect? {
        if (intent.mode != AppMode.EFFECT || usesSoftwareGradientOverlay(binding)) return null
        return binding.device.hardwareEffects.firstOrNull { it.id == intent.effectId }
    }

    private fun usesSoftwareGradientOverlay(binding: LightingBinding): Boolean =
        intent.effectUsesGradient && binding.catalog.byId(intent.effectId)?.need == EffectNeed.COLOR

    private fun needsRenderLoop(binding: LightingBinding): Boolean =
        when (intent.mode) {
            AppMode.GRADIENT -> intent.gradientPresentation == GradientPresentation.ANIMATED || !binding.device.supportsPerZone
            AppMode.EFFECT -> hardwareEffect(binding) == null
            AppMode.AUDIO -> binding.audio.state.value.status.keepsAudioCaptureActive
            else -> intent.mode.isDynamic
        }

    private suspend fun reconcile() {
        val binding = binding ?: run { publishSnapshot(); return }
        updateService()
        applyCurrent(binding, manageRenderJob = true)
        publishSnapshot()
    }

    private suspend fun applyCurrent(
        binding: LightingBinding,
        manageRenderJob: Boolean,
    ) {
        val hwEffect = hardwareEffect(binding)
        when {
            !intent.power -> {
                if (manageRenderJob) stopRenderJob()
                applyOff(binding)
            }
            intent.mode == AppMode.AUDIO && !binding.audio.state.value.status.keepsAudioCaptureActive -> {
                if (manageRenderJob) stopRenderJob()
                applyAudioUnavailable(binding)
            }
            hwEffect != null -> {
                if (manageRenderJob) stopRenderJob()
                applyHardwareEffect(binding, hwEffect)
            }
            needsRenderLoop(binding) -> if (manageRenderJob) ensureRenderJob(binding)
            else -> {
                if (manageRenderJob) stopRenderJob()
                applyStatic()
            }
        }
    }

    private suspend fun applyHardwareEffect(
        binding: LightingBinding,
        effect: HardwareEffect,
    ) {
        val effective = effectivePower()
        val palette = intent.gradientStops.ifEmpty { listOf(intent.solidColor) }
        val colors =
            if (effect.colorStops >= 2) listOf(palette.first(), palette.last()) else listOf(intent.solidColor)
        runCatching {
            binding.device.applyHardwareEffect(effect.id, colors, intent.brightness, intent.speed, effective)
        }.rethrowCancellation()
        lastFrame = if (effective) List(binding.zones) { colors.first() } else List(binding.zones) { RgbColor(0, 0, 0) }
        publishSnapshot()
    }

    private fun updateService() {
        val activeBinding = binding
        val needsService =
            activeBinding != null &&
                intent.power &&
                (needsRenderLoop(activeBinding) || intent.chargerOnly)
        if (needsService) serviceGate.start() else serviceGate.stop()
    }

    private suspend fun applyOff(binding: LightingBinding) {
        val colors = binding.offFrame()
        runCatching { binding.device.applyZones(colors, intent.brightness, false) }.rethrowCancellation()
        lastFrame = colors
        publishSnapshot()
    }

    private suspend fun applyAudioUnavailable(binding: LightingBinding) {
        val colors = binding.offFrame()
        runCatching { binding.device.applyZones(colors, intent.brightness, effectivePower()) }.rethrowCancellation()
        lastFrame = colors
        publishSnapshot()
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
        runCatching { binding.device.applyZones(colors, intent.brightness, effective) }.rethrowCancellation()
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
                    runCatching { binding.device.applyZones(binding.offFrame(), intent.brightness, false) }.rethrowCancellation()
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
            runCatching { binding.device.applyZones(tick.colors, intent.brightness, true) }.rethrowCancellation()
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
            AppMode.GRADIENT ->
                EffectRenderer(
                    effectId = "gradient_sweep",
                    zones = zones,
                    frameIntervalMs = interval,
                    speed = { intent.gradientSpeed },
                    palette = { EffectPalette(List(zones) { intent.solidColor }, intent.gradientStops) },
                )
            AppMode.BATTERY ->
                IndicatorRenderer(
                    zones = zones,
                    frameIntervalMs = interval,
                    idleIntervalMs = INDICATOR_IDLE_MS,
                    target = { StatusTargets.batteryTarget(batteryLevel, sensorBands.battery) },
                    breathing = { StatusTargets.batteryBreathing(charging, intent.batteryBreathe, batteryLevel) },
                )
            AppMode.TEMPERATURE ->
                IndicatorRenderer(
                    zones = zones,
                    frameIntervalMs = interval,
                    idleIntervalMs = INDICATOR_IDLE_MS,
                    target = {
                        val celsius = binding.temperature?.readCelsius().also { temperatureCelsius = it }
                        StatusTargets.temperatureTarget(celsius, sensorBands.temperature)
                    },
                    breathing = {
                        StatusTargets.temperatureBreathing(
                            intent.temperatureBreathe,
                            temperatureCelsius,
                            sensorBands.temperature,
                        )
                    },
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
            AppMode.AUDIO ->
                AudioVuRenderer(
                    zones = zones,
                    frameIntervalMs = interval,
                    scale = { intent.audioScale },
                    sensitivityDb = { intent.audioSensitivityDb },
                    state = { binding.audio.state.value },
                )
            else ->
                EffectRenderer(intent.effectId, zones, interval, { intent.speed }, { resolvePalette(binding) })
        }
    }

    private fun resolvePalette(binding: LightingBinding): EffectPalette {
        val zones = binding.zones
        val need = binding.catalog.byId(intent.effectId)?.need ?: EffectNeed.COLOR
        val usesGradient = need == EffectNeed.GRADIENT || (need == EffectNeed.COLOR && intent.effectUsesGradient)
        val base =
            if (usesGradient) {
                GradientInterpolator.interpolate(intent.gradientStops, zones)
            } else {
                List(zones) { intent.solidColor }
            }
        return EffectPalette(base, intent.gradientStops)
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
                gradientSpeed = intent.gradientSpeed,
                effectUsesGradient = intent.effectUsesGradient,
                brightness = intent.brightness,
                powerRequested = intent.power,
                effectivePower = effectivePower(),
                chargerOnly = intent.chargerOnly,
                batteryBreathe = intent.batteryBreathe,
                temperatureBreathe = intent.temperatureBreathe,
                charging = charging,
                batteryPresent = batteryPresent,
                batteryLevelPercent = batteryLevel,
                temperatureCelsius = temperatureCelsius,
                temperatureAvailable = binding?.temperature?.available == true,
                performanceMetric = binding?.performance?.metric,
                audio = binding?.audio?.state?.value ?: AudioLevelState(),
                audioScale = intent.audioScale,
                audioSensitivityDb = intent.audioSensitivityDb,
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

        data class SetGradientSpeed(val speed: Int) : Command

        data class SetEffectUsesGradient(val enabled: Boolean) : Command

        data class SetStaticFrame(val colors: List<RgbColor>) : Command

        data class SetPalette(val solid: RgbColor, val stops: List<RgbColor>) : Command

        data class SetBrightness(val brightness: Int) : Command

        data class SetPower(val power: Boolean) : Command

        data class SetChargerOnly(val chargerOnly: Boolean) : Command

        data class SetBatteryBreathe(val enabled: Boolean) : Command

        data class SetTemperatureBreathe(val enabled: Boolean) : Command

        data class SetAudioScale(val scale: AudioScale) : Command

        data class SetAudioSensitivity(val gainDb: Int) : Command

        data object AudioStateChanged : Command

        data class SetSensorBands(val bands: BandSet) : Command

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

private fun <T> Result<T>.rethrowCancellation(): Result<T> = onFailure { if (it is CancellationException) throw it }

private val com.hooandee.colores.audio.AudioCaptureStatus.keepsAudioCaptureActive: Boolean
    get() =
        this == com.hooandee.colores.audio.AudioCaptureStatus.STARTING ||
            this == com.hooandee.colores.audio.AudioCaptureStatus.CAPTURING ||
            this == com.hooandee.colores.audio.AudioCaptureStatus.NO_AUDIO
