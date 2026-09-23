package com.hooandee.colores.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.hooandee.colores.device.DeviceCapabilities
import com.hooandee.colores.device.learning.EvidenceLevel
import com.hooandee.colores.device.learning.HardwareLearningResult
import com.hooandee.colores.device.learning.HardwareLearningState
import com.hooandee.colores.device.learning.HardwareLearningStatus
import com.hooandee.colores.device.learning.LearningBlockReason
import com.hooandee.colores.device.learning.ProbeCandidate
import com.hooandee.colores.device.learning.ProbeEvidence
import com.hooandee.colores.device.learning.ProbeStep
import com.hooandee.colores.device.learning.ProbeSurface
import com.hooandee.colores.device.learning.RollbackStatus
import com.hooandee.colores.device.learning.UserObservation
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import com.hooandee.colores.report.ReportResult
import com.hooandee.colores.report.ReportSubmissionState
import com.hooandee.colores.settings.AppAppearance
import com.hooandee.colores.settings.ThemeMode

class UiGalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra("screen") ?: "report"
        val step = intent.getStringExtra("step") ?: "consent"
        val theme = if (intent.getStringExtra("theme") == "light") ThemeMode.LIGHT else ThemeMode.DARK
        setContent {
            ColoresTheme(AppAppearance(themeMode = theme)) {
                PrismaticBackdrop(Modifier.fillMaxSize()) {}
                when (screen) {
                    "learn" ->
                        HardwareLearningDialog(
                            ui = learningState(step),
                            onDismiss = {}, onConsent = {}, onRunProbe = {}, onAnswer = { _, _ -> }, onFinish = {},
                            onNextCandidate = {}, onReport = {}, onRetryRestore = {}, onRequestDiscard = {},
                            onCancelDiscard = {}, onConfirmDiscard = {},
                        )
                    else ->
                        AndroidReportDialog(
                            state = ColoresUiState(loading = false, reportSubmission = reportState(step)),
                            onDismiss = {},
                            onSubmit = { _, _ -> },
                            initialCategories = if (step == "locked") setOf("learning") else setOf("color"),
                            initialText = if (step == "empty") "" else "Los LEDs del stick derecho parpadean al cambiar de efecto.",
                            lockedCategories = step == "locked",
                        )
                }
            }
        }
    }

    private fun reportState(step: String) =
        when (step) {
            "done" -> ReportSubmissionState(result = ReportResult.Success("COL-7F3K-2QXA", null))
            "fail" -> ReportSubmissionState(result = ReportResult.Failure("offline", "/data/report.json"))
            "sending" -> ReportSubmissionState(sending = true)
            else -> ReportSubmissionState()
        }

    private fun learningState(step: String): HardwareLearningUiState {
        val candidate =
            ProbeCandidate(
                "android-sysfs-multicolor",
                1,
                ProbeSurface.SYSFS_RGB,
                SysfsRgbDescriptor("/sys/class/leds/gamepad", 2, 255, SysfsColorKind.MULTI_INTENSITY_HEX),
                emptySet(),
            )
        val htr = candidate.copy(cartridgeId = "htr3212-multipoint", surface = ProbeSurface.HTR3212)
        val steps = listOf(ProbeStep.COLOR, ProbeStep.BRIGHTNESS_LOW, ProbeStep.POWER_OFF)
        val colorYes = ProbeEvidence(ProbeStep.COLOR, null, EvidenceLevel.USER_CONFIRMED, UserObservation.YES)
        fun complete(status: HardwareLearningStatus, zones: Int = 2) =
            HardwareLearningState.Complete(
                HardwareLearningResult(status, candidate, listOf(colorYes), DeviceCapabilities(true, true, zones > 1, zones), RollbackStatus.RESTORED_AND_READ_BACK),
            )
        val session =
            when (step) {
                "probe" -> HardwareLearningState.Ready(candidate, steps, emptyList())
                "await" -> HardwareLearningState.AwaitingAnswer(candidate, steps, ProbeStep.COLOR, null, emptyList())
                "htr" -> HardwareLearningState.AwaitingAnswer(htr, listOf(ProbeStep.COLOR, ProbeStep.ZONE), ProbeStep.ZONE, 0, listOf(colorYes))
                "done" -> HardwareLearningState.Ready(candidate, listOf(ProbeStep.COLOR), listOf(colorYes))
                "success" -> complete(HardwareLearningStatus.ADAPTED)
                "twozones" -> complete(HardwareLearningStatus.ADAPTED)
                "nomatch" -> complete(HardwareLearningStatus.BLOCKED)
                "restorefail" -> HardwareLearningState.Blocked(LearningBlockReason.RESTORE_FAILED)
                else -> HardwareLearningState.ConsentRequired(candidate)
            }
        return HardwareLearningUiState(
            dialogOpen = true,
            sessionState = session,
            candidateIndex = 0,
            candidateCount = if (step == "twozones") 2 else 1,
            journalPending = step == "restorefail",
            restoreFailure = step == "restorefail",
        )
    }
}
