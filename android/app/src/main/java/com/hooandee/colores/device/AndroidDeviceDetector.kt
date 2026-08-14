package com.hooandee.colores.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.hooandee.colores.device.learning.DetectionOutcome
import com.hooandee.colores.device.learning.LearnedDeviceBinding
import com.hooandee.colores.device.learning.HardwareLearningGraph
import com.hooandee.colores.device.learning.Htr3212InformationCartridge
import com.hooandee.colores.device.learning.InformationCartridge
import com.hooandee.colores.device.learning.resolveLearnedDevice
import com.hooandee.colores.device.learning.resolveDetectionOutcome
import com.hooandee.colores.led.AndroidPServerCommandExecutor
import com.hooandee.colores.led.LedDescriptor
import com.hooandee.colores.led.SettingsProviderDescriptor
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SysfsRgbDescriptor
import java.util.concurrent.TimeUnit

data class AndroidDeviceIdentity(
    val model: String,
    val device: String,
    val manufacturer: String,
    val productProperties: Map<String, String>,
)

data class DetectedAndroidDevice(
    val id: String,
    val friendlyName: String,
    val capabilities: DeviceCapabilities,
    val led: LedDescriptor,
    val previewProfileId: String?,
    val previewCalibration: LedPreviewCalibration?,
    val gridLayout: List<LedGridCell>? = null,
)

class AndroidDeviceDetector(
    private val context: Context,
    private val pserverAvailable: () -> Boolean = { AndroidPServerCommandExecutor().available },
    private val readSetting: (String) -> String? = { key -> Settings.System.getString(context.contentResolver, key) },
    private val scanJoypad: () -> SingleAdcJoypadDescriptor? = { SingleAdcJoypadDiscovery.scan() },
    private val scanSysfs: () -> SysfsRgbDescriptor? = { SysfsRgbDiscovery.scan() },
    private val informationCartridges: List<InformationCartridge> = listOf(Htr3212InformationCartridge()),
) {
    fun readIdentity(): AndroidDeviceIdentity {
        val properties =
            PRODUCT_PROPERTIES.associateWith(::readProperty).filterValues { it.isNotBlank() }
        return AndroidDeviceIdentity(
            model = Build.MODEL.orEmpty().ifBlank { properties["ro.product.model"].orEmpty() },
            device = Build.DEVICE.orEmpty().ifBlank { properties["ro.product.device"].orEmpty() },
            manufacturer =
                Build.MANUFACTURER.orEmpty().ifBlank { properties["ro.product.manufacturer"].orEmpty() },
            productProperties = properties,
        )
    }

    fun detectOutcome(binding: LearnedDeviceBinding? = null): DetectionOutcome {
        val identity =
            runCatching { readIdentity() }.getOrElse {
                AndroidDeviceIdentity(model = "", device = "", manufacturer = "", productProperties = emptyMap())
            }
        val pserver = runCatching { pserverAvailable() }.getOrDefault(false)
        val exact = modelMatch(identity)
        val exactTransportAvailable = exact?.led?.isTransportAvailable(pserver) ?: false
        if (!shouldCollectGenericCandidates(exact, exactTransportAvailable)) {
            return DetectionOutcome.Resolved(identity, requireNotNull(exact))
        }
        val seedCandidates =
            listOfNotNull(
                GenericLedResolver.settingsCandidate(
                    pserverAvailable = pserver,
                    colorKeyValue = if (pserver) runCatching { readSetting(GenericVendorLed.COLOR_KEY) }.getOrNull() else null,
                ),
                GenericLedResolver.joypadCandidate(runCatching { scanJoypad() }.getOrNull()),
                GenericLedResolver.sysfsCandidate(runCatching { scanSysfs() }.getOrNull()),
            )
        val route = HardwareLearningGraph(informationCartridges).resolve(identity, seedCandidates)
        val candidates = route.candidates
        val learned = resolveLearnedDevice(identity, binding, candidates)
        return resolveDetectionOutcome(
            identity = identity,
            exact = exact,
            exactTransportAvailable = exactTransportAvailable,
            candidates = candidates,
            learned = learned,
            facts = route.facts,
        )
    }

    fun detect(binding: LearnedDeviceBinding? = null): DetectedAndroidDevice? =
        (detectOutcome(binding) as? DetectionOutcome.Resolved)?.device

    private fun modelMatch(identity: AndroidDeviceIdentity): DetectedAndroidDevice? =
        runCatching {
            DeviceRegistry.parse(
                devicesJson = context.readAsset("devices.json"),
                previewProfilesJson = context.readAsset("led-preview-profiles.json"),
            ).match(identity)
        }.getOrNull()

    private fun readProperty(name: String): String =
        runCatching {
            val process = ProcessBuilder("/system/bin/getprop", name).redirectErrorStream(true).start()
            if (!process.waitFor(300, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                return@runCatching ""
            }
            process.inputStream.bufferedReader().use { it.readText().trim() }
        }.getOrDefault("")

    private companion object {
        val PRODUCT_PROPERTIES =
            listOf(
                "ro.product.model",
                "ro.product.device",
                "ro.product.manufacturer",
                "ro.product.brand",
                "ro.product.name",
                "ro.product.board",
                "ro.board.platform",
            )
    }
}

internal fun shouldCollectGenericCandidates(
    exact: DetectedAndroidDevice?,
    exactTransportAvailable: Boolean,
): Boolean = exact == null || !exactTransportAvailable

private fun LedDescriptor.isTransportAvailable(pserverAvailable: Boolean): Boolean =
    this !is SettingsProviderDescriptor || transport != "pserver" || pserverAvailable

private fun Context.readAsset(name: String): String = assets.open(name).bufferedReader().use { it.readText() }
