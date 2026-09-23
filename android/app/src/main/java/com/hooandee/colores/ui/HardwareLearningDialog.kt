package com.hooandee.colores.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hooandee.colores.R
import com.hooandee.colores.device.learning.HardwareLearningState
import com.hooandee.colores.device.learning.HardwareLearningStatus
import com.hooandee.colores.device.learning.LearningBlockReason
import com.hooandee.colores.device.learning.MAX_ROLLBACK_RECOVERY_ATTEMPTS
import com.hooandee.colores.device.learning.ProbeStep
import com.hooandee.colores.device.learning.ProbeSurface
import com.hooandee.colores.device.learning.RollbackStatus
import com.hooandee.colores.device.learning.UserObservation
import com.hooandee.colores.device.learning.ZoneLocation

@Composable
internal fun HardwareLearningDialog(
    ui: HardwareLearningUiState,
    onDismiss: () -> Unit,
    onConsent: () -> Unit,
    onRunProbe: () -> Unit,
    onAnswer: (UserObservation, ZoneLocation?) -> Unit,
    onFinish: () -> Unit,
    onNextCandidate: () -> Unit,
    onReport: () -> Unit,
    onRetryRestore: () -> Unit,
    onRequestDiscard: () -> Unit,
    onCancelDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
) {
    val recoveryActions = RecoveryActions(onRetryRestore, onRequestDiscard, onCancelDiscard, onConfirmDiscard)
    Dialog(
        onDismissRequest = { if (ui.canDismiss) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val landscape = isUsableLandscape(maxWidth, maxHeight)
            Surface(
                modifier =
                    Modifier
                        .widthIn(max = if (landscape) 920.dp else 620.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .prismaticPanel(RoundedCornerShape(32.dp), strong = true),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(32.dp),
            ) {
                val compactHeight = maxHeight < 560.dp
                val gutter = if (compactHeight && !landscape) 20.dp else 26.dp
                val scrollState = rememberScrollState()
                val zoneQuestion =
                    (ui.sessionState as? HardwareLearningState.AwaitingAnswer)?.isHtrZone() == true
                Column(Modifier.padding(horizontal = gutter, vertical = if (compactHeight) 18.dp else 22.dp)) {
                    LearningHeader(ui, onDismiss)
                    Spacer(Modifier.height(if (compactHeight) 12.dp else 16.dp))
                    val bodyModifier =
                        Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .scrollFadeEdges(scrollState)
                            .verticalScroll(scrollState)
                    if (zoneQuestion) {
                        Column(bodyModifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            LearningBody(ui, textAlign = TextAlign.Start, horizontalAlignment = Alignment.Start)
                            LearningActions(ui, onDismiss, onConsent, onRunProbe, onAnswer, onFinish, onNextCandidate, onReport, recoveryActions, landscape)
                        }
                    } else if (landscape) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LearningBeacon(ui.sessionState, if (compactHeight) 64.dp else 88.dp, Modifier.width(188.dp), showAreas = !compactHeight)
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .scrollFadeEdges(scrollState)
                                        .verticalScroll(scrollState)
                                        .padding(end = 6.dp),
                            ) {
                                LearningBody(ui, textAlign = TextAlign.Start, horizontalAlignment = Alignment.Start)
                            }
                        }
                    } else {
                        Column(
                            modifier = bodyModifier,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (compactHeight) {
                                CompactLearningBeacon(ui.sessionState)
                            } else {
                                LearningBeacon(ui.sessionState, 94.dp)
                            }
                            LearningBody(ui, textAlign = TextAlign.Center, horizontalAlignment = Alignment.CenterHorizontally)
                        }
                    }
                    if (ui.actionLayout != HardwareLearningActionLayout.NONE && !zoneQuestion) {
                        Spacer(Modifier.height(if (compactHeight) 12.dp else 16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(if (compactHeight) 12.dp else 14.dp))
                        LearningActions(ui, onDismiss, onConsent, onRunProbe, onAnswer, onFinish, onNextCandidate, onReport, recoveryActions, landscape)
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningHeader(
    ui: HardwareLearningUiState,
    onDismiss: () -> Unit,
) {
    val routes = ui.candidateCount.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.hardware_learning_eyebrow),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
                maxLines = 1,
            )
            if (routes > 1) {
                val progress = stringResource(R.string.hardware_learning_progress, ui.candidateIndex + 1, routes)
                Row(
                    modifier = Modifier.semantics { contentDescription = progress },
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(routes) { index ->
                        Box(
                            Modifier
                                .height(6.dp)
                                .width(if (index == ui.candidateIndex) 18.dp else 6.dp)
                                .background(
                                    if (index <= ui.candidateIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }
        if (ui.canDismiss) {
            Surface(
                onClick = onDismiss,
                modifier = Modifier.size(40.dp).prismaticPanel(CircleShape),
                shape = CircleShape,
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.hardware_learning_close),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LearningBeacon(
    state: HardwareLearningState,
    lensSize: Dp,
    modifier: Modifier = Modifier,
    showAreas: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        OpticalLearningLens(state, lensSize)
        Text(
            text = stringResource(R.string.hardware_learning_look_device),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (showAreas) {
            Text(
                text = stringResource(R.string.hardware_learning_look_areas),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CompactLearningBeacon(state: HardwareLearningState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OpticalLearningLens(state, 52.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.hardware_learning_look_device),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.hardware_learning_look_areas),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun OpticalLearningLens(
    state: HardwareLearningState,
    lensSize: Dp,
) {
    val target =
        when (state) {
            is HardwareLearningState.AwaitingAnswer -> Color(0xFF5FD8FF)
            is HardwareLearningState.Complete -> Color(0xFF6FEDBE)
            is HardwareLearningState.Blocked -> Color(0xFFFF9A7A)
            else -> MaterialTheme.colorScheme.primary
        }
    val color by animateColorAsState(target, tween(520), label = "learning-ring-color")
    val settled = state is HardwareLearningState.Complete
    val transition = rememberInfiniteTransition(label = "learning-ring")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "learning-ring-rotation")
    val breath by transition.animateFloat(0.82f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "learning-ring-breath")
    val description = stringResource(R.string.hardware_learning_lens_accessibility)
    Canvas(
        Modifier
            .size(lensSize)
            .semantics { contentDescription = description },
    ) {
        val stroke = size.minDimension * 0.085f
        val radius = size.minDimension * 0.34f
        val glow = if (settled) 1f else breath
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.3f * glow), Color.Transparent), radius = size.minDimension * 0.5f),
            radius = size.minDimension * 0.5f,
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFF1B1F2A), Color(0xFF07090D)), radius = radius),
            radius = radius - stroke / 2f,
        )
        drawCircle(Color.White.copy(alpha = 0.07f), radius, style = Stroke(stroke))
        listOf(2.4f to 0.1f, 1.6f to 0.18f).forEach { (width, alpha) ->
            drawCircle(color.copy(alpha = alpha * glow), radius, style = Stroke(stroke * width))
        }
        rotate(if (settled) 0f else rotation) {
            drawCircle(
                brush =
                    Brush.sweepGradient(
                        if (settled) {
                            listOf(color, color)
                        } else {
                            listOf(color.copy(alpha = 0.35f), color, Color.White.copy(alpha = 0.95f), color, color.copy(alpha = 0.35f))
                        },
                    ),
                radius = radius,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), radius = radius * 0.7f),
            radius = radius * 0.7f,
        )
    }
}

@Composable
private fun LearningBody(
    ui: HardwareLearningUiState,
    textAlign: TextAlign,
    horizontalAlignment: Alignment.Horizontal,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        if (ui.discardConfirmation) {
            LearningMessage(
                title = stringResource(R.string.hardware_learning_discard_title),
                body = stringResource(R.string.hardware_learning_discard_body),
                safety = null,
                textAlign = textAlign,
            )
            return@Column
        }
        when (val state = ui.sessionState) {
            HardwareLearningState.Idle -> CircularProgressIndicator(Modifier.size(28.dp))
            is HardwareLearningState.ConsentRequired ->
                LearningMessage(
                    title =
                        stringResource(
                            if (ui.revalidation) R.string.hardware_learning_revalidate_title else R.string.hardware_learning_title,
                        ),
                    body =
                        if (ui.revalidation) {
                            stringResource(R.string.hardware_learning_revalidate_body)
                        } else {
                            stringResource(R.string.hardware_learning_consent, surfaceLabel(state.candidate.surface))
                        },
                    safety = stringResource(R.string.hardware_learning_safety),
                    textAlign = textAlign,
                )
            is HardwareLearningState.Ready -> {
                val request = ui.nextProbe
                if (request != null) {
                    LearningMessage(
                        title = probeTitle(request.step, request.zone),
                        body = probeBody(request.step, request.zone),
                        safety = stringResource(R.string.hardware_learning_state_saved),
                        textAlign = textAlign,
                    )
                } else {
                    LearningMessage(
                        title = stringResource(R.string.hardware_learning_candidate_done),
                        body = stringResource(R.string.hardware_learning_candidate_done_body),
                        safety = stringResource(R.string.hardware_learning_restore_before_result),
                        textAlign = textAlign,
                    )
                }
            }
            is HardwareLearningState.AwaitingAnswer ->
                LearningMessage(
                    title =
                        if (state.isHtrZone()) {
                            stringResource(R.string.hardware_learning_zone_location_title)
                        } else {
                            observationTitle(state.step, state.zone)
                        },
                    body =
                        if (state.isHtrZone()) {
                            stringResource(R.string.hardware_learning_zone_location_body)
                        } else {
                            stringResource(R.string.hardware_learning_observation_body)
                        },
                    safety = null,
                    textAlign = textAlign,
                )
            is HardwareLearningState.Complete ->
                if (state.result.status == HardwareLearningStatus.RESTORE_FAILED) {
                    LearningMessage(
                        title = stringResource(R.string.hardware_learning_restore_failed),
                        body = recoveryBody(ui, stringResource(R.string.hardware_learning_restore_failed_body)),
                        safety = null,
                        textAlign = textAlign,
                    )
                } else {
                    LearningResultBody(
                        state.result.status,
                        state.result.rollbackStatus,
                        ui.hasNextCandidate,
                        ui.confirmedTwoZoneFallback,
                        ui.groupedMultipointWithFallback,
                        textAlign,
                    )
                }
            is HardwareLearningState.Blocked ->
                when (state.reason) {
                    LearningBlockReason.RESTORE_FAILED ->
                        LearningMessage(
                            title = stringResource(R.string.hardware_learning_restore_failed),
                            body = recoveryBody(ui, stringResource(R.string.hardware_learning_restore_failed_body)),
                            safety = null,
                            textAlign = textAlign,
                        )
                    LearningBlockReason.JOURNAL_UNAVAILABLE ->
                        LearningMessage(
                            title = stringResource(R.string.hardware_learning_journal_unavailable),
                            body = recoveryBody(ui, stringResource(R.string.hardware_learning_journal_unavailable_body)),
                            safety = null,
                            textAlign = textAlign,
                        )
                    else ->
                        LearningMessage(
                            title = stringResource(R.string.hardware_learning_blocked),
                            body = stringResource(R.string.hardware_learning_blocked_body),
                            safety = null,
                            textAlign = textAlign,
                        )
                }
        }
    }
}

@Composable
private fun LearningMessage(
    title: String,
    body: String,
    safety: String?,
    textAlign: TextAlign,
) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = textAlign,
    )
    Text(
        body,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = textAlign,
    )
    if (safety != null) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(
                "✓  $safety",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LearningResultBody(
    status: HardwareLearningStatus,
    rollbackStatus: RollbackStatus,
    hasNextCandidate: Boolean,
    confirmedTwoZoneFallback: Boolean,
    groupedMultipointWithFallback: Boolean,
    textAlign: TextAlign,
) {
    val adapted = status == HardwareLearningStatus.ADAPTED
    LearningMessage(
        title =
            when {
                confirmedTwoZoneFallback -> stringResource(R.string.hardware_learning_two_zones_confirmed)
                groupedMultipointWithFallback -> stringResource(R.string.hardware_learning_grouped_points)
                adapted -> stringResource(R.string.hardware_learning_success)
                else -> stringResource(R.string.hardware_learning_no_match)
            },
        body =
            when {
                confirmedTwoZoneFallback -> stringResource(R.string.hardware_learning_two_zones_confirmed_body)
                groupedMultipointWithFallback -> stringResource(R.string.hardware_learning_grouped_points_body)
                hasNextCandidate -> stringResource(R.string.hardware_learning_more_routes_body)
                adapted -> stringResource(R.string.hardware_learning_success_body)
                else -> stringResource(R.string.hardware_learning_no_match_body)
            },
        safety =
            stringResource(
                when (rollbackStatus) {
                    RollbackStatus.RESTORED_AND_READ_BACK -> R.string.hardware_learning_restored
                    RollbackStatus.RESTORED_UNVERIFIED -> R.string.hardware_learning_restored_unverified
                    else -> R.string.hardware_learning_restored_without_readback
                },
            ),
        textAlign = textAlign,
    )
}

@Composable
private fun LearningActions(
    ui: HardwareLearningUiState,
    onDismiss: () -> Unit,
    onConsent: () -> Unit,
    onRunProbe: () -> Unit,
    onAnswer: (UserObservation, ZoneLocation?) -> Unit,
    onFinish: () -> Unit,
    onNextCandidate: () -> Unit,
    onReport: () -> Unit,
    recoveryActions: RecoveryActions,
    wide: Boolean,
) {
    when (ui.actionLayout) {
        HardwareLearningActionLayout.NONE -> Unit
        HardwareLearningActionLayout.CONSENT ->
            PrimaryLearningButton(stringResource(R.string.hardware_learning_prepare), !ui.busy, onConsent)
        HardwareLearningActionLayout.RUN_PROBE ->
            PrimaryLearningButton(stringResource(R.string.hardware_learning_run), !ui.busy, onRunProbe)
        HardwareLearningActionLayout.FINISH ->
            PrimaryLearningButton(stringResource(R.string.hardware_learning_finish), ui.canFinish && !ui.busy, onFinish)
        HardwareLearningActionLayout.OBSERVATION -> {
            val awaiting = ui.sessionState as HardwareLearningState.AwaitingAnswer
            if (awaiting.isHtrZone()) ZoneLocationActions(awaiting.zone, ui.busy, wide, onAnswer) else ObservationActions(ui.busy, onAnswer)
        }
        HardwareLearningActionLayout.RESULT -> {
            val next: (@Composable (Modifier) -> Unit)? =
                if (ui.hasNextCandidate) {
                    { modifier ->
                        Button(onClick = onNextCandidate, enabled = !ui.busy, modifier = modifier.heightIn(min = 52.dp)) {
                            Text(
                                stringResource(
                                    if (ui.confirmedTwoZoneFallback) {
                                        R.string.hardware_learning_try_multipoint
                                    } else {
                                        R.string.hardware_learning_continue_discovery
                                    },
                                ),
                            )
                        }
                    }
                } else {
                    null
                }
            val keep: (@Composable (Modifier) -> Unit)? =
                if (ui.confirmedTwoZoneFallback || ui.groupedMultipointWithFallback) {
                    { modifier ->
                        OutlinedButton(onClick = onDismiss, enabled = !ui.busy, modifier = modifier.heightIn(min = 50.dp)) {
                            Text(stringResource(R.string.hardware_learning_use_two_zones))
                        }
                    }
                } else {
                    null
                }
            val report: @Composable (Modifier) -> Unit = { modifier ->
                TextButton(onClick = onReport, enabled = !ui.busy, modifier = modifier.heightIn(min = 48.dp)) {
                    Text(
                        if (ui.showBlockedReport) {
                            stringResource(R.string.hardware_learning_report_blocked)
                        } else {
                            stringResource(R.string.hardware_learning_report)
                        },
                    )
                }
            }
            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    report(Modifier)
                    Spacer(Modifier.weight(if (next == null && keep == null) 1f else 0.001f))
                    keep?.invoke(Modifier.weight(1f))
                    next?.invoke(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    next?.invoke(Modifier.fillMaxWidth())
                    keep?.invoke(Modifier.fillMaxWidth())
                    report(Modifier.fillMaxWidth())
                }
            }
        }
        HardwareLearningActionLayout.REPORT_ONLY -> RecoveryLearningActions(ui, onReport, recoveryActions, wide)
    }
}

internal class RecoveryActions(
    val onRetry: () -> Unit,
    val onRequestDiscard: () -> Unit,
    val onCancelDiscard: () -> Unit,
    val onConfirmDiscard: () -> Unit,
)

@Composable
private fun RecoveryLearningActions(
    ui: HardwareLearningUiState,
    onReport: () -> Unit,
    actions: RecoveryActions,
    wide: Boolean,
) {
    val buttons: List<@Composable (Modifier) -> Unit> =
        if (ui.discardConfirmation) {
            listOf(
                { modifier ->
                    OutlinedButton(onClick = actions.onCancelDiscard, enabled = !ui.busy, modifier = modifier.heightIn(min = 50.dp)) {
                        Text(stringResource(R.string.hardware_learning_discard_cancel))
                    }
                },
                { modifier ->
                    Button(onClick = actions.onConfirmDiscard, enabled = !ui.busy, modifier = modifier.heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.hardware_learning_discard_confirm))
                    }
                },
            )
        } else {
            listOfNotNull(
                { modifier: Modifier ->
                    TextButton(onClick = onReport, enabled = !ui.busy, modifier = modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.hardware_learning_report_critical))
                    }
                },
                { modifier: Modifier ->
                    OutlinedButton(onClick = actions.onRequestDiscard, enabled = !ui.busy, modifier = modifier.heightIn(min = 50.dp)) {
                        Text(stringResource(R.string.hardware_learning_discard_restore))
                    }
                },
                if (ui.journalPending) {
                    { modifier: Modifier ->
                        Button(onClick = actions.onRetry, enabled = !ui.busy, modifier = modifier.heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.hardware_learning_retry_restore))
                        }
                    }
                } else {
                    null
                },
            )
        }
    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            buttons.forEach { it(Modifier.weight(1f)) }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            buttons.asReversed().forEach { it(Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun recoveryBody(
    ui: HardwareLearningUiState,
    base: String,
): String {
    val details =
        if (ui.journalPending) {
            listOfNotNull(
                stringResource(R.string.hardware_learning_restore_options),
                ui.recoveryAttempts.takeIf { it > 0 }?.let {
                    stringResource(R.string.hardware_learning_restore_attempts, it, MAX_ROLLBACK_RECOVERY_ATTEMPTS)
                },
            )
        } else {
            listOf(stringResource(R.string.hardware_learning_restore_archived))
        }
    return (listOf(base) + details).joinToString(" ")
}

@Composable
private fun PrimaryLearningButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(label)
    }
}

