package com.hooandee.colores.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.hooandee.colores.device.learning.DetectionOutcome
import com.hooandee.colores.device.learning.FACT_PSERVER
import com.hooandee.colores.device.learning.LearnedDeviceBinding
import com.hooandee.colores.device.learning.HardwareLearningGraph
import com.hooandee.colores.device.learning.HardwareFact
import com.hooandee.colores.device.learning.HardwareLearningRoute
import com.hooandee.colores.device.learning.Htr3212InformationCartridge
import com.hooandee.colores.device.learning.HTR3212_PROBE_ID
import com.hooandee.colores.device.learning.HTR3212_PROBE_VERSION
import com.hooandee.colores.device.learning.I2cController
import com.hooandee.colores.device.learning.I2cTopologyReader
import com.hooandee.colores.device.learning.FactEvidence
import com.hooandee.colores.device.learning.SysfsI2cTopologyReader
import com.hooandee.colores.device.learning.InformationCartridge
import com.hooandee.colores.device.learning.PROBE_VERSION
import com.hooandee.colores.device.learning.ProbeCandidate
import com.hooandee.colores.device.learning.ProbeSurface
import com.hooandee.colores.device.learning.SETTINGS_PROBE_ID
import com.hooandee.colores.device.learning.canActivateExactProfile
import com.hooandee.colores.device.learning.encodeLearningDescriptor
import com.hooandee.colores.device.learning.resolveLearnedDevice
import com.hooandee.colores.device.learning.resolveDetectionOutcome
import com.hooandee.colores.device.learning.usesTopologyGatedActivation
import com.hooandee.colores.led.AndroidPServerCommandExecutor
import com.hooandee.colores.led.LedDescriptor
import com.hooandee.colores.led.SettingsProviderCodec
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
    private val scanSysfs: () -> List<SysfsRgbDescriptor> = { SysfsRgbDiscovery.scanAll() },
    private val informationCartridges: List<InformationCartridge> = listOf(Htr3212InformationCartridge()),
    private val topologyReader: I2cTopologyReader = SysfsI2cTopologyReader(),
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
        return detectFromInputs(
            identity = identity,
            exact = modelMatch(identity),
            pserverAvailable = pserver,
            binding = binding,
            readTopology = { topologyReader.read() },
            seedCandidates = {
                listOfNotNull(
                    GenericLedResolver.settingsCandidate(
                        pserverAvailable = pserver,
                        colorKeyValue = if (pserver) runCatching { readSetting(GenericVendorLed.COLOR_KEY) }.getOrNull() else null,
                    ),
                    GenericLedResolver.joypadCandidate(runCatching { scanJoypad() }.getOrNull()),
                ) + GenericLedResolver.sysfsCandidates(runCatching { scanSysfs() }.getOrDefault(emptyList()))
            },
            informationCartridges = informationCartridges,
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

internal fun detectFromInputs(
    identity: AndroidDeviceIdentity,
    exact: DetectedAndroidDevice?,
    pserverAvailable: Boolean,
    binding: LearnedDeviceBinding?,
    readTopology: () -> List<I2cController>,
    seedCandidates: () -> List<ProbeCandidate>,
    informationCartridges: List<InformationCartridge>,
): DetectionOutcome {
    val guard = guardExactHtrTopology(exact) { runCatching(readTopology).getOrDefault(emptyList()) }
    val trustedExact = exact.takeUnless { guard.contradicted }
    val exactTransportAvailable = trustedExact?.led?.isTransportAvailable(pserverAvailable) ?: false
    if (!shouldCollectVerificationCandidates(trustedExact, exactTransportAvailable)) {
        return DetectionOutcome.Resolved(identity, requireNotNull(trustedExact), facts = guard.facts)
    }
    val (graphSeeds, deferredSeeds) = seedCandidates().partition { !it.usesNonDefaultSettingsFormat() }
    val route = resolveHardwareLearningRoute(identity, pserverAvailable, graphSeeds, informationCartridges)
    val facts = route.facts + guard.facts
    val candidates = verificationCandidates(route.candidates + deferredSeeds, trustedExact, exactTransportAvailable, facts)
    val learned = resolveLearnedDevice(identity, binding, candidates)
    return resolveDetectionOutcome(
        identity = identity,
        exact = trustedExact,
        exactTransportAvailable = exactTransportAvailable,
        candidates = candidates,
        learned = learned,
        facts = facts,
    )
}

internal const val FACT_HTR3212_TOPOLOGY = "controller.htr3212.topology"

internal data class ExactTopologyGuard(
    val contradicted: Boolean,
    val facts: List<HardwareFact>,
)

internal fun guardExactHtrTopology(
    exact: DetectedAndroidDevice?,
    readTopology: () -> List<I2cController>,
): ExactTopologyGuard {
    val hardware = (exact?.led as? SettingsProviderDescriptor)?.takeIf { it.driver == "htr3212" }?.htr3212
    if (hardware == null || hardware.automaticActivation) return ExactTopologyGuard(false, emptyList())
    val controllers = readTopology()
    if (controllers.isEmpty()) return ExactTopologyGuard(false, listOf(topologyFact("unverified")))
    val left = controllers.filter { it.driver == HTR_LEFT_DRIVER }
    val right = controllers.filter { it.driver == HTR_RIGHT_DRIVER }
    val matches =
        left.singleOrNull()?.let { it.bus == hardware.leftBus && it.address == hardware.address } == true &&
            right.singleOrNull()?.let { it.bus == hardware.rightBus && it.address == hardware.address } == true
    if (matches) return ExactTopologyGuard(false, listOf(topologyFact("matched")))
    val expected = "expected left=${hardware.leftBus}-0x%02x right=${hardware.rightBus}-0x%02x".format(hardware.address, hardware.address)
    val observed = "observed left=${left.describe()} right=${right.describe()}"
    return ExactTopologyGuard(true, listOf(topologyFact("contradicted; $expected; $observed")))
}

private fun ProbeCandidate.usesNonDefaultSettingsFormat(): Boolean =
    (descriptor as? SettingsProviderDescriptor)?.colorFormat?.let { it != SettingsProviderCodec.ARGB_HEX_CSV } == true

private fun List<I2cController>.describe(): String =
    if (isEmpty()) "none" else joinToString("|") { "${it.bus}-0x%02x".format(it.address) }

private fun topologyFact(value: String): HardwareFact =
    HardwareFact(FACT_HTR3212_TOPOLOGY, value, FactEvidence.OBSERVED, "android-detector")

private const val HTR_LEFT_DRIVER = "htr3212l"
private const val HTR_RIGHT_DRIVER = "htr3212r"

internal fun resolveHardwareLearningRoute(
    identity: AndroidDeviceIdentity,
    pserverAvailable: Boolean,
    seedCandidates: List<ProbeCandidate>,
    informationCartridges: List<InformationCartridge>,
): HardwareLearningRoute =
    HardwareLearningGraph(informationCartridges).resolve(
        identity = identity,
        seedCandidates = seedCandidates,
        seedFacts =
            if (pserverAvailable) {
                listOf(HardwareFact(FACT_PSERVER, "present", FactEvidence.OBSERVED, "android-detector"))
            } else {
                emptyList()
            },
    )

internal fun exactProfileCandidate(device: DetectedAndroidDevice): ProbeCandidate? {
    val descriptor = device.led as? SettingsProviderDescriptor ?: return null
    return when (descriptor.driver) {
        "settings_provider" ->
            ProbeCandidate(
                cartridgeId = SETTINGS_PROBE_ID,
                cartridgeVersion = PROBE_VERSION,
                surface = ProbeSurface.SETTINGS_PSERVER,
                descriptor = descriptor,
                signalKeys = setOf("exact_profile"),
            )
        "htr3212" ->
            descriptor.htr3212?.let {
                ProbeCandidate(
                    cartridgeId = HTR3212_PROBE_ID,
                    cartridgeVersion = HTR3212_PROBE_VERSION,
                    surface = ProbeSurface.HTR3212,
                    descriptor = descriptor,
                    signalKeys = setOf("exact_profile"),
                )
            }
        else -> null
    }
}

internal fun shouldCollectVerificationCandidates(
    exact: DetectedAndroidDevice?,
    exactTransportAvailable: Boolean,
): Boolean = exact == null || !exactTransportAvailable || exact.usesTopologyGatedActivation()

internal fun verificationCandidates(
    observed: List<ProbeCandidate>,
    exact: DetectedAndroidDevice?,
    exactTransportAvailable: Boolean,
    facts: List<HardwareFact>,
): List<ProbeCandidate> {
    val exactCandidate =
        exact
            ?.takeIf { exactTransportAvailable }
            ?.takeIf { !it.usesTopologyGatedActivation() || canActivateExactProfile(it, true, facts) }
            ?.let(::exactProfileCandidate)
    val withoutCompetingHtr =
        if (exactCandidate?.surface == ProbeSurface.HTR3212) observed.filterNot { it.surface == ProbeSurface.HTR3212 } else observed
    val ordered =
        buildList {
            addAll(withoutCompetingHtr.filter { it.surface == ProbeSurface.SETTINGS_PSERVER })
            exactCandidate?.let(::add)
            addAll(withoutCompetingHtr.filter { it.surface == ProbeSurface.HTR3212 })
            addAll(withoutCompetingHtr.filter { it.surface != ProbeSurface.SETTINGS_PSERVER && it.surface != ProbeSurface.HTR3212 })
        }
    return ordered.distinctBy { "${it.cartridgeId}:${it.cartridgeVersion}:${encodeLearningDescriptor(it.descriptor)}" }
}

private fun LedDescriptor.isTransportAvailable(pserverAvailable: Boolean): Boolean =
    this !is SettingsProviderDescriptor || transport != "pserver" || pserverAvailable

private fun Context.readAsset(name: String): String = assets.open(name).bufferedReader().use { it.readText() }
