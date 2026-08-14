package com.hooandee.colores.ui

import com.hooandee.colores.device.learning.EvidenceLevel
import com.hooandee.colores.device.learning.HardwareLearningResult
import com.hooandee.colores.device.learning.HardwareLearningState
import com.hooandee.colores.device.learning.HardwareLearningStatus
import com.hooandee.colores.device.learning.LearningBlockReason
import com.hooandee.colores.device.learning.ProbeEvidence
import com.hooandee.colores.device.learning.ProbeStep
import com.hooandee.colores.device.learning.RollbackStatus
import com.hooandee.colores.led.SettingsProviderDescriptor
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SysfsRgbDescriptor

data class ProbeRequest(
    val step: ProbeStep,
    val zone: Int?,
)

enum class HardwareLearningActionLayout {
    NONE,
    CONSENT,
    RUN_PROBE,
    FINISH,
    OBSERVATION,
    RESULT,
    REPORT_ONLY,
}

data class HardwareLearningUiState(
    val dialogOpen: Boolean = false,
    val reportOpen: Boolean = false,
    val restoreFailure: Boolean = false,
    val busy: Boolean = false,
    val sessionState: HardwareLearningState = HardwareLearningState.Idle,
    val candidateIndex: Int = 0,
    val candidateCount: Int = 0,
    val results: List<HardwareLearningResult> = emptyList(),
) {
    val actionLayout: HardwareLearningActionLayout
        get() =
            when (val state = sessionState) {
                HardwareLearningState.Idle -> HardwareLearningActionLayout.NONE
                is HardwareLearningState.ConsentRequired -> HardwareLearningActionLayout.CONSENT
                is HardwareLearningState.Ready ->
                    if (nextProbe == null) HardwareLearningActionLayout.FINISH else HardwareLearningActionLayout.RUN_PROBE
                is HardwareLearningState.AwaitingAnswer -> HardwareLearningActionLayout.OBSERVATION
                is HardwareLearningState.Complete ->
                    if (state.result.status == HardwareLearningStatus.RESTORE_FAILED) {
                        HardwareLearningActionLayout.REPORT_ONLY
                    } else {
                        HardwareLearningActionLayout.RESULT
                    }
                is HardwareLearningState.Blocked ->
                    if (state.reason in setOf(LearningBlockReason.RESTORE_FAILED, LearningBlockReason.JOURNAL_UNAVAILABLE)) {
                        HardwareLearningActionLayout.REPORT_ONLY
                    } else {
                        HardwareLearningActionLayout.NONE
                    }
            }

    val hasNextCandidate: Boolean
        get() = candidateIndex + 1 < candidateCount

    val nextProbe: ProbeRequest?
        get() {
            val ready = sessionState as? HardwareLearningState.Ready ?: return null
            if (!ready.evidence.hasAnswer(ProbeStep.COLOR)) return ProbeRequest(ProbeStep.COLOR, null)
            if (!ready.evidence.isConfirmed(ProbeStep.COLOR)) return null
            if (ProbeStep.ZONE in ready.supportedSteps) {
                val zones = ready.candidate.observedZoneLimit()
                (0 until zones).firstOrNull { !ready.evidence.hasAnswer(ProbeStep.ZONE, it) }?.let {
                    return ProbeRequest(ProbeStep.ZONE, it)
                }
            }
            listOf(
                ProbeStep.BRIGHTNESS_LOW,
                ProbeStep.BRIGHTNESS_HIGH,
                ProbeStep.POWER_OFF,
                ProbeStep.POWER_ON,
            ).firstOrNull { it in ready.supportedSteps && !ready.evidence.hasAnswer(it) }?.let {
                return ProbeRequest(it, null)
            }
            return null
        }

    val canFinish: Boolean
        get() = sessionState is HardwareLearningState.Ready && nextProbe == null

    val showBlockedReport: Boolean
        get() {
            val result = (sessionState as? HardwareLearningState.Complete)?.result ?: return false
            return !hasNextCandidate && result.status in setOf(HardwareLearningStatus.BLOCKED, HardwareLearningStatus.RESTORE_FAILED)
        }

    val canOpenReport: Boolean
        get() = results.isNotEmpty() || restoreFailure || criticalBlockReason != null

    val canDismiss: Boolean
        get() = !busy && !restoreFailure && criticalBlockReason == null

    val criticalBlockReason: LearningBlockReason?
        get() =
            (sessionState as? HardwareLearningState.Blocked)
                ?.reason
                ?.takeIf { it in setOf(LearningBlockReason.RESTORE_FAILED, LearningBlockReason.JOURNAL_UNAVAILABLE) }
}

internal fun dismissedHardwareLearningUiState(current: HardwareLearningUiState): HardwareLearningUiState =
    HardwareLearningUiState(results = current.results)

internal fun hardwareLearningCancellationFailed(
    status: RollbackStatus?,
    state: HardwareLearningState,
): Boolean =
    status == RollbackStatus.RESTORE_FAILED ||
        state is HardwareLearningState.Blocked &&
        state.reason in setOf(LearningBlockReason.RESTORE_FAILED, LearningBlockReason.JOURNAL_UNAVAILABLE)

internal fun hardwareLearningRestoreFailed(
    status: RollbackStatus?,
    state: HardwareLearningState,
): Boolean =
    status == RollbackStatus.RESTORE_FAILED ||
        state is HardwareLearningState.Blocked && state.reason == LearningBlockReason.RESTORE_FAILED

private fun List<ProbeEvidence>.hasAnswer(
    step: ProbeStep,
    zone: Int? = null,
): Boolean = any { it.step == step && it.zone == zone && it.observation != null }

private fun List<ProbeEvidence>.isConfirmed(step: ProbeStep): Boolean =
    any { it.step == step && it.level == EvidenceLevel.USER_CONFIRMED }

private fun com.hooandee.colores.device.learning.ProbeCandidate.observedZoneLimit(): Int =
    when (val value = descriptor) {
        is SettingsProviderDescriptor -> value.zones
        is SysfsRgbDescriptor -> value.zones
        is SingleAdcJoypadDescriptor -> 1
    }.coerceIn(1, 32)