@Composable
private fun ObservationActions(
    busy: Boolean,
    onAnswer: (UserObservation, ZoneLocation?) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 560.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ObservationButton(
                    label = stringResource(R.string.hardware_learning_no),
                    enabled = !busy,
                    primary = false,
                    onClick = { onAnswer(UserObservation.NO, null) },
                    modifier = Modifier.weight(1f),
                )
                ObservationButton(
                    label = stringResource(R.string.hardware_learning_unsure),
                    enabled = !busy,
                    primary = false,
                    onClick = { onAnswer(UserObservation.UNSURE, null) },
                    modifier = Modifier.weight(1f),
                )
                ObservationButton(
                    label = stringResource(R.string.hardware_learning_yes),
                    enabled = !busy,
                    primary = true,
                    onClick = { onAnswer(UserObservation.YES, null) },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ObservationButton(
                    label = stringResource(R.string.hardware_learning_yes),
                    enabled = !busy,
                    primary = true,
                    onClick = { onAnswer(UserObservation.YES, null) },
                )
                ObservationButton(
                    label = stringResource(R.string.hardware_learning_no),
                    enabled = !busy,
                    primary = false,
                    onClick = { onAnswer(UserObservation.NO, null) },
                )
                ObservationButton(
                    label = stringResource(R.string.hardware_learning_unsure),
                    enabled = !busy,
                    primary = false,
                    onClick = { onAnswer(UserObservation.UNSURE, null) },
                )
            }
        }
    }
}

