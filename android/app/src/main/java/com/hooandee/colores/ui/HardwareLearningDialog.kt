package com.hooandee.colores.ui

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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hooandee.colores.R
import com.hooandee.colores.device.learning.HardwareLearningState
import com.hooandee.colores.device.learning.HardwareLearningStatus
import com.hooandee.colores.device.learning.LearningBlockReason
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
) {
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
                Column(Modifier.padding(horizontal = 26.dp, vertical = 22.dp)) {
                    LearningHeader(ui, onDismiss)
                    Spacer(Modifier.height(16.dp))
                    val compactZoneQuestion =
                        (ui.sessionState as? HardwareLearningState.AwaitingAnswer)?.isHtrZone() == true
                    if (compactZoneQuestion) {
                        LearningBody(ui, textAlign = TextAlign.Start, horizontalAlignment = Alignment.Start)
                    } else if (landscape) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LearningBeacon(ui.sessionState, 88.dp, Modifier.width(188.dp))
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                        .padding(end = 6.dp),
                            ) {
                                LearningBody(ui, textAlign = TextAlign.Start, horizontalAlignment = Alignment.Start)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            LearningBeacon(ui.sessionState, 94.dp)
                            LearningBody(ui, textAlign = TextAlign.Center, horizontalAlignment = Alignment.CenterHorizontally)
                        }
                    }
                    if (ui.actionLayout != HardwareLearningActionLayout.NONE) {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
                        Spacer(Modifier.height(14.dp))
                        LearningActions(ui, onDismiss, onConsent, onRunProbe, onAnswer, onFinish, onNextCandidate, onReport)
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        LearningProgress(ui, Modifier.weight(1f))
        if (ui.canDismiss) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.padding(start = 14.dp).size(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.hardware_learning_close),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun LearningProgress(
    ui: HardwareLearningUiState,
    modifier: Modifier = Modifier,
) {
    val progress = if (ui.candidateCount == 0) 0f else (ui.candidateIndex + 1f) / ui.candidateCount
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.hardware_learning_eyebrow),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.hardware_learning_progress, ui.candidateIndex + 1, ui.candidateCount.coerceAtLeast(1)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f), CircleShape),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@Composable
private fun LearningBeacon(
    state: HardwareLearningState,
    lensSize: Dp,
    modifier: Modifier = Modifier,
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
        Text(
            text = stringResource(R.string.hardware_learning_look_areas),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OpticalLearningLens(
    state: HardwareLearningState,
    lensSize: Dp,
) {
    val (center, halo) =
        when (state) {
            is HardwareLearningState.AwaitingAnswer -> Color(0xFF68D9FF) to Color(0x6858AFFF)
            is HardwareLearningState.Complete -> Color(0xFF86F0C5) to Color(0x5556DDAE)
            is HardwareLearningState.Blocked -> Color(0xFFFFB38C) to Color(0x55FF835E)
            else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
        }
    val description = stringResource(R.string.hardware_learning_lens_accessibility)
    Box(
        modifier =
            Modifier
                .size(lensSize)
                .semantics { contentDescription = description }
                .drawBehind {
                    drawCircle(brush = Brush.radialGradient(listOf(halo, Color.Transparent)), radius = size.minDimension * 0.72f)
                    drawCircle(color = Color.White.copy(alpha = 0.18f), radius = size.minDimension * 0.34f)
                    drawCircle(
                        brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f), center, center.copy(alpha = 0.4f))),
                        radius = size.minDimension * 0.3f,
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.72f),
                        radius = size.minDimension * 0.055f,
                        center = Offset(size.width * 0.43f, size.height * 0.38f),
                    )
                },
    )
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
        when (val state = ui.sessionState) {
            HardwareLearningState.Idle -> CircularProgressIndicator(Modifier.size(28.dp))
            is HardwareLearningState.ConsentRequired ->
                LearningMessage(
                    title = stringResource(R.string.hardware_learning_title),
                    body = stringResource(R.string.hardware_learning_consent, surfaceLabel(state.candidate.surface)),
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
                        body = stringResource(R.string.hardware_learning_restore_failed_body),
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
                            body = stringResource(R.string.hardware_learning_restore_failed_body),
                            safety = null,
                            textAlign = textAlign,
                        )
                    LearningBlockReason.JOURNAL_UNAVAILABLE ->
                        LearningMessage(
                            title = stringResource(R.string.hardware_learning_journal_unavailable),
                            body = stringResource(R.string.hardware_learning_journal_unavailable_body),
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
                if (rollbackStatus == RollbackStatus.RESTORED_AND_READ_BACK) {
                    R.string.hardware_learning_restored
                } else {
                    R.string.hardware_learning_restored_without_readback
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
            if (awaiting.isHtrZone()) ZoneLocationActions(awaiting.zone, ui.busy, onAnswer) else ObservationActions(ui.busy, onAnswer)
        }
        HardwareLearningActionLayout.RESULT ->
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (ui.hasNextCandidate) {
                    PrimaryLearningButton(
                        stringResource(
                            if (ui.confirmedTwoZoneFallback) {
                                R.string.hardware_learning_try_multipoint
                            } else {
                                R.string.hardware_learning_continue_discovery
                            },
                        ),
                        !ui.busy,
                        onNextCandidate,
                    )
                }
                if (ui.confirmedTwoZoneFallback || ui.groupedMultipointWithFallback) {
                    OutlinedButton(onClick = onDismiss, enabled = !ui.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                        Text(stringResource(R.string.hardware_learning_use_two_zones))
                    }
                }
                TextButton(onClick = onReport, enabled = !ui.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(
                        if (ui.showBlockedReport) {
                            stringResource(R.string.hardware_learning_report_blocked)
                        } else {
                            stringResource(R.string.hardware_learning_report)
                        },
                    )
                }
            }
        HardwareLearningActionLayout.REPORT_ONLY ->
            PrimaryLearningButton(stringResource(R.string.hardware_learning_report_critical), !ui.busy, onReport)
    }
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
    onAnswer: (UserObservation, ZoneLocation?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                modifier = Modifier.weight(1f),
            )
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
                modifier = Modifier.weight(1f),
            )
        }
        ObservationButton(
            label = stringResource(R.string.hardware_learning_whole_stick),
            enabled = !busy,
            primary = false,
            onClick = { onAnswer(UserObservation.YES, wholeStickLocation(zone)) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ObservationButton(
                label = stringResource(R.string.hardware_learning_none_visible),
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
                        Text(zoneArrow(rowIndex, columnIndex))
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

private fun zoneArrow(row: Int, column: Int): String =
    when {
        row == 0 && column == 0 -> "↖"
        row == 0 -> "↗"
        column == 0 -> "↙"
        else -> "↘"
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
    }

@Composable
private fun observationTitle(step: ProbeStep, zone: Int?): String =
    when (step) {
        ProbeStep.COLOR -> stringResource(R.string.hardware_learning_saw_light)
        ProbeStep.BRIGHTNESS_LOW, ProbeStep.BRIGHTNESS_HIGH -> stringResource(R.string.hardware_learning_saw_brightness)
        ProbeStep.POWER_OFF -> stringResource(R.string.hardware_learning_saw_power_off)
        ProbeStep.POWER_ON -> stringResource(R.string.hardware_learning_saw_power_on)
        ProbeStep.ZONE -> stringResource(R.string.hardware_learning_saw_zone, (zone ?: 0) + 1)
    }
