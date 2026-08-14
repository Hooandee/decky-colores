package com.hooandee.colores.device.learning

internal const val SETTINGS_PROBE_ID = "settings-pserver-joystick"
internal const val SINGLEADC_PROBE_ID = "singleadc-joypad"
internal const val SYSFS_PROBE_ID = "android-sysfs-multicolor"
internal const val PROBE_VERSION = 1

interface ProbeCartridge {
    val id: String
    val version: Int
    val surface: ProbeSurface

    fun accepts(candidate: ProbeCandidate): Boolean

    fun snapshot(candidate: ProbeCandidate): ProbeSnapshot?

    fun supportedSteps(candidate: ProbeCandidate): List<ProbeStep>

    fun execute(
        candidate: ProbeCandidate,
        step: ProbeStep,
        zone: Int? = null,
    ): Boolean

    fun restore(
        candidate: ProbeCandidate,
        snapshot: ProbeSnapshot,
    ): RollbackStatus

    fun bindingCandidate(
        candidate: ProbeCandidate,
        evidence: List<ProbeEvidence>,
    ): ProbeCandidate = candidate

    fun canBind(
        candidate: ProbeCandidate,
        capabilities: com.hooandee.colores.device.DeviceCapabilities,
        evidence: List<ProbeEvidence>,
    ): Boolean = capabilities.color
}

internal inline fun <K, V> Map<K, V>.attemptAll(action: (K, V) -> Boolean): Boolean {
    var succeeded = true
    forEach { (key, value) ->
        if (!action(key, value)) succeeded = false
    }
    return succeeded
}

class ProbeCartridgeCatalog(
    cartridges: List<ProbeCartridge>,
) {
    val all: List<ProbeCartridge> = cartridges.filter { it.id in ALLOWED_IDS }

    fun find(
        id: String,
        version: Int,
    ): ProbeCartridge? = all.firstOrNull { it.id == id && it.version == version }

    private companion object {
        val ALLOWED_IDS = setOf(SETTINGS_PROBE_ID, SINGLEADC_PROBE_ID, SYSFS_PROBE_ID, HTR3212_PROBE_ID)
    }
}
