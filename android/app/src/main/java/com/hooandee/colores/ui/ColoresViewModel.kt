package com.hooandee.colores.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import com.hooandee.colores.ambient.AmbientCaptureConfig
import com.hooandee.colores.ambient.AmbientCaptureStatus
import com.hooandee.colores.ambient.AmbientFrameState
import com.hooandee.colores.ambient.AmbientSamplingMode
import com.hooandee.colores.ambient.keepsCaptureActive
import com.hooandee.colores.ambient.needsAuthorization
import com.hooandee.colores.ambient.normalizedAmbientCaptureFps
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hooandee.colores.ColoresApplication
import com.hooandee.colores.audio.AudioCaptureStatus
import com.hooandee.colores.audio.AudioLevelState
import com.hooandee.colores.apps.LaunchableApp
import com.hooandee.colores.apps.LaunchableAppCatalog
import com.hooandee.colores.control.AppMode
import com.hooandee.colores.control.LightingBinding
import com.hooandee.colores.control.LightingController
import com.hooandee.colores.control.LightingIntent
import com.hooandee.colores.control.DevicePreferenceMigration
import com.hooandee.colores.control.LightingPreferences
import com.hooandee.colores.control.StoredLighting
import com.hooandee.colores.device.AndroidDeviceDetector
import com.hooandee.colores.device.AndroidDeviceIdentityCatalog
import com.hooandee.colores.device.DetectedAndroidDevice
import com.hooandee.colores.device.DevicePresentation
import com.hooandee.colores.device.learning.DetectionOutcome
import com.hooandee.colores.device.learning.HardwareLearningAttempt
import com.hooandee.colores.device.learning.HardwareLearningResult
import com.hooandee.colores.device.learning.HardwareLearningSession
import com.hooandee.colores.device.learning.HardwareLearningState
import com.hooandee.colores.device.learning.HardwareLearningStatus
import com.hooandee.colores.device.learning.LearningBlockReason
import com.hooandee.colores.device.learning.ProbeCandidate
import com.hooandee.colores.device.learning.RollbackStatus
import com.hooandee.colores.device.learning.UserObservation
import com.hooandee.colores.device.learning.ZoneLocation
import com.hooandee.colores.device.learning.learningIdentityHash
import com.hooandee.colores.device.learning.learningDescriptorsCompatible
import com.hooandee.colores.device.learning.learnedDeviceIdForPromotion
import com.hooandee.colores.device.learning.resultsFor
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
import com.hooandee.colores.profiles.ConfiguredProfile
import com.hooandee.colores.profiles.LightingProfile
import com.hooandee.colores.profiles.LightingProfileCoordinator
import com.hooandee.colores.profiles.LightingProfileStore
import com.hooandee.colores.profiles.ProfileAutomationStatus
import com.hooandee.colores.profiles.ProfilePatch
import com.hooandee.colores.profiles.ProfileScope
import com.hooandee.colores.profiles.ProfileScopeState
import com.hooandee.colores.report.AndroidReportSnapshot
import com.hooandee.colores.report.ReportSender
import com.hooandee.colores.report.ReportSubmissionState
import com.hooandee.colores.report.buildReportBundleForSubmission
import com.hooandee.colores.sensor.AndroidBatterySource
import com.hooandee.colores.sensor.PerformanceMetric
import com.hooandee.colores.sensor.PerformanceSources
import com.hooandee.colores.sensor.SysfsThermalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
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
    val detectionOutcome: DetectionOutcome? = null,
    val devicePresentation: DevicePresentation = DevicePresentation.UNKNOWN,
    val learnedHardware: Boolean = false,
    val hardwareLearning: HardwareLearningUiState = HardwareLearningUiState(),
    val controlAccess: ControlAccess = ControlAccess.SERVICE_UNAVAILABLE,
    val mode: AppMode = AppMode.COLOR,
    val profileStoredMode: AppMode = AppMode.COLOR,
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
    val ambient: AmbientFrameState = AmbientFrameState(),
    val ambientCaptureFps: Int = 10,
    val ambientSamplingMode: AmbientSamplingMode = AmbientSamplingMode.FULL_SCENE,
    val ambientVividness: Int = 35,
    val ambientSmoothing: Int = 45,
    val profileScope: ProfileScope = ProfileScope.Global,
    val profileApps: List<LaunchableApp> = emptyList(),
    val configuredProfiles: List<ConfiguredProfile> = emptyList(),
    val profilePickerOpen: Boolean = false,
    val profileScopeState: ProfileScopeState = ProfileScopeState(false, true),
    val automationEnabled: Boolean = false,
    val automationStatus: ProfileAutomationStatus = ProfileAutomationStatus.DISABLED,
    val reportSubmission: ReportSubmissionState = ReportSubmissionState(),
) {
    val canWrite: Boolean
        get() = controlAccess == ControlAccess.ENABLED

    val hardwareLearningCandidates: List<ProbeCandidate>
        get() =
            when (val outcome = detectionOutcome) {
                is DetectionOutcome.Candidates -> outcome.candidates
                is DetectionOutcome.UnavailableKnownDevice -> outcome.candidates
                is DetectionOutcome.Resolved ->
                    outcome.candidates
                        .takeIf { learnedHardware }
                        .orEmpty()
                        .filterNot { learningDescriptorsCompatible(it.descriptor, outcome.device.led) }
                is DetectionOutcome.Unsupported, null -> emptyList()
            }

    val hasHardwareLearningCandidates: Boolean
        get() = hardwareLearningCandidates.isNotEmpty()

    val hardwareLearningNeedsReport: Boolean
        get() =
            detected == null &&
                hardwareLearning.results.isNotEmpty() &&
                hardwareLearning.results.none { it.status == HardwareLearningStatus.ADAPTED }

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

    val ambientNeedsAuthorization: Boolean
        get() = ambient.status.needsAuthorization

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
            if (colorEnabled) add(AppMode.AMBIENT)
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

    private val coloresApplication = application as ColoresApplication
    private val controller: LightingController = coloresApplication.lightingController
    private val profileStore: LightingProfileStore = coloresApplication.profileStore
    private val profileCoordinator: LightingProfileCoordinator = coloresApplication.profileCoordinator
    private val reportSender = ReportSender(application)
    private val appCatalog = LaunchableAppCatalog(application)
    private var refreshJob: Job? = null
    private var commitJob: Job? = null
    private val ledPreviewPreferences = LedPreviewPreferences(application)
    private val gradientPreferences = GradientPreferences(application)
    private val lightingPreferences = LightingPreferences(application)
    private val deviceIdentityCatalog =
        AndroidDeviceIdentityCatalog.parse(runCatching { application.readAsset("android-device-identities.json") }.getOrDefault(""))
    private val gradientPresets = GradientPresetRepository(application).load()
    private var hardwareLearningSession: HardwareLearningSession? = null
    private var hardwareLearningOperation: Job? = null

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
                        ambient = snap.ambient,
                        ambientVividness = snap.ambientVividness,
                        ambientSmoothing = snap.ambientSmoothing,
                        ledState = current.ledState.copy(power = snap.powerRequested, brightness = snap.brightness),
                    )
                }
            }
        }
        viewModelScope.launch {
            profileCoordinator.state.collect { runtime ->
                mutableState.update {
                    it.copy(
                        automationEnabled = runtime.automationEnabled,
                        automationStatus = runtime.automationStatus,
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        if (mutableState.value.hardwareLearning.dialogOpen) return
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                val context = coloresApplication
                val rollbackStatus = context.recoverHardwareLearningRollback()
                if (rollbackStatus == RollbackStatus.RESTORE_FAILED) {
                    controller.unbind()
                    mutableState.update {
                        it.copy(
                            loading = false,
                            detected = null,
                            detectionOutcome = null,
                            controlAccess = ControlAccess.SERVICE_UNAVAILABLE,
                            hardwareLearning =
                                it.hardwareLearning.copy(
                                    dialogOpen = true,
                                    restoreFailure = true,
                                    sessionState = HardwareLearningState.Blocked(LearningBlockReason.RESTORE_FAILED),
                                ),
                        )
                    }
                    return@launch
                }
                val binding = withContext(Dispatchers.IO) { context.hardwareLearningStore.loadBinding() }
                val outcome = withContext(Dispatchers.IO) { AndroidDeviceDetector(context).detectOutcome(binding) }
                val detected =
                    when (outcome) {
                        is DetectionOutcome.Resolved -> outcome.device
                        is DetectionOutcome.UnavailableKnownDevice -> outcome.device
                        is DetectionOutcome.Candidates, is DetectionOutcome.Unsupported -> null
                    }
                if (outcome is DetectionOutcome.Resolved && detected != null) {
                    learnedDeviceIdForPromotion(outcome.identity, detected, binding)?.let { sourceDeviceId ->
                        withContext(Dispatchers.IO) {
                            DevicePreferenceMigration(context).migrate(sourceDeviceId, detected.id)
                        }
                    }
                }
                val learnedHardware = detected?.id?.startsWith("learned-") == true
                val storedAttempt = withContext(Dispatchers.IO) { context.hardwareLearningStore.loadAttempt() }
                val storedResults = storedAttempt?.resultsFor(outcome.identity).orEmpty()
                if (detected != null && storedAttempt != null) {
                    withContext(Dispatchers.IO) { context.hardwareLearningStore.clearAttempt() }
                }
                val devicePresentation = deviceIdentityCatalog.resolve(outcome.identity, detected)
                val userPermissionGranted = WriteSettingsPermission.canWrite(context)
                val applicationScope = coloresApplication.applicationScope
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
                        it.copy(
                            loading = false,
                            detected = detected,
                            detectionOutcome = outcome,
                            devicePresentation = devicePresentation,
                            learnedHardware = learnedHardware,
                            controlAccess = controlAccess,
                            hardwareLearning =
                                it.hardwareLearning.copy(
                                    results = if (it.hardwareLearning.results.isEmpty()) storedResults else it.hardwareLearning.results,
                                ),
                        )
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

                val legacyGradient =
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
                val brightness = storedLighting.brightness ?: liveState?.brightness ?: 100
                val power = storedLighting.power ?: liveState?.power ?: true
                profileStore.migrateIfMissing(
                    detected.id,
                    LightingProfile(
                        mode = storedLighting.mode,
                        effectId = storedLighting.effectId,
                        speed = storedLighting.speed,
                        gradientSpeed = storedLighting.gradientSpeed,
                        effectUsesGradient = storedLighting.effectUsesGradient,
                        solidColor = legacyGradient.stops.firstOrNull() ?: storedLighting.solidColor,
                        staticColors = legacyGradient.stops.ifEmpty { List(zones) { storedLighting.solidColor } },
                        gradientStops = legacyGradient.stops,
                        brightness = brightness,
                        batteryBreathe = storedLighting.batteryBreathe,
                        temperatureBreathe = storedLighting.temperatureBreathe,
                    ),
                )
                val selectedScope = mutableState.value.profileScope
                val selectedProfile =
                    when (selectedScope) {
                        ProfileScope.Global -> profileStore.global(detected.id)
                        is ProfileScope.App -> profileStore.effective(detected.id, selectedScope.packageName)
                    }
                val hydratedGradient =
                    legacyGradient.copy(
                        mode = if (selectedProfile.mode == AppMode.GRADIENT) LightingMode.GRADIENT else LightingMode.COLOR,
                        stops = selectedProfile.gradientStops,
                    )
                val zoneColors =
                    if (selectedProfile.mode == AppMode.GRADIENT) {
                        GradientInterpolator.interpolate(hydratedGradient.stops, zones)
                    } else {
                        GradientInterpolator.interpolate(selectedProfile.staticColors, zones)
                    }

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
                            audio = coloresApplication.audioLevelSource,
                            ambient = coloresApplication.ambientFrameSource,
                        ),
                        LightingIntent(
                            mode = selectedProfile.mode.coerceAvailable(gradientSupported),
                            staticColors = zoneColors,
                            solidColor = zoneColors.firstOrNull() ?: RgbColor(93, 81, 255),
                            gradientStops = hydratedGradient.stops,
                            effectId =
                                effectPresets.firstOrNull { it.id == selectedProfile.effectId }?.id
                                    ?: effectPresets.firstOrNull()?.id
                                    ?: catalog.defaultEffectId,
                            speed = selectedProfile.speed,
                            gradientSpeed = selectedProfile.gradientSpeed,
                            gradientPresentation = gradientPresentation ?: GradientPresentation.SPATIAL,
                            effectUsesGradient = selectedProfile.effectUsesGradient,
                            brightness = selectedProfile.brightness,
                            power = power,
                            chargerOnly = storedLighting.chargerOnly,
                            batteryBreathe = selectedProfile.batteryBreathe,
                            temperatureBreathe = selectedProfile.temperatureBreathe,
                            audioScale = storedLighting.audioScale,
                            audioSensitivityDb = storedLighting.audioSensitivityDb,
                            ambientVividness = storedLighting.ambientVividness,
                            ambientSmoothing = storedLighting.ambientSmoothing,
                        ),
                    )
                }

                profileCoordinator.bindDevice(detected.id, zones, gradientSupported)
                profileCoordinator.refreshAccess()
                profileCoordinator.beginPreview(selectedScope)
                val configured = profileStore.configuredPackages(detected.id)
                val configuredProfiles = profileStore.configuredProfiles(detected.id)
                val apps = withContext(Dispatchers.IO) { appCatalog.load(configured) }
                val scopeState =
                    when (selectedScope) {
                        ProfileScope.Global -> ProfileScopeState(false, true)
                        is ProfileScope.App -> profileStore.scopeState(detected.id, selectedScope.packageName)
                    }

                mutableState.update { current ->
                    current.copy(
                        loading = false,
                        detected = detected,
                        detectionOutcome = outcome,
                        devicePresentation = devicePresentation,
                        learnedHardware = learnedHardware,
                        controlAccess = controlAccess,
                        hardwareLearning = current.hardwareLearning.copy(results = emptyList()),
                        effects = effectPresets,
                        softwareEffectIds = catalog.presets.mapTo(mutableSetOf()) { it.id },
                        mode = selectedProfile.mode.coerceAvailable(gradientSupported),
                        profileStoredMode = selectedProfile.mode,
                        effectId = selectedProfile.effectId,
                        speed = selectedProfile.speed,
                        gradientSpeed = selectedProfile.gradientSpeed,
                        effectUsesGradient = selectedProfile.effectUsesGradient,
                        ledState = LedState(zoneColors, selectedProfile.brightness, power),
                        gradientPresentation = gradientPresentation,
                        gradient = hydratedGradient,
                        batteryBreathe = selectedProfile.batteryBreathe,
                        temperatureBreathe = selectedProfile.temperatureBreathe,
                        sensorBandDefaults = bands,
                        sensorBands = storedLighting.sensorBands,
                        audioScale = storedLighting.audioScale,
                        audioSensitivityDb = storedLighting.audioSensitivityDb,
                        ambientCaptureFps = storedLighting.ambientCaptureFps,
                        ambientSamplingMode = storedLighting.ambientSamplingMode,
                        ambientVividness = storedLighting.ambientVividness,
                        ambientSmoothing = storedLighting.ambientSmoothing,
                        profileApps = apps,
                        configuredProfiles = configuredProfiles,
                        profileScopeState = scopeState,
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

    fun onAppBackground() {
        profileCoordinator.endPreview()
        if (mutableState.value.hardwareLearning.dialogOpen) dismissHardwareLearning()
    }

    fun openHardwareLearning() {
        val current = mutableState.value
        val candidates = current.hardwareLearningCandidates
        val identity = current.detectionOutcome?.identity ?: return
        if (candidates.isEmpty() || current.hardwareLearning.dialogOpen) return
        val pendingRefresh = refreshJob
        mutableState.update {
            it.copy(
                hardwareLearning =
                    HardwareLearningUiState(
                        dialogOpen = true,
                        busy = true,
                        candidateIndex = 0,
                        candidateCount = candidates.size,
                    ),
            )
        }
        profileCoordinator.endPreview()
        hardwareLearningOperation =
            viewModelScope.launch {
                pendingRefresh?.cancelAndJoin()
                val claimed =
                    coloresApplication.hardwareLearningCoordinator.begin {
                        controller.unbindAndAwait()
                        true
                    }
                if (!claimed) {
                    mutableState.update {
                        it.copy(
                            hardwareLearning =
                                it.hardwareLearning.copy(
                                    busy = false,
                                    sessionState = HardwareLearningState.Blocked(LearningBlockReason.UNSUPPORTED_CANDIDATE),
                                ),
                        )
                    }
                    return@launch
                }
                val session =
                    HardwareLearningSession(
                        identity = identity,
                        catalog = coloresApplication.hardwareLearningCatalog,
                        store = coloresApplication.hardwareLearningStore,
                        appVersion = applicationVersion(),
                    )
                withContext(Dispatchers.IO) { coloresApplication.hardwareLearningStore.clearAttempt() }
                hardwareLearningSession = session
                mutableState.update {
                    it.copy(hardwareLearning = it.hardwareLearning.copy(busy = false, sessionState = session.start(candidates.first())))
                }
            }
    }

    fun confirmHardwareLearningConsent() {
        val session = hardwareLearningSession ?: return
        runHardwareLearningOperation { session.consent() }
    }

    fun runHardwareLearningProbe() {
        val request = mutableState.value.hardwareLearning.nextProbe ?: return
        val session = hardwareLearningSession ?: return
        runHardwareLearningOperation {
            session.run(request.step, request.zone)
            session.state
        }
    }

    fun answerHardwareLearning(
        observation: UserObservation,
        location: ZoneLocation?,
    ) {
        val session = hardwareLearningSession ?: return
        runHardwareLearningOperation { session.answer(observation, location) }
    }

    fun finishHardwareLearningCandidate() {
        val session = hardwareLearningSession ?: return
        val ui = mutableState.value.hardwareLearning
        val identity = mutableState.value.detectionOutcome?.identity ?: return
        if (!ui.canFinish || ui.busy) return
        mutableState.update { it.copy(hardwareLearning = it.hardwareLearning.copy(busy = true)) }
        hardwareLearningOperation =
            viewModelScope.launch {
                val result =
                    coloresApplication.hardwareLearningCoordinator.run {
                        withContext(Dispatchers.IO) { session.finish() }
                    } ?: return@launch
                val terminal =
                    !ui.hasNextCandidate ||
                        result.status == HardwareLearningStatus.RESTORE_FAILED
                val accumulatedResults = ui.results + result
                withContext(Dispatchers.IO) {
                    if (result.status == HardwareLearningStatus.ADAPTED) {
                        coloresApplication.hardwareLearningStore.clearAttempt()
                    } else {
                        coloresApplication.hardwareLearningStore.saveAttempt(
                            HardwareLearningAttempt(
                                identityHash = learningIdentityHash(identity),
                                results = accumulatedResults,
                                appVersion = applicationVersion(),
                                completedAtEpochMs = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
                if (terminal) {
                    coloresApplication.hardwareLearningCoordinator.finish { Unit }
                    hardwareLearningSession = null
                }
                mutableState.update {
                    it.copy(
                        hardwareLearning =
                            it.hardwareLearning.copy(
                                busy = false,
                                sessionState = session.state,
                                results = accumulatedResults,
                                restoreFailure =
                                    it.hardwareLearning.restoreFailure ||
                                        result.status == HardwareLearningStatus.RESTORE_FAILED,
                            ),
                    )
                }
            }
    }

    fun tryNextHardwareCandidate() {
        val session = hardwareLearningSession ?: return
        val current = mutableState.value
        val ui = current.hardwareLearning
        if (!ui.hasNextCandidate || ui.busy) return
        val nextIndex = ui.candidateIndex + 1
        val candidate = current.hardwareLearningCandidates.getOrNull(nextIndex) ?: return
        val sessionState = session.start(candidate)
        mutableState.update {
            it.copy(hardwareLearning = it.hardwareLearning.copy(candidateIndex = nextIndex, sessionState = sessionState))
        }
    }

    fun dismissHardwareLearning() {
        val ui = mutableState.value.hardwareLearning
        if (!ui.canDismiss && !ui.busy) return
        val mayNeedRestore = ui.sessionState !is HardwareLearningState.Complete
        mutableState.update { it.copy(hardwareLearning = it.hardwareLearning.copy(busy = mayNeedRestore)) }
        val pending = hardwareLearningOperation
        hardwareLearningOperation =
            viewModelScope.launch {
                pending?.join()
                if (mutableState.value.hardwareLearning.restoreFailure) return@launch
                val needsRestore = mutableState.value.hardwareLearning.sessionState !is HardwareLearningState.Complete
                val session = hardwareLearningSession
                val cancellationStatus =
                    if (needsRestore) {
                        withContext(NonCancellable + Dispatchers.IO) {
                            coloresApplication.hardwareLearningCoordinator.finish { session?.cancel() }
                        }
                    } else {
                        coloresApplication.hardwareLearningCoordinator.finish { Unit }
                        null
                    }
                val cancellationState = session?.state ?: HardwareLearningState.Idle
                if (hardwareLearningCancellationFailed(cancellationStatus, cancellationState)) {
                    val restoreFailed = hardwareLearningRestoreFailed(cancellationStatus, cancellationState)
                    hardwareLearningSession = null
                    mutableState.update {
                        it.copy(
                            hardwareLearning =
                                it.hardwareLearning.copy(
                                    busy = false,
                                    restoreFailure = it.hardwareLearning.restoreFailure || restoreFailed,
                                    sessionState = cancellationState,
                                ),
                        )
                    }
                    return@launch
                }
                hardwareLearningSession = null
                mutableState.update {
                    it.copy(
                        hardwareLearning = dismissedHardwareLearningUiState(it.hardwareLearning),
                        loading = it.loading,
                    )
                }
                refresh()
            }
    }

    fun forgetLearnedHardware() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { coloresApplication.hardwareLearningStore.clearBinding() }
            withContext(Dispatchers.IO) { coloresApplication.hardwareLearningStore.clearAttempt() }
            controller.unbind()
            refresh()
        }
    }

    fun openHardwareLearningReport() {
        if (!mutableState.value.hardwareLearning.canOpenReport) return
        mutableState.update { it.copy(hardwareLearning = it.hardwareLearning.copy(reportOpen = true)) }
    }

    fun closeHardwareLearningReport() {
        mutableState.update { it.copy(hardwareLearning = it.hardwareLearning.copy(reportOpen = false)) }
        resetReport()
    }

    private fun runHardwareLearningOperation(operation: () -> HardwareLearningState) {
        if (mutableState.value.hardwareLearning.busy) return
        mutableState.update { it.copy(hardwareLearning = it.hardwareLearning.copy(busy = true)) }
        hardwareLearningOperation =
            viewModelScope.launch {
                val state =
                    coloresApplication.hardwareLearningCoordinator.run {
                        withContext(Dispatchers.IO) { operation() }
                    } ?: HardwareLearningState.Blocked(LearningBlockReason.UNSUPPORTED_CANDIDATE)
                val restoreFailed =
                    state is HardwareLearningState.Blocked && state.reason == LearningBlockReason.RESTORE_FAILED
                if (restoreFailed) {
                    coloresApplication.hardwareLearningCoordinator.finish { Unit }
                    hardwareLearningSession = null
                }
                mutableState.update {
                    it.copy(
                        hardwareLearning =
                            it.hardwareLearning.copy(
                                busy = false,
                                sessionState = state,
                                restoreFailure = it.hardwareLearning.restoreFailure || restoreFailed,
                            ),
                    )
                }
            }
    }

    private fun applicationVersion(): String =
        runCatching {
            getApplication<Application>().packageManager
                .getPackageInfo(getApplication<Application>().packageName, 0)
                .versionName
        }.getOrNull().orEmpty()

    fun openProfilePicker() {
        mutableState.update { it.copy(profilePickerOpen = true) }
    }

    fun closeProfilePicker() {
        mutableState.update { it.copy(profilePickerOpen = false) }
    }

    fun selectGlobalProfile() = selectProfileScope(ProfileScope.Global)

    fun selectAppProfile(packageName: String) = selectProfileScope(ProfileScope.App(packageName))

    fun setProfileAutomation(enabled: Boolean) {
        profileCoordinator.setAutomationEnabled(enabled)
    }

    fun refreshProfileAutomation() {
        profileCoordinator.refreshAccess()
    }

    fun submitReport(
        categories: List<String>,
        text: String,
    ) {
        if (text.isBlank() || mutableState.value.reportSubmission.sending) return
        val current = mutableState.value
        val detected = current.detected
        val descriptor = detected?.led
        val application = getApplication<Application>()
        val version =
            runCatching {
                application.packageManager
                    .getPackageInfo(application.packageName, 0)
                    .versionName
            }.getOrNull().orEmpty()
        val snapshot =
            AndroidReportSnapshot(
                appVersion = version,
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
                androidRelease = Build.VERSION.RELEASE.orEmpty(),
                sdk = Build.VERSION.SDK_INT,
                deviceId = detected?.id,
                deviceName = detected?.friendlyName,
                driver = descriptor?.diagnosticDriver(),
                transport = descriptor?.diagnosticRoute(),
                color = detected?.capabilities?.color == true,
                brightness = detected?.capabilities?.brightness == true,
                perZone = detected?.capabilities?.perZone == true,
                zones = detected?.capabilities?.zones ?: 0,
                controlStatus = current.controlAccess.name.lowercase(),
                mode = current.mode.name,
                brightnessValue = current.ledState.brightness,
                power = current.ledState.power,
                configuredProfiles = current.configuredProfiles.size,
                automationStatus = current.automationStatus.name.lowercase(),
                ambientStatus = current.ambient.status.name.lowercase(),
                ambientCaptureFps = current.ambientCaptureFps,
                ambientSamplingMode = current.ambientSamplingMode.name.lowercase(),
            )
        mutableState.update { it.copy(reportSubmission = ReportSubmissionState(sending = true)) }
        val bundle =
            buildReportBundleForSubmission(
                snapshot = snapshot,
                categories = categories,
                text = text,
                learningResults = current.hardwareLearning.results,
                restoreFailure = current.hardwareLearning.restoreFailure,
                criticalSafetyFailure = current.hardwareLearning.criticalBlockReason == LearningBlockReason.JOURNAL_UNAVAILABLE,
                learningFacts = current.detectionOutcome?.facts.orEmpty(),
            )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { reportSender.submit(bundle) }
            mutableState.update { it.copy(reportSubmission = ReportSubmissionState(result = result)) }
        }
    }

    fun resetReport() {
        mutableState.update { it.copy(reportSubmission = ReportSubmissionState()) }
    }

    fun setSelectedAppFollowGlobal(follow: Boolean) {
        val current = mutableState.value
        val scope = current.profileScope as? ProfileScope.App ?: return
        val scopeState = profileCoordinator.setFollowGlobal(scope.packageName, follow) ?: return
        val profile = profileCoordinator.selectedProfile(scope) ?: return
        applyProfileToState(scope, profile, scopeState)
        refreshConfiguredProfiles()
    }

    fun forgetSelectedAppProfile() {
        val scope = mutableState.value.profileScope as? ProfileScope.App ?: return
        profileCoordinator.forget(scope.packageName)
        val profile = profileCoordinator.selectedProfile(scope) ?: return
        applyProfileToState(scope, profile, ProfileScopeState(false, true))
        refreshConfiguredProfiles()
    }

    private fun selectProfileScope(scope: ProfileScope) {
        val current = mutableState.value
        val deviceId = current.detected?.id ?: return
        profileCoordinator.beginPreview(scope)
        val profile = profileCoordinator.selectedProfile(scope) ?: return
        val scopeState =
            when (scope) {
                ProfileScope.Global -> ProfileScopeState(false, true)
                is ProfileScope.App -> profileStore.scopeState(deviceId, scope.packageName)
            }
        applyProfileToState(scope, profile, scopeState)
    }

    private fun applyProfileToState(
        scope: ProfileScope,
        profile: LightingProfile,
        scopeState: ProfileScopeState,
    ) {
        mutableState.update { current ->
            val zones = current.detected?.capabilities?.zones ?: 1
            val gradientMode = profile.mode == AppMode.GRADIENT
            val colors =
                if (gradientMode) {
                    GradientInterpolator.interpolate(profile.gradientStops, zones)
                } else {
                    GradientInterpolator.interpolate(profile.staticColors, zones)
                }
            current.copy(
                profileScope = scope,
                profilePickerOpen = false,
                profileScopeState = scopeState,
                mode = profile.mode.coerceAvailable(current.gradientAvailable),
                profileStoredMode = profile.mode,
                effectId = profile.effectId,
                speed = profile.speed,
                gradientSpeed = profile.gradientSpeed,
                effectUsesGradient = profile.effectUsesGradient,
                ledState = current.ledState.copy(zoneColors = colors, brightness = profile.brightness),
                gradient =
                    current.gradient.copy(
                        mode = if (gradientMode) LightingMode.GRADIENT else LightingMode.COLOR,
                        stops = profile.gradientStops,
                        selectedStopIndex = 0,
                    ),
                batteryBreathe = profile.batteryBreathe,
                temperatureBreathe = profile.temperatureBreathe,
            )
        }
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
        if (target == current.mode && target == current.profileStoredMode) return
        if (target == AppMode.GRADIENT && !current.gradientAvailable) return
        if (current.mode == AppMode.AUDIO) {
            EffectsService.stopAudio(getApplication(), AudioCaptureStatus.AUTHORIZATION_REQUIRED)
        }
        if (current.mode == AppMode.AMBIENT) {
            EffectsService.stopAmbient(getApplication(), AmbientCaptureStatus.AUTHORIZATION_REQUIRED)
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
        mutableState.update { it.copy(mode = target, profileStoredMode = target) }
        pushColorsToController()
        persistLighting()
    }

    fun selectEffect(effectId: String) {
        val current = mutableState.value
        val preset = current.effects.firstOrNull { it.id == effectId } ?: return
        controller.setEffect(preset.id)
        if (preset.id != current.effectId) controller.setSpeed(preset.defaultSpeed)
        controller.setMode(AppMode.EFFECT)
        mutableState.update {
            it.copy(
                mode = AppMode.EFFECT,
                profileStoredMode = AppMode.EFFECT,
                effectId = preset.id,
                speed = if (preset.id != current.effectId) preset.defaultSpeed else it.speed,
            )
        }
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
        mutableState.update { it.copy(mode = mode, profileStoredMode = mode) }
        persistLighting()
    }

    fun setPower(power: Boolean) {
        if (!power) {
            when (mutableState.value.mode) {
                AppMode.AUDIO -> EffectsService.stopAudio(getApplication(), AudioCaptureStatus.AUTHORIZATION_REQUIRED)
                AppMode.AMBIENT -> EffectsService.stopAmbient(getApplication(), AmbientCaptureStatus.AUTHORIZATION_REQUIRED)
                else -> Unit
            }
        }
        controller.setPower(power)
        mutableState.update { it.copy(ledState = it.ledState.copy(power = power)) }
        persistGlobalModifiers()
    }

    fun activateAudio(
        resultCode: Int,
        resultData: Intent,
    ) {
        val current = mutableState.value
        if (!current.canWrite || !current.colorEnabled) return
        EffectsService.stopAmbient(getApplication(), AmbientCaptureStatus.AUTHORIZATION_REQUIRED)
        coloresApplication.audioLevelSource.reset(AudioCaptureStatus.STARTING)
        controller.setMode(AppMode.AUDIO)
        mutableState.update {
            it.copy(
                mode = AppMode.AUDIO,
                profileStoredMode = AppMode.AUDIO,
                audio = AudioLevelState(status = AudioCaptureStatus.STARTING),
            )
        }
        EffectsService.startAudio(getApplication(), resultCode, resultData)
        persistLighting()
    }

    fun onAudioAuthorizationDenied() {
        coloresApplication.audioLevelSource.reset(AudioCaptureStatus.AUTHORIZATION_REQUIRED)
        mutableState.update { it.copy(audio = AudioLevelState(status = AudioCaptureStatus.AUTHORIZATION_REQUIRED)) }
    }

    fun activateAmbient(
        resultCode: Int,
        resultData: Intent,
    ) {
        val current = mutableState.value
        if (current.detected == null) return
        if (!current.canWrite || !current.colorEnabled) return
        EffectsService.stopAudio(getApplication(), AudioCaptureStatus.AUTHORIZATION_REQUIRED)
        val config = current.ambientCaptureConfig() ?: return
        coloresApplication.ambientFrameSource.reset(AmbientCaptureStatus.STARTING)
        controller.setMode(AppMode.AMBIENT)
        mutableState.update {
            it.copy(
                mode = AppMode.AMBIENT,
                profileStoredMode = AppMode.AMBIENT,
                ambient = AmbientFrameState(status = AmbientCaptureStatus.STARTING),
            )
        }
        EffectsService.startAmbient(getApplication(), resultCode, resultData, config)
        persistLighting()
    }

    fun onAmbientAuthorizationDenied() {
        coloresApplication.ambientFrameSource.reset(AmbientCaptureStatus.AUTHORIZATION_REQUIRED)
        mutableState.update {
            it.copy(ambient = AmbientFrameState(status = AmbientCaptureStatus.AUTHORIZATION_REQUIRED))
        }
    }

    fun setAmbientCaptureFps(value: Int) {
        val fps = value.normalizedAmbientCaptureFps()
        mutableState.update { it.copy(ambientCaptureFps = fps) }
        updateActiveAmbientConfig()
        persistGlobalModifiers()
    }

    fun setAmbientSamplingMode(mode: AmbientSamplingMode) {
        mutableState.update { it.copy(ambientSamplingMode = mode) }
        updateActiveAmbientConfig()
        persistGlobalModifiers()
    }

    fun setAmbientVividness(value: Int) {
        val clamped = value.coerceIn(0, 100)
        controller.setAmbientVividness(clamped)
        mutableState.update { it.copy(ambientVividness = clamped) }
        persistGlobalModifiers()
    }

    fun setAmbientSmoothing(value: Int) {
        val clamped = value.coerceIn(0, 100)
        controller.setAmbientSmoothing(clamped)
        mutableState.update { it.copy(ambientSmoothing = clamped) }
        persistGlobalModifiers()
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
        persistGlobalModifiers()
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
        persistGlobalModifiers()
    }

    fun setAudioScale(scale: AudioScale) {
        controller.setAudioScale(scale)
        mutableState.update { it.copy(audioScale = scale) }
        persistGlobalModifiers()
    }

    fun setAudioSensitivity(gainDb: Int) {
        val clamped = gainDb.coerceIn(AudioSensitivity.MIN_DB, AudioSensitivity.MAX_DB)
        controller.setAudioSensitivity(clamped)
        mutableState.update { it.copy(audioSensitivityDb = clamped) }
        persistGlobalModifiers()
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
        val selected = current.gradient.selectStop(index)
        mutableState.update { it.copy(gradient = selected) }
        if (current.gradientAnimated) selected.selectedStop?.let(controller::setGradientEditingPreview)
    }

    fun setGradientEditing(editing: Boolean) {
        val current = mutableState.value
        if (editing && (!current.gradientAnimated || (current.mode != AppMode.GRADIENT && !current.effectNeedsGradient))) return
        controller.setGradientEditing(editing)
        if (editing) current.gradient.selectedStop?.let(controller::setGradientEditingPreview)
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
        scheduleCommit(debounce) {
            pushColorsToController()
            persistLighting()
        }
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
        if (current.gradientAnimated) gradient.selectedStop?.let(controller::setGradientEditingPreview)
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
        if (current.profileScope == ProfileScope.Global) {
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
        persistLighting()
    }

    private fun persistLighting() {
        val current = mutableState.value
        if (current.detected == null) return
        val solid = current.ledState.zoneColors.firstOrNull() ?: RgbColor(93, 81, 255)
        profileCoordinator.edit(
            current.profileScope,
            ProfilePatch(
                mode = current.profileStoredMode,
                effectId = current.effectId,
                speed = current.speed,
                gradientSpeed = current.gradientSpeed,
                effectUsesGradient = current.effectUsesGradient,
                solidColor = solid,
                staticColors = current.ledState.zoneColors,
                gradientStops = current.gradient.stops,
                brightness = current.ledState.brightness,
                batteryBreathe = current.batteryBreathe,
                temperatureBreathe = current.temperatureBreathe,
            ),
        )
        if (current.profileScope == ProfileScope.Global) persistGlobalModifiers()
        if (current.profileScope is ProfileScope.App) {
            mutableState.update { it.copy(profileScopeState = ProfileScopeState(true, false)) }
            refreshConfiguredProfiles()
        }
    }

    private fun refreshConfiguredProfiles() {
        val deviceId = mutableState.value.detected?.id ?: return
        mutableState.update { it.copy(configuredProfiles = profileStore.configuredProfiles(deviceId)) }
    }

    private fun persistGlobalModifiers() {
        val current = mutableState.value
        val deviceId = current.detected?.id ?: return
        val global = profileStore.global(deviceId)
        lightingPreferences.save(
            deviceId,
            StoredLighting(
                mode = global.mode,
                effectId = global.effectId,
                speed = global.speed,
                gradientSpeed = global.gradientSpeed,
                effectUsesGradient = global.effectUsesGradient,
                solidColor = global.solidColor,
                brightness = global.brightness,
                power = current.ledState.power,
                chargerOnly = current.chargerOnly,
                batteryBreathe = global.batteryBreathe,
                temperatureBreathe = global.temperatureBreathe,
                audioScale = current.audioScale,
                audioSensitivityDb = current.audioSensitivityDb,
                ambientCaptureFps = current.ambientCaptureFps,
                ambientSamplingMode = current.ambientSamplingMode,
                ambientVividness = current.ambientVividness,
                ambientSmoothing = current.ambientSmoothing,
                sensorBands = current.sensorBands,
            ),
        )
    }

    private fun updateActiveAmbientConfig() {
        val current = mutableState.value
        if (
            current.mode == AppMode.AMBIENT &&
            current.ambient.status.keepsCaptureActive
        ) {
            current.ambientCaptureConfig()?.let { EffectsService.updateAmbient(getApplication(), it) }
        }
    }
}

private const val COLOR_COMMIT_DEBOUNCE_MS = 120L

private fun AppMode.coerceAvailable(gradientSupported: Boolean): AppMode =
    if (this == AppMode.GRADIENT && !gradientSupported) AppMode.COLOR else this

private fun ColoresUiState.gradientStopCount(): Int =
    gradientPresentation?.editorStopCount(detected?.capabilities?.zones ?: 2) ?: 2

private fun ColoresUiState.ambientCaptureConfig(): AmbientCaptureConfig? {
    val device = detected ?: return null
    return AmbientCaptureConfig(
        zones = device.capabilities.zones,
        gridLayout = device.gridLayout,
        supportsPerZone = device.capabilities.perZone,
        captureFps = ambientCaptureFps,
        samplingMode = ambientSamplingMode,
    )
}

private fun android.content.Context.readAsset(name: String): String =
    assets.open(name).bufferedReader().use { it.readText() }
