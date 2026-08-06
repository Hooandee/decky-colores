package com.hooandee.colores.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hooandee.colores.ColoresApplication
import com.hooandee.colores.audio.AudioCaptureStatus
import com.hooandee.colores.audio.AudioLevelState
import com.hooandee.colores.control.AppMode
import com.hooandee.colores.control.LightingBinding
import com.hooandee.colores.control.LightingController
import com.hooandee.colores.control.LightingIntent
import com.hooandee.colores.control.LightingPreferences
import com.hooandee.colores.control.StoredLighting
import com.hooandee.colores.device.AndroidDeviceDetector
import com.hooandee.colores.device.DetectedAndroidDevice
import com.hooandee.colores.engine.BandSet
import com.hooandee.colores.engine.AudioScale
import com.hooandee.colores.engine.AudioSensitivity
import com.hooandee.colores.engine.EffectCatalog
import com.hooandee.colores.engine.EffectNeed
import com.hooandee.colores.engine.EffectPreset
import com.hooandee.colores.engine.SensorBand
import com.hooandee.colores.engine.SensorKind
import com.hooandee.colores.effects.EffectsService
import com.hooandee.colores.gradient.DeviceGradientPreferences
import com.hooandee.colores.gradient.GradientInterpolator
import com.hooandee.colores.gradient.GradientPreferences
import com.hooandee.colores.gradient.GradientPresentation
import com.hooandee.colores.gradient.GradientPreset
import com.hooandee.colores.gradient.GradientPresetRepository
import com.hooandee.colores.gradient.LightingMode
import com.hooandee.colores.gradient.editorStopCount
import com.hooandee.colores.gradient.gradientPresentation
import com.hooandee.colores.led.LedDevice
import com.hooandee.colores.led.LedDeviceFactory
import com.hooandee.colores.led.LedState
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.permission.WriteSettingsPermission
import com.hooandee.colores.sensor.AndroidBatterySource
import com.hooandee.colores.sensor.PerformanceMetric
import com.hooandee.colores.sensor.PerformanceSources
import com.hooandee.colores.sensor.SysfsThermalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ColoresUiState(
    val loading: Boolean = true,
    val detected: DetectedAndroidDevice? = null,
    val controlAccess: ControlAccess = ControlAccess.SERVICE_UNAVAILABLE,
    val mode: AppMode = AppMode.COLOR,
    val effects: List<EffectPreset> = emptyList(),
    val effectId: String = "breathing",
    val speed: Int = 50,
    val gradientSpeed: Int = 30,
    val effectUsesGradient: Boolean = false,
    val softwareEffectIds: Set<String> = emptySet(),
    val ledState: LedState =
        LedState(
            zoneColors = listOf(RgbColor(93, 81, 255), RgbColor(93, 81, 255)),
            brightness = 100,
            power = true,
        ),
    val effectivePower: Boolean = true,
    val currentFrame: List<RgbColor> = emptyList(),
    val editTarget: EditTarget = EditTarget.BOTH,
    val ledPreviewEnabled: Boolean = false,
    val gradientPresentation: GradientPresentation? = null,
    val gradient: GradientUiState = GradientUiState(),
    val chargerOnly: Boolean = false,
    val batteryBreathe: Boolean = true,
    val temperatureBreathe: Boolean = true,
    val sensorBandDefaults: BandSet = BandSet.FALLBACK,
    val sensorBands: BandSet = BandSet.FALLBACK,
    val charging: Boolean = true,
    val batteryPresent: Boolean = true,
    val batteryLevelPercent: Int? = null,
    val temperatureCelsius: Double? = null,
    val temperatureAvailable: Boolean = false,
    val performanceMetric: PerformanceMetric? = null,
    val audio: AudioLevelState = AudioLevelState(),
    val audioScale: AudioScale = AudioScale.DEFAULT,
    val audioSensitivityDb: Int = AudioSensitivity.NORMAL_DB,
) {
    val canWrite: Boolean
        get() = controlAccess == ControlAccess.ENABLED

    val colorEnabled: Boolean
        get() = detected?.capabilities?.color == true

    val brightnessEnabled: Boolean
        get() = detected?.capabilities?.brightness == true

    val gradientAvailable: Boolean
        get() = gradientPresentation != null

    val gradientAnimated: Boolean
        get() = gradientPresentation == GradientPresentation.ANIMATED

    val sensorsAvailable: Boolean
        get() = batteryPresent || temperatureAvailable || performanceMetric != null

    val audioNeedsAuthorization: Boolean
        get() =
            audio.status == AudioCaptureStatus.AUTHORIZATION_REQUIRED ||
                audio.status == AudioCaptureStatus.REVOKED ||
                audio.status == AudioCaptureStatus.ERROR

    val currentEffect: EffectPreset?
        get() = effects.firstOrNull { it.id == effectId }

    val effectNeedsGradient: Boolean
        get() =
            mode == AppMode.EFFECT &&
                (currentEffect?.need == EffectNeed.GRADIENT || (effectUsesGradient && canUseGradientForEffect))

    val canUseGradientForEffect: Boolean
        get() =
            gradientAvailable &&
                currentEffect?.need == EffectNeed.COLOR &&
                currentEffect?.id in softwareEffectIds

    val gradientEditable: Boolean
        get() = gradientAvailable || effectNeedsGradient

    val editingGradientStops: Boolean
        get() = (gradient.mode == LightingMode.GRADIENT && gradientAvailable) || effectNeedsGradient

    val editingColor: RgbColor
        get() =
            if (editingGradientStops) {
                gradient.selectedStop ?: ledState.colorForEditing(editTarget)
            } else {
                ledState.colorForEditing(editTarget)
            }

    val mixedTarget: Boolean
        get() = gradient.mode == LightingMode.COLOR && editTarget == EditTarget.BOTH && ledState.hasMixedColors

    val ledColorProjection: LedColorProjection
        get() = LedColorProjection(detected?.previewCalibration, ledPreviewEnabled)

    fun availableModes(): List<AppMode> =
        buildList {
            if (colorEnabled) {
                add(AppMode.COLOR)
                if (gradientAvailable) add(AppMode.GRADIENT)
                add(AppMode.EFFECT)
            }
            if (sensorsAvailable) add(AppMode.BATTERY)
            if (colorEnabled) add(AppMode.CLOCK)
            if (colorEnabled) add(AppMode.AUDIO)
        }

    fun availableSensorModes(): List<AppMode> =
        buildList {
            if (batteryPresent) add(AppMode.BATTERY)
            if (temperatureAvailable) add(AppMode.TEMPERATURE)
            if (performanceMetric != null) add(AppMode.PERFORMANCE)
        }
}

class ColoresViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(ColoresUiState())
    val state: StateFlow<ColoresUiState> = mutableState.asStateFlow()

    private val controller: LightingController = (application as ColoresApplication).lightingController
    private var refreshJob: Job? = null
    private var commitJob: Job? = null
    private val ledPreviewPreferences = LedPreviewPreferences(application)
    private val gradientPreferences = GradientPreferences(application)
    private val lightingPreferences = LightingPreferences(application)
    private val gradientPresets = GradientPresetRepository(application).load()

    init {
        viewModelScope.launch {
            controller.snapshot.collect { snap ->
                if (!snap.bound) return@collect
                mutableState.update { current ->
                    current.copy(
                        mode = snap.mode,
                        effectId = snap.effectId,
                        speed = snap.speed,
                        gradientSpeed = snap.gradientSpeed,
                        effectUsesGradient = snap.effectUsesGradient,
                        effectivePower = snap.effectivePower,
                        currentFrame = snap.currentFrame,
                        chargerOnly = snap.chargerOnly,
                        batteryBreathe = snap.batteryBreathe,
                        temperatureBreathe = snap.temperatureBreathe,
                        charging = snap.charging,
                        batteryPresent = snap.batteryPresent,
                        batteryLevelPercent = snap.batteryLevelPercent,
                        temperatureCelsius = snap.temperatureCelsius,
                        temperatureAvailable = snap.temperatureAvailable,
                        performanceMetric = snap.performanceMetric,
                        audio = snap.audio,
                        audioScale = snap.audioScale,
                        audioSensitivityDb = snap.audioSensitivityDb,
                        ledState = current.ledState.copy(power = snap.powerRequested, brightness = snap.brightness),
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                val context = getApplication<Application>()
                val detected = withContext(Dispatchers.IO) { AndroidDeviceDetector(context).detect() }
                val userPermissionGranted = WriteSettingsPermission.canWrite(context)
                val applicationScope = getApplication<ColoresApplication>().applicationScope
                val device =
                    detected?.let {
                        controller.boundDevice(it.id)
                            ?: LedDeviceFactory.create(
                                context,
                                it.led,
                                scope = CoroutineScope(applicationScope.coroutineContext + Dispatchers.IO),
                            )
                    }
                val controlAccess =
                    detected?.let {
                        ControlAccess.resolve(
                            descriptor = it.led,
                            deviceAvailable = device?.available == true,
                            userPermissionGranted = userPermissionGranted,
                        )
                    } ?: ControlAccess.SERVICE_UNAVAILABLE

                if (detected == null || device == null || controlAccess != ControlAccess.ENABLED) {
                    controller.unbind()
                    mutableState.update {
                        it.copy(loading = false, detected = detected, controlAccess = controlAccess)
                    }
                    return@launch
                }

                val catalog = withContext(Dispatchers.IO) { EffectCatalog.parse(context.readAsset("effects.json")) }
                val effectPresets =
                    if (device.hardwareEffects.isNotEmpty()) {
                        device.hardwareEffects.map {
                            EffectPreset(
                                id = it.id,
                                need =
                                    when {
                                        it.colorStops >= 2 -> EffectNeed.GRADIENT
                                        it.colorStops == 1 -> EffectNeed.COLOR
                                        else -> EffectNeed.NONE
                                    },
                                defaultSpeed = it.defaultSpeed,
                                colors = it.colors,
                            )
                        }
                    } else {
                        catalog.presets
                    }
                val bands = withContext(Dispatchers.IO) { BandSet.parse(context.readAsset("bands.json")) }
                val zones = detected.capabilities.zones
                val gradientPresentation = detected.capabilities.gradientPresentation(device.supportsPerZone)
                val gradientSupported = gradientPresentation != null
                val gradientStopCount = gradientPresentation?.editorStopCount(zones) ?: zones
                val storedGradient =
                    withContext(Dispatchers.IO) { gradientPreferences.load(detected.id) }
                val storedLighting = withContext(Dispatchers.IO) { lightingPreferences.load(detected.id, bands) }
                val liveState = withContext(Dispatchers.IO) { runCatching { device.readState() }.getOrNull() }

                val hydratedGradient =
                    hydrateGradientUiState(
                        liveColors = storedGradient.currentStops,
                        preferences = storedGradient,
                        presets = gradientPresets,
                        zones = gradientStopCount,
                        supported = gradientSupported,
                    ).let { gradient ->
                        val minStops = if (device.hardwareEffects.any { it.colorStops >= 2 }) 2 else 1
                        if (gradient.stops.size < minStops) {
                            val fallback = gradient.stops.firstOrNull() ?: RgbColor(93, 81, 255)
                            val padded = List(maxOf(zones, minStops)) { gradient.stops.getOrNull(it) ?: fallback }
                            gradient.copy(stops = padded)
                        } else {
                            gradient
                        }
                    }
                val zoneColors = GradientInterpolator.interpolate(hydratedGradient.stops, zones)
                val brightness = storedLighting.brightness ?: liveState?.brightness ?: 100
                val power = storedLighting.power ?: liveState?.power ?: true

                val alreadyBound =
                    controller.snapshot.value.bound && controller.snapshot.value.deviceId == detected.id

                if (!alreadyBound) {
                    controller.bind(
                        LightingBinding(
                            deviceId = detected.id,
                            device = device,
                            zones = zones,
                            catalog = catalog,
                            bands = storedLighting.sensorBands,
                            battery = AndroidBatterySource(context),
                            temperature = SysfsThermalSource().takeIf { it.available },
                            performance = PerformanceSources.detect(),
                            audio = getApplication<ColoresApplication>().audioLevelSource,
                        ),
                        LightingIntent(
                            mode = storedLighting.mode.coerceAvailable(gradientSupported),
                            staticColors = zoneColors,
                            solidColor = zoneColors.firstOrNull() ?: RgbColor(93, 81, 255),
                            gradientStops = hydratedGradient.stops,
                            effectId =
                                effectPresets.firstOrNull { it.id == storedLighting.effectId }?.id
                                    ?: effectPresets.firstOrNull()?.id
                                    ?: catalog.defaultEffectId,
                            speed = storedLighting.speed,
                            gradientSpeed = storedLighting.gradientSpeed,
                            gradientPresentation = gradientPresentation ?: GradientPresentation.SPATIAL,
                            effectUsesGradient = storedLighting.effectUsesGradient,
                            brightness = brightness,
                            power = power,
                            chargerOnly = storedLighting.chargerOnly,
                            batteryBreathe = storedLighting.batteryBreathe,
                            temperatureBreathe = storedLighting.temperatureBreathe,
                            audioScale = storedLighting.audioScale,
                            audioSensitivityDb = storedLighting.audioSensitivityDb,
                        ),
                    )
                }

                mutableState.update { current ->
                    current.copy(
                        loading = false,
                        detected = detected,
                        controlAccess = controlAccess,
                        effects = effectPresets,
                        softwareEffectIds = catalog.presets.mapTo(mutableSetOf()) { it.id },
                        gradientSpeed = storedLighting.gradientSpeed,
                        effectUsesGradient = storedLighting.effectUsesGradient,
                        ledState = LedState(zoneColors, brightness, power),
                        gradientPresentation = gradientPresentation,
                        gradient = hydratedGradient,
                        sensorBandDefaults = bands,
                        sensorBands = storedLighting.sensorBands,
                        audioScale = storedLighting.audioScale,
                        audioSensitivityDb = storedLighting.audioSensitivityDb,
                        ledPreviewEnabled =
                            detected.takeIf { it.previewCalibration != null }
                                ?.let { ledPreviewPreferences.isEnabled(it.id) } ?: false,
                    )
                }
            }
    }

    fun onScreenOn() {
        if (mutableState.value.canWrite) controller.reassert()
    }

    fun setAppMode(mode: AppMode) {
        val current = mutableState.value
        if (!current.canWrite) return
        val target =
            if (mode.isSensor) {
                if (current.mode.isSensor) return
                current.availableSensorModes().firstOrNull() ?: return
            } else {
                mode
            }
        if (target == current.mode) return
        if (target == AppMode.AUDIO) return
        if (target == AppMode.GRADIENT && !current.gradientAvailable) return
        if (current.mode == AppMode.AUDIO) {
            EffectsService.stopAudio(getApplication(), AudioCaptureStatus.AUTHORIZATION_REQUIRED)
        }
        when (target) {
            AppMode.GRADIENT -> updateGradient(current.gradient.copy(mode = LightingMode.GRADIENT), apply = false)
            AppMode.COLOR -> {
                val color = current.gradient.selectedStop ?: current.editingColor
                mutableState.update {
                    it.copy(
                        gradient = it.gradient.copy(mode = LightingMode.COLOR),
                        ledState = it.ledState.withTargetColor(EditTarget.BOTH, color),
                        editTarget = EditTarget.BOTH,
                    )
                }
            }
            else -> Unit
        }
        controller.setMode(target)
        mutableState.update { it.copy(mode = target) }
        pushColorsToController()
        persistLighting()
    }

    fun selectEffect(effectId: String) {
        val current = mutableState.value
        val preset = current.effects.firstOrNull { it.id == effectId } ?: return
        controller.setEffect(preset.id)
        if (preset.id != current.effectId) controller.setSpeed(preset.defaultSpeed)
        controller.setMode(AppMode.EFFECT)
        mutableState.update { it.copy(mode = AppMode.EFFECT, effectId = preset.id, speed = if (preset.id != current.effectId) preset.defaultSpeed else it.speed) }
        pushColorsToController()
        persistLighting()
    }

    fun setSpeed(speed: Int) {
        controller.setSpeed(speed)
        mutableState.update { it.copy(speed = speed.coerceIn(0, 100)) }
        persistLighting()
    }

    fun setGradientSpeed(speed: Int) {
        val clamped = speed.coerceIn(0, 100)
        controller.setGradientSpeed(clamped)
        mutableState.update { it.copy(gradientSpeed = clamped) }
        persistLighting()
    }

    fun setEffectUsesGradient(enabled: Boolean) {
        val current = mutableState.value
        if (enabled && !current.canUseGradientForEffect) return
        controller.setEffectUsesGradient(enabled)
        mutableState.update { it.copy(effectUsesGradient = enabled) }
        pushColorsToController()
        persistLighting()
    }

    fun selectSensorMode(mode: AppMode) {
        if (mode !in mutableState.value.availableSensorModes()) return
        controller.setMode(mode)
        mutableState.update { it.copy(mode = mode) }
        persistLighting()
    }

    fun setPower(power: Boolean) {
        if (!power && mutableState.value.mode == AppMode.AUDIO) {
            EffectsService.stopAudio(getApplication(), AudioCaptureStatus.AUTHORIZATION_REQUIRED)
        }
        controller.setPower(power)
        mutableState.update { it.copy(ledState = it.ledState.copy(power = power)) }
        persistLighting()
    }

    fun activateAudio(
        resultCode: Int,
        resultData: Intent,
    ) {
        val current = mutableState.value
        if (!current.canWrite || !current.colorEnabled) return
        EffectsService.startAudio(getApplication(), resultCode, resultData)
        getApplication<ColoresApplication>().audioLevelSource.reset(AudioCaptureStatus.STARTING)
        controller.setMode(AppMode.AUDIO)
        mutableState.update { it.copy(mode = AppMode.AUDIO, audio = AudioLevelState(status = AudioCaptureStatus.STARTING)) }
        persistLighting()
    }

    fun onAudioAuthorizationDenied() {
        getApplication<ColoresApplication>().audioLevelSource.reset(AudioCaptureStatus.AUTHORIZATION_REQUIRED)
        mutableState.update { it.copy(audio = AudioLevelState(status = AudioCaptureStatus.AUTHORIZATION_REQUIRED)) }
    }

    fun setBrightness(brightness: Int) {
        val clamped = brightness.coerceIn(0, 100)
        controller.setBrightness(clamped)
        mutableState.update { it.copy(ledState = it.ledState.copy(brightness = clamped)) }
        persistLighting()
    }

    fun setChargerOnly(enabled: Boolean) {
        controller.setChargerOnly(enabled)
        mutableState.update { it.copy(chargerOnly = enabled) }
        persistLighting()
    }

    fun setBatteryBreathe(enabled: Boolean) {
        controller.setBatteryBreathe(enabled)
        mutableState.update { it.copy(batteryBreathe = enabled) }
        persistLighting()
    }

    fun setTemperatureBreathe(enabled: Boolean) {
        controller.setTemperatureBreathe(enabled)
        mutableState.update { it.copy(temperatureBreathe = enabled) }
        persistLighting()
    }

    fun setSensorBands(
        kind: SensorKind,
        bands: List<SensorBand>,
    ) {
        val current = mutableState.value
        val updated = current.sensorBands.replace(kind, bands) ?: return
        controller.setSensorBands(updated)
        mutableState.update { it.copy(sensorBands = updated) }
        persistLighting()
    }

    fun setAudioScale(scale: AudioScale) {
        controller.setAudioScale(scale)
        mutableState.update { it.copy(audioScale = scale) }
        persistLighting()
    }

    fun setAudioSensitivity(gainDb: Int) {
        val clamped = gainDb.coerceIn(AudioSensitivity.MIN_DB, AudioSensitivity.MAX_DB)
        controller.setAudioSensitivity(clamped)
        mutableState.update { it.copy(audioSensitivityDb = clamped) }
        persistLighting()
    }

    fun selectTarget(target: EditTarget) {
        mutableState.update { it.copy(editTarget = target) }
    }

    fun setEditingColor(color: RgbColor) {
        val current = mutableState.value
        if (current.editingGradientStops) {
            updateGradient(current.gradient.replaceSelectedStop(color), apply = true, debounce = true)
        } else {
            updateLedState(debounce = true) { state -> state.withTargetColor(current.editTarget, color) }
        }
    }

    fun setSaturation(saturation: Float) {
        val current = mutableState.value
        if (current.editingGradientStops) {
            val changed = current.editingColor.toHsvColor().copy(saturation = saturation.coerceIn(0f, 1f)).toRgbColor()
            updateGradient(current.gradient.replaceSelectedStop(changed), apply = true, debounce = true)
        } else {
            updateLedState(debounce = true) { state -> state.withTargetSaturation(current.editTarget, saturation) }
        }
    }

    fun selectGradientStop(index: Int) {
        val current = mutableState.value
        if (!current.gradientEditable) return
        mutableState.update { it.copy(gradient = it.gradient.selectStop(index)) }
    }

    fun selectGradientPreset(preset: GradientPreset) {
        val current = mutableState.value
        if (current.detected == null) return
        if (!current.gradientEditable) return
        updateGradient(current.gradient.selectPreset(preset, current.gradientStopCount()), apply = true)
    }

    fun selectSavedGradient(name: String) {
        val current = mutableState.value
        val saved = current.gradient.savedGradients.firstOrNull { it.name == name } ?: return
        if (current.detected == null) return
        updateGradient(
            current.gradient.copy(
                mode = LightingMode.GRADIENT,
                stops = GradientInterpolator.interpolate(saved.stops, current.gradientStopCount()),
                selectedStopIndex = 0,
                selectedPresetId = null,
            ),
            apply = true,
        )
    }

    fun restoreGradientPreset() {
        val current = mutableState.value
        if (current.detected == null) return
        val restored = current.gradient.restorePreset(current.gradientStopCount())
        if (restored == current.gradient) return
        updateGradient(restored, apply = true)
    }

    fun saveGradient(name: String) {
        val current = mutableState.value
        val deviceId = current.detected?.id ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || current.gradient.stops.isEmpty()) return
        persistGradient()
        val saved = gradientPreferences.upsert(deviceId, trimmed, current.gradient.stops)
        mutableState.update { it.copy(gradient = it.gradient.copy(savedGradients = saved.savedGradients)) }
    }

    fun deleteGradient(name: String) {
        val current = mutableState.value
        val deviceId = current.detected?.id ?: return
        val saved = gradientPreferences.delete(deviceId, name)
        mutableState.update { it.copy(gradient = it.gradient.copy(savedGradients = saved.savedGradients)) }
    }

    fun setLedPreviewEnabled(enabled: Boolean) {
        val current = mutableState.value
        val device = current.detected?.takeIf { it.previewCalibration != null } ?: return
        mutableState.update { it.copy(ledPreviewEnabled = enabled) }
        ledPreviewPreferences.setEnabled(device.id, enabled)
    }

    private fun updateLedState(
        debounce: Boolean = false,
        transform: (LedState) -> LedState,
    ) {
        val current = mutableState.value
        if (!current.canWrite || current.detected == null) return
        mutableState.update { it.copy(ledState = transform(it.ledState)) }
        scheduleCommit(debounce) { pushColorsToController() }
    }

    private fun updateGradient(
        gradient: GradientUiState,
        apply: Boolean,
        debounce: Boolean = false,
    ) {
        val current = mutableState.value
        val zones = current.detected?.capabilities?.zones ?: return
        if (!current.canWrite || !current.gradientEditable) return
        val colors = GradientInterpolator.interpolate(gradient.stops, zones)
        mutableState.update { it.copy(gradient = gradient, ledState = it.ledState.copy(zoneColors = colors)) }
        if (apply) {
            scheduleCommit(debounce) {
                persistGradient()
                pushColorsToController()
            }
        } else {
            persistGradient()
        }
    }

    private fun scheduleCommit(
        debounce: Boolean,
        action: () -> Unit,
    ) {
        commitJob?.cancel()
        if (!debounce) {
            action()
            return
        }
        commitJob = viewModelScope.launch {
            delay(COLOR_COMMIT_DEBOUNCE_MS)
            action()
        }
    }

    private fun pushColorsToController() {
        val current = mutableState.value
        val fallback = RgbColor(93, 81, 255)
        controller.setStaticFrame(current.ledState.zoneColors)
        controller.setPaletteSources(
            solid = current.ledState.zoneColors.firstOrNull() ?: fallback,
            stops = current.gradient.stops.ifEmpty { current.ledState.zoneColors.ifEmpty { listOf(fallback) } },
        )
    }

    private fun persistGradient() {
        val current = mutableState.value
        val deviceId = current.detected?.id ?: return
        gradientPreferences.save(
            deviceId,
            DeviceGradientPreferences(
                mode = current.gradient.mode,
                currentStops = current.gradient.stops,
                lastPresetId = current.gradient.selectedPresetId,
                savedGradients = current.gradient.savedGradients,
            ),
        )
    }

    private fun persistLighting() {
        val current = mutableState.value
        val deviceId = current.detected?.id ?: return
        lightingPreferences.save(
            deviceId,
            StoredLighting(
                mode = current.mode,
                effectId = current.effectId,
                speed = current.speed,
                gradientSpeed = current.gradientSpeed,
                effectUsesGradient = current.effectUsesGradient,
                solidColor = current.ledState.zoneColors.firstOrNull() ?: RgbColor(93, 81, 255),
                brightness = current.ledState.brightness,
                power = current.ledState.power,
                chargerOnly = current.chargerOnly,
                batteryBreathe = current.batteryBreathe,
                temperatureBreathe = current.temperatureBreathe,
                audioScale = current.audioScale,
                audioSensitivityDb = current.audioSensitivityDb,
                sensorBands = current.sensorBands,
            ),
        )
    }
}

private const val COLOR_COMMIT_DEBOUNCE_MS = 120L

private fun AppMode.coerceAvailable(gradientSupported: Boolean): AppMode =
    if (this == AppMode.GRADIENT && !gradientSupported) AppMode.COLOR else this

private fun ColoresUiState.gradientStopCount(): Int =
    gradientPresentation?.editorStopCount(detected?.capabilities?.zones ?: 2) ?: 2

private fun android.content.Context.readAsset(name: String): String =
    assets.open(name).bufferedReader().use { it.readText() }