@Composable
private fun ZoneLocationActions(
    zone: Int?,
    busy: Boolean,
    wide: Boolean,
    onAnswer: (UserObservation, ZoneLocation?) -> Unit,
) {
    val leftGrid: @Composable (Modifier) -> Unit = { modifier ->
        ZoneStickGrid(
            label = stringResource(R.string.hardware_learning_left_stick),
            locations =
                listOf(
                    ZoneLocation.LEFT_TOP_LEFT,
                    ZoneLocation.LEFT_TOP_RIGHT,
                    ZoneLocation.LEFT_BOTTOM_LEFT,
                    ZoneLocation.LEFT_BOTTOM_RIGHT,
                ),
            busy = busy,
            onLocation = { onAnswer(UserObservation.YES, it) },
            modifier = modifier,
        )
    }
    val rightGrid: @Composable (Modifier) -> Unit = { modifier ->
        ZoneStickGrid(
            label = stringResource(R.string.hardware_learning_right_stick),
            locations =
                listOf(
                    ZoneLocation.RIGHT_TOP_LEFT,
                    ZoneLocation.RIGHT_TOP_RIGHT,
                    ZoneLocation.RIGHT_BOTTOM_LEFT,
                    ZoneLocation.RIGHT_BOTTOM_RIGHT,
                ),
            busy = busy,
            onLocation = { onAnswer(UserObservation.YES, it) },
            modifier = modifier,
        )
    }
    val whole: @Composable () -> Unit = {
        ObservationButton(
            label = stringResource(R.string.hardware_learning_whole_stick),
            enabled = !busy,
            primary = false,
            onClick = { onAnswer(UserObservation.YES, wholeStickLocation(zone)) },
        )
    }
    val none: @Composable (Modifier) -> Unit = { modifier ->
        ObservationButton(
            label = stringResource(R.string.hardware_learning_none_visible),
            enabled = !busy,
            primary = false,
            onClick = { onAnswer(UserObservation.NO, null) },
            modifier = modifier,
        )
    }
    val unsure: @Composable (Modifier) -> Unit = { modifier ->
        ObservationButton(
            label = stringResource(R.string.hardware_learning_unsure),
            enabled = !busy,
            primary = false,
            onClick = { onAnswer(UserObservation.UNSURE, null) },
            modifier = modifier,
        )
    }
    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            leftGrid(Modifier.weight(1f))
            rightGrid(Modifier.weight(1f))
            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                whole()
                none(Modifier)
                unsure(Modifier)
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            leftGrid(Modifier.weight(1f))
            rightGrid(Modifier.weight(1f))
        }
        whole()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            none(Modifier.weight(1f))
            unsure(Modifier.weight(1f))
        }
    }
}

