package com.hooandee.colores.device.learning

import com.hooandee.colores.device.AndroidDeviceIdentity

internal const val FACT_SETTINGS_PSERVER = "surface.settings_pserver"
internal const val FACT_HTR3212_LEFT = "controller.htr3212.left"
internal const val FACT_HTR3212_RIGHT = "controller.htr3212.right"
internal const val FACT_HTR3212_PAIR = "controller.htr3212.pair"

enum class FactEvidence {
    OBSERVED,
    TRANSPORT_CONFIRMED,
    USER_CONFIRMED,
    REPEATED,
}

data class HardwareFact(
    val key: String,
    val value: String,
    val evidence: FactEvidence,
    val cartridgeId: String,
)

data class HardwareLearningContext(
    val identity: AndroidDeviceIdentity,
    val facts: List<HardwareFact>,
    val candidates: List<ProbeCandidate>,
) {
    fun hasFact(key: String): Boolean = facts.any { it.key == key }
}

data class InformationCartridgeResult(
    val facts: List<HardwareFact> = emptyList(),
    val candidates: List<ProbeCandidate> = emptyList(),
)

interface InformationCartridge {
    val id: String
    val version: Int
    val requiredFactKeys: Set<String>

    fun inspect(context: HardwareLearningContext): InformationCartridgeResult
}

data class HardwareLearningRoute(
    val facts: List<HardwareFact>,
    val candidates: List<ProbeCandidate>,
    val inspectedCartridgeIds: List<String>,
)

class HardwareLearningGraph(
    private val cartridges: List<InformationCartridge>,
) {
    fun resolve(
        identity: AndroidDeviceIdentity,
        seedCandidates: List<ProbeCandidate>,
    ): HardwareLearningRoute {
        val facts = linkedMapOf<String, HardwareFact>()
        seedCandidates.flatMap(::factsForCandidate).forEach { facts.merge(it) }
        val candidates = linkedMapOf<String, ProbeCandidate>()
        seedCandidates.forEach { candidates.putIfAbsent(it.routeKey(), it) }
        val pending = cartridges.distinctBy { "${it.id}:${it.version}" }.toMutableList()
        val inspected = mutableListOf<String>()

        while (pending.isNotEmpty()) {
            val available = pending.firstOrNull { cartridge -> cartridge.requiredFactKeys.all(facts::containsKey) } ?: break
            pending.remove(available)
            val context = HardwareLearningContext(identity, facts.values.toList(), candidates.values.toList())
            val result = runCatching { available.inspect(context) }.getOrDefault(InformationCartridgeResult())
            result.facts.forEach { facts.merge(it) }
            result.candidates.forEach { candidates.putIfAbsent(it.routeKey(), it) }
            inspected += available.id
        }

        return HardwareLearningRoute(facts.values.toList(), candidates.values.toList(), inspected)
    }
}

private fun factsForCandidate(candidate: ProbeCandidate): List<HardwareFact> =
    buildList {
        val surfaceKey =
            when (candidate.surface) {
                ProbeSurface.SETTINGS_PSERVER -> FACT_SETTINGS_PSERVER
                ProbeSurface.SINGLEADC_JOYPAD -> "surface.singleadc_joypad"
                ProbeSurface.SYSFS_RGB -> "surface.sysfs_rgb"
                ProbeSurface.HTR3212 -> "surface.htr3212"
            }
        add(HardwareFact(surfaceKey, "present", FactEvidence.OBSERVED, candidate.cartridgeId))
        candidate.signalKeys.sorted().forEach { key ->
            add(HardwareFact("signal.$key", "present", FactEvidence.OBSERVED, candidate.cartridgeId))
        }
    }

private fun MutableMap<String, HardwareFact>.merge(fact: HardwareFact) {
    val current = this[fact.key]
    if (current == null || fact.evidence.ordinal > current.evidence.ordinal) this[fact.key] = fact
}

private fun ProbeCandidate.routeKey(): String =
    "$cartridgeId:$cartridgeVersion:${encodeLearningDescriptor(descriptor)}"
