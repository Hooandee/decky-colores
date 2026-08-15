package com.hooandee.colores.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hooandee.colores.R
import com.hooandee.colores.audio.AudioCaptureStatus
import com.hooandee.colores.control.AppMode
import com.hooandee.colores.engine.AudioSensitivity
import com.hooandee.colores.led.RgbColor

@Composable
fun DashboardScreen(
    state: ColoresUiState,
    onGrantPermission: () -> Unit,
    onPowerChange: (Boolean) -> Unit,
    onTargetChange: (EditTarget) -> Unit,
    onColorChange: (RgbColor) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHardwareLearning: () -> Unit,
    onOpenHardwareLearningReport: () -> Unit,
    gradientActions: GradientActions,
    modeActions: ModeActions,
) {
    PrismaticBackdrop(modifier = Modifier.fillMaxSize()) {
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@PrismaticBackdrop
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            DashboardHeader(
                state = state,
                onPowerChange = onPowerChange,
                onOpenProfiles = onOpenProfiles,
                onOpenSettings = onOpenSettings,
            )
            Spacer(Modifier.height(14.dp))
            val detected = state.detected
            if (detected == null) {
                val canConfigure = state.hasHardwareLearningCandidates
                ControlStatusCard(
                    title =
                        stringResource(
                            when {
                                state.hardwareLearningNeedsReport -> R.string.lights_no_match_title
                                canConfigure -> R.string.lights_setup_title
                                else -> R.string.no_leds_title
                            },
                        ),
                    description =
                        when {
                            state.hardwareLearningNeedsReport -> stringResource(R.string.lights_no_match_description)
                            canConfigure ->
                                stringResource(
                                    R.string.lights_setup_description,
                                    state.devicePresentation.friendlyName.ifBlank { stringResource(R.string.device_unknown) },
                                )
                            else -> stringResource(R.string.no_leds_description)
                        },
                    action =
                        stringResource(
                            if (state.hardwareLearningNeedsReport) {
                                R.string.lights_no_match_report
                            } else {
                                R.string.lights_setup_action
                            },
                        ).takeIf { state.hardwareLearningNeedsReport || canConfigure },
                    onAction =
                        if (state.hardwareLearningNeedsReport) {
                            onOpenHardwareLearningReport
                        } else {
                            onOpenHardwareLearning.takeIf { canConfigure }
                        },
                    secondaryAction = stringResource(R.string.lights_no_match_retry).takeIf { state.hardwareLearningNeedsReport },
                    onSecondaryAction = onOpenHardwareLearning.takeIf { state.hardwareLearningNeedsReport && canConfigure },
                )
                return@Column
            }
            when (state.controlAccess) {
                ControlAccess.USER_PERMISSION_REQUIRED ->
                    ControlStatusCard(
                        title = stringResource(R.string.permission_title),
                        description = stringResource(R.string.permission_description),
                        action = stringResource(R.string.permission_button),
                        onAction = onGrantPermission,
                    )
                ControlAccess.SERVICE_UNAVAILABLE ->
                    ControlStatusCard(
                        title = stringResource(R.string.control_service_title),
                        description = stringResource(R.string.control_service_description),
                        action = stringResource(R.string.hardware_learning_repair).takeIf { state.hasHardwareLearningCandidates },
                        onAction = onOpenHardwareLearning.takeIf { state.hasHardwareLearningCandidates },
                    )
                ControlAccess.ENABLED ->
                    DashboardBody(
                        state = state,
                        perZone = detected.capabilities.perZone && detected.capabilities.zones > 1,
                        colorEnabled = detected.capabilities.color,
                        brightnessEnabled = detected.capabilities.brightness,
                        onTargetChange = onTargetChange,
                        onColorChange = onColorChange,
                        onSaturationChange = onSaturationChange,
                        onBrightnessChange = onBrightnessChange,
                        gradientActions = gradientActions,
                        modeActions = modeActions,
                        modifier = Modifier.weight(1f),
                    )
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    state: ColoresUiState,
    onPowerChange: (Boolean) -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val settingsLabel = stringResource(R.string.settings_open)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val showSettingsLabel = maxWidth >= 720.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.4.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.devicePresentation.friendlyName.ifBlank { stringResource(R.string.device_unknown) },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.canWrite) ConnectedPill()
                    if (state.detected == null && (state.hasHardwareLearningCandidates || state.hardwareLearningNeedsReport)) {
                        SetupRequiredPill(needsReport = state.hardwareLearningNeedsReport)
                    }
                    if (state.detected != null) {
                        ProfileSelectorPill(state = state, onOpen = onOpenProfiles)
                    }
                }
            }
            if (state.detected?.capabilities?.power == true) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.power_title),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Switch(
                        checked = state.ledState.power,
                        onCheckedChange = onPowerChange,
                        enabled = state.canWrite,
                    )
                }
            }
            Surface(
                onClick = onOpenSettings,
                modifier =
                    Modifier
                        .height(48.dp)
                        .widthIn(min = 48.dp)
                        .prismaticPanel(RoundedCornerShape(14.dp))
                        .semantics { contentDescription = settingsLabel },
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = if (showSettingsLabel) 16.dp else 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    if (showSettingsLabel) {
                        Text(settingsLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupRequiredPill(needsReport: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = stringResource(if (needsReport) R.string.lights_no_match_pill else R.string.lights_setup_title),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ConnectedPill() {
    Surface(
        color = Color(0xFF17372F),
        contentColor = Color(0xFF82E7C7),
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(6.dp),
                color = Color(0xFF82E7C7),
                shape = RoundedCornerShape(999.dp),
            ) {}
            Text(
                text = stringResource(R.string.status_connected),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DashboardBody(
    state: ColoresUiState,
    perZone: Boolean,
    colorEnabled: Boolean,
    brightnessEnabled: Boolean,
    onTargetChange: (EditTarget) -> Unit,
    onColorChange: (RgbColor) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    gradientActions: GradientActions,
    modeActions: ModeActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModeNav(
            modes = state.availableModes(),
            selected = state.mode,
            enabled = state.canWrite,
            onModeChange = modeActions.onModeChange,
        )
        DashboardModeLayout(
            state = state,
            perZone = perZone,
            colorEnabled = colorEnabled,
            brightnessEnabled = brightnessEnabled,
            onTargetChange = onTargetChange,
            onColorChange = onColorChange,
            onSaturationChange = onSaturationChange,
            onBrightnessChange = onBrightnessChange,
            gradientActions = gradientActions,
            modeActions = modeActions,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DashboardModeLayout(
    state: ColoresUiState,
    perZone: Boolean,
    colorEnabled: Boolean,
    brightnessEnabled: Boolean,
    onTargetChange: (EditTarget) -> Unit,
    onColorChange: (RgbColor) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    gradientActions: GradientActions,
    modeActions: ModeActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val dynamic = state.mode.isDynamic || (state.mode == AppMode.GRADIENT && state.gradientAnimated)
        val gradientMode = state.mode == AppMode.GRADIENT
        val previewFrame = state.devicePreviewFrame()
        val sceneTarget =
            if (gradientMode) {
                when (state.gradient.selectedStopIndex) {
                    0 -> EditTarget.LEFT
                    state.gradient.stops.lastIndex -> EditTarget.RIGHT
                    else -> EditTarget.BOTH
                }
            } else {
                state.editTarget
            }
        val sceneTargetChange: (EditTarget) -> Unit = { target ->
            if (gradientMode) {
                when (target) {
                    EditTarget.LEFT -> gradientActions.onStopChange(0)
                    EditTarget.RIGHT -> gradientActions.onStopChange(state.gradient.stops.lastIndex)
                    EditTarget.BOTH -> Unit
                }
            } else {
                onTargetChange(target)
            }
        }
        val sceneEnabled = state.canWrite && colorEnabled && !dynamic

        @Composable
        fun Scene(sceneModifier: Modifier) {
            if (state.mode == AppMode.AUDIO) {
                AudioDeviceScene(
                    frame = state.currentFrame,
                    layout = state.detected?.gridLayout,
                    level = AudioSensitivity.adjust(state.audio.level, state.audioSensitivityDb),
                    capturing = state.audio.status == AudioCaptureStatus.CAPTURING,
                    scale = state.audioScale,
                    power = state.effectivePower,
                    projection = state.ledColorProjection,
                    modifier = sceneModifier,
                )
            } else {
                DeviceScene(
                    frame = previewFrame,
                    layout = state.detected?.gridLayout,
                    selectedTarget = sceneTarget,
                    power = state.effectivePower,
                    enabled = sceneEnabled,
                    perZone = perZone && !dynamic,
                    projection = state.ledColorProjection,
                    onTargetChange = sceneTargetChange,
                    showBoth = !gradientMode && !dynamic,
                    modifier = sceneModifier,
                )
            }
        }

        @Composable
        fun Panel(panelModifier: Modifier) {
            ModeControlPanel(
                state = state,
                perZone = perZone,
                colorEnabled = colorEnabled,
                brightnessEnabled = brightnessEnabled,
                onTargetChange = onTargetChange,
                onColorChange = onColorChange,
                onSaturationChange = onSaturationChange,
                onBrightnessChange = onBrightnessChange,
                gradientActions = gradientActions,
                modeActions = modeActions,
                modifier = panelModifier,
            )
        }

        if (maxWidth >= 760.dp) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Scene(Modifier.weight(0.88f).fillMaxHeight())
                Panel(Modifier.weight(1.12f).fillMaxHeight())
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Scene(Modifier.fillMaxWidth().height(360.dp))
                Panel(Modifier.fillMaxWidth().height(440.dp))
            }
        }
    }
}

@Composable
private fun ControlStatusCard(
    title: String,
    description: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryAction: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().prismaticPanel(RoundedCornerShape(28.dp), strong = true),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null && onAction != null) {
                Button(onClick = onAction) { Text(action) }
            }
            if (secondaryAction != null && onSecondaryAction != null) {
                OutlinedButton(onClick = onSecondaryAction) { Text(secondaryAction) }
            }
        }
    }
}