internal fun wholeStickLocation(zone: Int?): ZoneLocation =
    if ((zone ?: 0) < 4) ZoneLocation.LEFT_WHOLE else ZoneLocation.RIGHT_WHOLE

@Composable
private fun ZoneStickGrid(
    label: String,
    locations: List<ZoneLocation>,
    busy: Boolean,
    onLocation: (ZoneLocation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        locations.chunked(2).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { columnIndex, location ->
                    val position = zonePositionLabel(rowIndex, columnIndex)
                    OutlinedButton(
                        onClick = { onLocation(location) },
                        enabled = !busy,
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = "$label, $position" },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        ZoneGlyph(rowIndex, columnIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun zonePositionLabel(row: Int, column: Int): String =
    stringResource(
        when {
            row == 0 && column == 0 -> R.string.hardware_learning_top_left
            row == 0 -> R.string.hardware_learning_top_right
            column == 0 -> R.string.hardware_learning_bottom_left
            else -> R.string.hardware_learning_bottom_right
        },
    )

@Composable
private fun ZoneGlyph(
    row: Int,
    column: Int,
) {
    val ring = MaterialTheme.colorScheme.onSurfaceVariant
    val dot = Color(0xFFFF4FD8)
    Canvas(Modifier.size(24.dp)) {
        val radius = size.minDimension / 2f - 1.5.dp.toPx()
        drawCircle(ring.copy(alpha = 0.7f), radius, style = Stroke(1.5.dp.toPx()))
        val offset = radius * 0.62f
        val center = Offset(size.width / 2f + if (column == 0) -offset else offset, size.height / 2f + if (row == 0) -offset else offset)
        drawCircle(dot.copy(alpha = 0.35f), 5.dp.toPx(), center)
        drawCircle(dot, 3.dp.toPx(), center)
    }
}

private fun HardwareLearningState.AwaitingAnswer.isHtrZone(): Boolean =
    candidate.surface == ProbeSurface.HTR3212 && step == ProbeStep.ZONE

@Composable
private fun ObservationButton(
    label: String,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth().heightIn(min = 50.dp).semantics { role = Role.Button },
        ) { Text(label, textAlign = TextAlign.Center) }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth().heightIn(min = 50.dp).semantics { role = Role.Button },
        ) { Text(label, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun surfaceLabel(surface: ProbeSurface): String =
    stringResource(
        when (surface) {
            ProbeSurface.SETTINGS_PSERVER -> R.string.hardware_learning_surface_settings
            ProbeSurface.SINGLEADC_JOYPAD -> R.string.hardware_learning_surface_joypad
            ProbeSurface.SYSFS_RGB -> R.string.hardware_learning_surface_sysfs
            ProbeSurface.HTR3212 -> R.string.hardware_learning_surface_htr3212
        },
    )

@Composable
private fun probeTitle(step: ProbeStep, zone: Int?): String =
    when (step) {
        ProbeStep.COLOR -> stringResource(R.string.hardware_learning_probe_color)
        ProbeStep.BRIGHTNESS_LOW, ProbeStep.BRIGHTNESS_HIGH -> stringResource(R.string.hardware_learning_probe_brightness)
        ProbeStep.POWER_OFF -> stringResource(R.string.hardware_learning_probe_power_off)
        ProbeStep.POWER_ON -> stringResource(R.string.hardware_learning_probe_power_on)
        ProbeStep.HARDWARE_EFFECT -> stringResource(R.string.hardware_learning_probe_hardware_effect)
        ProbeStep.ZONE -> stringResource(R.string.hardware_learning_probe_zone, (zone ?: 0) + 1)
    }

@Composable
private fun probeBody(step: ProbeStep, zone: Int?): String =
    when (step) {
        ProbeStep.COLOR -> stringResource(R.string.hardware_learning_probe_color_body)
        ProbeStep.BRIGHTNESS_LOW -> stringResource(R.string.hardware_learning_probe_brightness_low_body)
        ProbeStep.BRIGHTNESS_HIGH -> stringResource(R.string.hardware_learning_probe_brightness_high_body)
        ProbeStep.ZONE -> stringResource(R.string.hardware_learning_probe_zone_body, (zone ?: 0) + 1)
        ProbeStep.POWER_OFF -> stringResource(R.string.hardware_learning_probe_power_off_body)
        ProbeStep.POWER_ON -> stringResource(R.string.hardware_learning_probe_power_on_body)
        ProbeStep.HARDWARE_EFFECT -> stringResource(R.string.hardware_learning_probe_hardware_effect_body)
    }

@Composable
private fun observationTitle(step: ProbeStep, zone: Int?): String =
    when (step) {
        ProbeStep.COLOR -> stringResource(R.string.hardware_learning_saw_light)
        ProbeStep.BRIGHTNESS_LOW, ProbeStep.BRIGHTNESS_HIGH -> stringResource(R.string.hardware_learning_saw_brightness)
        ProbeStep.POWER_OFF -> stringResource(R.string.hardware_learning_saw_power_off)
        ProbeStep.POWER_ON -> stringResource(R.string.hardware_learning_saw_power_on)
        ProbeStep.HARDWARE_EFFECT -> stringResource(R.string.hardware_learning_saw_hardware_effect)
        ProbeStep.ZONE -> stringResource(R.string.hardware_learning_saw_zone, (zone ?: 0) + 1)
    }
