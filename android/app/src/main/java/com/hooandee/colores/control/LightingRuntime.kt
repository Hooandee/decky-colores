package com.hooandee.colores.control

import android.content.Context
import com.hooandee.colores.ambient.AmbientCaptureStatus
import com.hooandee.colores.ambient.MutableAmbientFrameSource
import com.hooandee.colores.audio.AudioCaptureStatus
import com.hooandee.colores.audio.MutableAudioLevelSource
import com.hooandee.colores.device.AndroidDeviceDetector
import com.hooandee.colores.device.learning.DetectionOutcome
import com.hooandee.colores.device.learning.HardwareLearningStore
import com.hooandee.colores.device.learning.learnedDeviceIdForPromotion
import com.hooandee.colores.engine.BandSet
import com.hooandee.colores.engine.EffectCatalog
import com.hooandee.colores.gradient.GradientInterpolator
import com.hooandee.colores.gradient.GradientPreferences
import com.hooandee.colores.gradient.GradientPresentation
import com.hooandee.colores.gradient.editorStopCount
import com.hooandee.colores.gradient.gradientPresentation
import com.hooandee.colores.led.LedDeviceFactory
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.permission.WriteSettingsPermission
import com.hooandee.colores.sensor.AndroidBatterySource
import com.hooandee.colores.sensor.PerformanceSources
import com.hooandee.colores.sensor.SysfsThermalSource
import com.hooandee.colores.ui.ControlAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RestoredLightingBinding(
    val deviceId: String,
    val zones: Int,
    val gradientSupported: Boolean,
)

internal fun attachProfileRuntime(
    restored: RestoredLightingBinding?,
    attach: (RestoredLightingBinding) -> Unit,
): Boolean {
    restored ?: return false
    attach(restored)
    return true
}

class LightingRuntime(
    private val context: Context,
    private val scope: CoroutineScope,
    private val controller: LightingController,
    private val audio: MutableAudioLevelSource,
    private val ambient: MutableAmbientFrameSource,
) {
    suspend fun restoreSaved(): RestoredLightingBinding? =
        withContext(Dispatchers.IO) {
            val preferences = LightingPreferences(context)
            val binding = HardwareLearningStore(context).loadBinding()
            val outcome = AndroidDeviceDetector(context).detectOutcome(binding)
            val detected = (outcome as? DetectionOutcome.Resolved)?.device ?: return@withContext null
            learnedDeviceIdForPromotion(outcome.identity, detected, binding)?.let { sourceDeviceId ->
                DevicePreferenceMigration(context).migrate(sourceDeviceId, detected.id)
            }
            val activeDeviceId = preferences.activeDeviceId() ?: return@withContext null
            if (detected.id != activeDeviceId) return@withContext null

            val device =
                LedDeviceFactory.create(
                    context,
                    detected.led,
                    scope = CoroutineScope(scope.coroutineContext + Dispatchers.IO),
                ) ?: return@withContext null
            val access =
                ControlAccess.resolve(
                    descriptor = detected.led,
                    deviceAvailable = device.available,
                    userPermissionGranted = WriteSettingsPermission.canWrite(context),
                )
            if (access != ControlAccess.ENABLED) return@withContext null

            val catalog = EffectCatalog.parse(context.readAsset("effects.json"))
            val bands = BandSet.parse(context.readAsset("bands.json"))
            val stored = preferences.load(detected.id, bands)
            val supportedPresentation = detected.capabilities.gradientPresentation(device.supportsPerZone)
            val presentation = supportedPresentation ?: GradientPresentation.SPATIAL
            val zones = detected.capabilities.zones.coerceAtLeast(1)
            val storedStops = GradientPreferences(context).load(detected.id).currentStops
            val sourceStops = storedStops.ifEmpty { listOf(stored.solidColor, stored.solidColor) }
            val editorStops = presentation.editorStopCount(zones)
            val gradientStops = GradientInterpolator.interpolate(sourceStops, editorStops)
            val zoneColors = GradientInterpolator.interpolate(gradientStops, zones)
            val liveState = runCatching { device.readState() }.getOrNull()
            val supportedEffectIds =
                if (device.hardwareEffects.isNotEmpty()) {
                    device.hardwareEffects.mapTo(mutableSetOf()) { it.id }
                } else {
                    catalog.presets.mapTo(mutableSetOf()) { it.id }
                }
            val mode =
                if (stored.mode == AppMode.GRADIENT && supportedPresentation == null) {
                    AppMode.COLOR
                } else {
                    stored.mode
                }
            if (mode == AppMode.AUDIO) audio.reset(AudioCaptureStatus.AUTHORIZATION_REQUIRED)
            if (mode == AppMode.AMBIENT) ambient.reset(AmbientCaptureStatus.AUTHORIZATION_REQUIRED)

            controller.bind(
                LightingBinding(
                    deviceId = detected.id,
                    device = device,
                    zones = zones,
                    catalog = catalog,
                    bands = stored.sensorBands,
                    battery = AndroidBatterySource(context),
                    temperature = SysfsThermalSource().takeIf { it.available },
                    performance = PerformanceSources.detect(),
                    audio = audio,
                    ambient = ambient,
                ),
                LightingIntent(
                    mode = mode,
                    staticColors = zoneColors,
                    solidColor = zoneColors.firstOrNull() ?: RgbColor(93, 81, 255),
                    gradientStops = gradientStops,
                    effectId = stored.effectId.takeIf { it in supportedEffectIds } ?: catalog.defaultEffectId,
                    speed = stored.speed,
                    gradientSpeed = stored.gradientSpeed,
                    gradientPresentation = presentation,
                    effectUsesGradient = stored.effectUsesGradient,
                    brightness = stored.brightness ?: liveState?.brightness ?: 100,
                    power = stored.power ?: liveState?.power ?: true,
                    chargerOnly = stored.chargerOnly,
                    batteryBreathe = stored.batteryBreathe,
                    temperatureBreathe = stored.temperatureBreathe,
                    audioScale = stored.audioScale,
                    audioSensitivityDb = stored.audioSensitivityDb,
                    ambientVividness = stored.ambientVividness,
                    ambientSmoothing = stored.ambientSmoothing,
                ),
            )
            RestoredLightingBinding(
                deviceId = detected.id,
                zones = zones,
                gradientSupported = supportedPresentation != null,
            )
        }
}

private fun Context.readAsset(name: String): String =
    assets.open(name).bufferedReader().use { it.readText() }
