package com.hooandee.colores.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hooandee.colores.R
import com.hooandee.colores.control.AppMode
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.profiles.ConfiguredProfile
import com.hooandee.colores.profiles.ProfileAutomationStatus
import com.hooandee.colores.device.learning.ProbeSurface
import com.hooandee.colores.settings.AppAppearance
import com.hooandee.colores.settings.AppLanguage
import com.hooandee.colores.settings.ThemeMode
import kotlin.math.roundToInt

private const val SUPPORT_CREATOR = "@hooandee"
private const val SOURCE_URL = "https://github.com/Hooandee/decky-colores"
private const val LICENSE_URL = "https://github.com/Hooandee/decky-colores/blob/main/LICENSE"
private const val NOTICE_URL = "https://github.com/Hooandee/decky-colores/blob/main/NOTICE"

@Composable
fun SettingsScreen(
    state: ColoresUiState,
    appearance: AppAppearance,
    currentLanguageTag: String?,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentChange: (RgbColor) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onChargerOnlyChange: (Boolean) -> Unit,
    onProfileAutomationChange: (Boolean) -> Unit,
    onGrantUsage: () -> Unit,
    onLedPreviewChange: (Boolean) -> Unit,
    onSubmitReport: (List<String>, String) -> Unit,
    onResetReport: () -> Unit,
    onOpenHardwareLearning: () -> Unit,
    onForgetLearnedHardware: () -> Unit,
) {
    var reportOpen by remember { mutableStateOf(false) }
    var usageDisclosureOpen by remember { mutableStateOf(false) }
    var legalOpen by remember { mutableStateOf(false) }
    PrismaticBackdrop(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (shouldUseTwoPaneLayout(maxWidth, maxHeight, 760.dp)) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 22.dp),
                ) {
                    SettingsHeader(onBack)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            item { SettingsSectionLabel(stringResource(R.string.settings_profiles_section)) }
                            item { ProfilesOverview(state) }
                            item { SettingsSectionLabel(stringResource(R.string.settings_app_section)) }
                            item {
                                ApplicationCard(
                                    state = state,
                                    selected = AppLanguage.fromLanguageTag(currentLanguageTag),
                                    onSelected = onLanguageChange,
                                    onChargerOnlyChange = onChargerOnlyChange,
                                    onProfileAutomationChange = onProfileAutomationChange,
                                    onGrantUsage = { usageDisclosureOpen = true },
                                )
                            }
                            item { Spacer(Modifier.height(10.dp)) }
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            item { SettingsSectionLabel(stringResource(R.string.settings_appearance)) }
                            item {
                                AppearanceCard(
                                    appearance = appearance,
                                    ledPreviewAvailable = state.detected?.previewCalibration != null,
                                    ledPreviewEnabled = state.ledPreviewEnabled,
                                    onThemeModeChange = onThemeModeChange,
                                    onAccentChange = onAccentChange,
                                    onLedPreviewChange = onLedPreviewChange,
                                )
                            }
                            item { SettingsSectionLabel(stringResource(R.string.settings_support_section)) }
                            item { SupportCard() }
                            item { SettingsSectionLabel(stringResource(R.string.settings_help_section)) }
                            item { DiagnosticsCard(state, onOpenHardwareLearning, onForgetLearnedHardware) }
                            item {
                                SettingsActionCard(
                                    glyph = "!",
                                    title = stringResource(R.string.report_button),
                                    description = stringResource(R.string.report_button_description),
                                    onClick = { reportOpen = true },
                                )
                            }
                            item { SettingsSectionLabel(stringResource(R.string.settings_about_section)) }
                            item { AboutCard(onOpenLegal = { legalOpen = true }) }
                            item { Spacer(Modifier.height(10.dp)) }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item { SettingsHeader(onBack) }
                    item { SettingsSectionLabel(stringResource(R.string.settings_profiles_section)) }
                    item { ProfilesOverview(state) }
                    item { SettingsSectionLabel(stringResource(R.string.settings_app_section)) }
                    item {
                        ApplicationCard(
                            state = state,
                            selected = AppLanguage.fromLanguageTag(currentLanguageTag),
                            onSelected = onLanguageChange,
                            onChargerOnlyChange = onChargerOnlyChange,
                            onProfileAutomationChange = onProfileAutomationChange,
                            onGrantUsage = { usageDisclosureOpen = true },
                        )
                    }
                    item { SettingsSectionLabel(stringResource(R.string.settings_appearance)) }
                    item {
                        AppearanceCard(
                            appearance = appearance,
                            ledPreviewAvailable = state.detected?.previewCalibration != null,
                            ledPreviewEnabled = state.ledPreviewEnabled,
                            onThemeModeChange = onThemeModeChange,
                            onAccentChange = onAccentChange,
                            onLedPreviewChange = onLedPreviewChange,
                        )
                    }
                    item { SettingsSectionLabel(stringResource(R.string.settings_support_section)) }
                    item { SupportCard() }
                    item { SettingsSectionLabel(stringResource(R.string.settings_help_section)) }
                    item { DiagnosticsCard(state, onOpenHardwareLearning, onForgetLearnedHardware) }
                    item {
                        SettingsActionCard(
                            glyph = "!",
                            title = stringResource(R.string.report_button),
                            description = stringResource(R.string.report_button_description),
                            onClick = { reportOpen = true },
                        )
                    }
                    item { SettingsSectionLabel(stringResource(R.string.settings_about_section)) }
                    item { AboutCard(onOpenLegal = { legalOpen = true }) }
                    item { Spacer(Modifier.height(10.dp)) }
                }
            }
        }
    }
    if (reportOpen) {
        AndroidReportDialog(
            state = state,
            onDismiss = {
                reportOpen = false
                onResetReport()
            },
            onSubmit = onSubmitReport,
        )
    }
    if (usageDisclosureOpen) {
        AlertDialog(
            onDismissRequest = { usageDisclosureOpen = false },
            title = { Text(stringResource(R.string.profile_usage_disclosure_title)) },
            text = { Text(stringResource(R.string.profile_usage_disclosure_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        usageDisclosureOpen = false
                        onGrantUsage()
                    },
                ) {
                    Text(stringResource(R.string.profile_usage_disclosure_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { usageDisclosureOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (legalOpen) {
        LegalDialog(onDismiss = { legalOpen = false })
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    val backLabel = stringResource(R.string.settings_back)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBack,
            modifier =
                Modifier
                    .size(48.dp)
                    .prismaticPanel(CircleShape)
                    .semantics { contentDescription = backLabel },
            shape = CircleShape,
            color = Color.Transparent,
        ) {
            Box(contentAlignment = Alignment.Center) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
        }
        Column {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f,
    )
}

@Composable
private fun ProfilesOverview(state: ColoresUiState) {
    SettingsPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("G", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    stringResource(R.string.settings_profiles_global_base),
                    fontWeight = FontWeight.Bold,
                )
                ProfileValue(
                    pluralStringResource(
                        R.plurals.settings_profiles_count,
                        state.configuredProfiles.size,
                        state.configuredProfiles.size,
                    ),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_profiles_automation),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color =
                    if (state.automationStatus == ProfileAutomationStatus.ACTIVE) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    },
            ) {
                Text(
                    automationStatusLabel(state.automationStatus),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (state.automationStatus == ProfileAutomationStatus.ACTIVE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
        if (state.configuredProfiles.isEmpty()) {
            Text(
                stringResource(R.string.settings_profiles_empty_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ProfileSummaryRow(state)
        }
    }
}

@Composable
private fun ProfileSummaryRow(state: ColoresUiState) {
    val listState = rememberLazyListState()
    val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }
    val canScrollBackward by remember { derivedStateOf { listState.canScrollBackward } }
    val scrollable = canScrollForward || canScrollBackward
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(end = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.configuredProfiles, key = ConfiguredProfile::packageName) { configured ->
                val app = state.profileApps.firstOrNull { it.packageName == configured.packageName }
                ProfileSummaryCard(
                    profile = configured,
                    label = app?.label ?: configured.packageName,
                )
            }
        }
        if (scrollable) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("↔", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.settings_profiles_swipe_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ProfileSummaryCard(
    profile: ConfiguredProfile,
    label: String,
) {
    val colors =
        when (profile.profile.mode) {
            AppMode.GRADIENT -> profile.profile.gradientStops
            else -> profile.profile.staticColors.ifEmpty { listOf(profile.profile.solidColor) }
        }
    Surface(
        modifier = Modifier.width(210.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(32.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
                    Box(contentAlignment = Alignment.Center) { Text(label.take(1).uppercase(), fontWeight = FontWeight.Bold) }
                }
                Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    profileModeLabel(profile.profile.mode),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.settings_profile_brightness, profile.profile.brightness),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth().height(4.dp)) {
                colors.take(8).forEach { color ->
                    Box(Modifier.weight(1f).fillMaxSize().background(color.toComposeColor()))
                }
            }
        }
    }
}

@Composable
private fun ProfileValue(text: String) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
        Text(text, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun profileModeLabel(mode: AppMode): String =
    when (mode) {
        AppMode.COLOR -> stringResource(R.string.nav_color)
        AppMode.GRADIENT -> stringResource(R.string.nav_gradient)
        AppMode.EFFECT -> stringResource(R.string.nav_effects)
        AppMode.BATTERY -> stringResource(R.string.sensor_battery)
        AppMode.TEMPERATURE -> stringResource(R.string.sensor_temperature)
        AppMode.PERFORMANCE -> stringResource(R.string.sensor_performance)
        AppMode.CLOCK -> stringResource(R.string.nav_clock)
        AppMode.AUDIO -> stringResource(R.string.nav_audio)
        AppMode.AMBIENT -> stringResource(R.string.nav_ambient)
    }

@Composable
private fun ApplicationCard(
    state: ColoresUiState,
    selected: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
    onChargerOnlyChange: (Boolean) -> Unit,
    onProfileAutomationChange: (Boolean) -> Unit,
    onGrantUsage: () -> Unit,
) {
    SettingsPanel {
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AppLanguage.entries.forEachIndexed { index, language ->
                SegmentedButton(
                    selected = selected == language,
                    onClick = { onSelected(language) },
                    shape = SegmentedButtonDefaults.itemShape(index, AppLanguage.entries.size),
                ) {
                    Text(
                        when (language) {
                            AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
                            AppLanguage.SPANISH -> stringResource(R.string.settings_language_spanish)
                            AppLanguage.ENGLISH -> stringResource(R.string.settings_language_english)
                        },
                    )
                }
            }
        }
        SettingsToggleRow(
            title = stringResource(R.string.charger_only_title),
            checked = state.chargerOnly,
            enabled = state.canWrite,
            onCheckedChange = onChargerOnlyChange,
        )
        SettingsToggleRow(
            title = stringResource(R.string.profile_auto_title),
            description = automationStatusLabel(state.automationStatus),
            checked = state.automationEnabled,
            enabled = state.detected != null,
            onCheckedChange = onProfileAutomationChange,
        )
        if (state.automationStatus == ProfileAutomationStatus.PERMISSION_REQUIRED) {
            OutlinedButton(onClick = onGrantUsage) {
                Text(stringResource(R.string.profile_usage_permission))
            }
        }
    }
}

@Composable
private fun AppearanceCard(
    appearance: AppAppearance,
    ledPreviewAvailable: Boolean,
    ledPreviewEnabled: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentChange: (RgbColor) -> Unit,
    onLedPreviewChange: (Boolean) -> Unit,
) {
    val hsv = appearance.accent.toHsvColor()
    SettingsPanel {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = appearance.themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                ) {
                    Text(
                        when (mode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                            ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                            ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        if (ledPreviewAvailable) {
            SettingsToggleRow(
                title = stringResource(R.string.led_preview_toggle),
                description = stringResource(R.string.settings_led_preview_description),
                checked = ledPreviewEnabled,
                onCheckedChange = onLedPreviewChange,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            stringResource(R.string.settings_accent_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
                RingColorPicker(
                    color = appearance.accent,
                    enabled = true,
                    projection = LedColorProjection(null, false),
                    contentDescription = stringResource(R.string.settings_accent_picker),
                    onColorChange = onAccentChange,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Surface(modifier = Modifier.size(30.dp), shape = CircleShape, color = appearance.accent.toComposeColor()) {}
                    Text(toHex(appearance.accent), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                }
                Text(stringResource(R.string.saturation_title), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = hsv.saturation,
                    onValueChange = { saturation -> onAccentChange(hsv.copy(saturation = saturation).toRgbColor()) },
                    valueRange = 0f..1f,
                )
                OutlinedButton(onClick = { onAccentChange(RgbColor(141, 131, 255)) }) {
                    Text(stringResource(R.string.settings_accent_reset))
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun automationStatusLabel(status: ProfileAutomationStatus): String =
    when (status) {
        ProfileAutomationStatus.DISABLED -> stringResource(R.string.profile_auto_disabled)
        ProfileAutomationStatus.PERMISSION_REQUIRED -> stringResource(R.string.profile_auto_permission_required)
        ProfileAutomationStatus.ACTIVE -> stringResource(R.string.profile_auto_active)
    }

@Composable
private fun SettingsPanel(
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().prismaticPanel(RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun DiagnosticsCard(
    state: ColoresUiState,
    onOpenHardwareLearning: () -> Unit,
    onForgetLearnedHardware: () -> Unit,
) {
    val multipointCandidate = state.hardwareLearningCandidates.any { it.surface == ProbeSurface.HTR3212 }
    SettingsPanel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DiagnosticTile(
                label = stringResource(R.string.settings_diagnostic_device),
                value = state.detected?.friendlyName ?: stringResource(R.string.device_unknown),
                modifier = Modifier.weight(1f),
            )
            DiagnosticTile(
                label = stringResource(R.string.settings_diagnostic_control),
                value = controlStatusLabel(state.controlAccess),
                modifier = Modifier.weight(1f),
                highlighted = state.controlAccess == ControlAccess.ENABLED,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DiagnosticTile(
                label = stringResource(R.string.settings_diagnostic_zones),
                value = state.detected?.capabilities?.zones?.toString() ?: "0",
                modifier = Modifier.weight(1f),
            )
            DiagnosticTile(
                label = stringResource(R.string.settings_diagnostic_profiles),
                value = state.configuredProfiles.size.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        if (state.learnedHardware) {
            Text(
                stringResource(
                    if (multipointCandidate) {
                        R.string.hardware_learning_more_zones_available
                    } else {
                        R.string.hardware_learning_experimental_binding
                    },
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenHardwareLearning, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(
                        stringResource(
                            if (multipointCandidate) {
                                R.string.hardware_learning_try_multipoint
                            } else {
                                R.string.hardware_learning_repeat
                            },
                        ),
                    )
                }
                TextButton(onClick = onForgetLearnedHardware, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.hardware_learning_forget))
                }
            }
        }
    }
}

@Composable
private fun SupportCard() {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().prismaticPanel(shape),
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("♥", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.settings_support_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.settings_support_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SupportPlatform.entries.forEach { platform ->
                    SupportButton(platform, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AboutCard(onOpenLegal: () -> Unit) {
    SettingsPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_about_version, appVersionName()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = onOpenLegal, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.settings_legal_action), modifier = Modifier.weight(1f))
            Text("›")
        }
    }
}

@Composable
private fun LegalDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_legal_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.settings_license_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                ExternalLinkButton(stringResource(R.string.settings_source_code), SOURCE_URL)
                ExternalLinkButton(stringResource(R.string.settings_open_source_license), LICENSE_URL)
                ExternalLinkButton(stringResource(R.string.settings_third_party_notices), NOTICE_URL)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.report_close)) }
        },
    )
}

@Composable
private fun ExternalLinkButton(
    label: String,
    url: String,
) {
    val context = LocalContext.current
    val intent = remember(url) { Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    OutlinedButton(
        onClick = { runCatching { context.startActivity(intent) } },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text("↗")
    }
}

@Composable
private fun SupportButton(
    platform: SupportPlatform,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val label = platform.supportLabel()
    val intent =
        remember(platform) {
            Intent(Intent.ACTION_VIEW, Uri.parse(supportUrl(platform, SUPPORT_CREATOR))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    val enabled = remember(context, intent) { intent.resolveActivity(context.packageManager) != null }
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = { runCatching { context.startActivity(intent) } },
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).semantics { role = Role.Button },
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 0.34f else 0.2f),
        contentColor =
            if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun SupportPlatform.supportLabel(): String =
    stringResource(
        when (this) {
            SupportPlatform.KOFI -> R.string.settings_support_kofi
            SupportPlatform.PAYPAL -> R.string.settings_support_paypal
            SupportPlatform.PATREON -> R.string.settings_support_patreon
        },
    )

@Composable
private fun appVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}

@Composable
private fun DiagnosticTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Surface(
        modifier = modifier.heightIn(min = 72.dp),
        shape = RoundedCornerShape(16.dp),
        color =
            if (highlighted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
            },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Text(
                value,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun controlStatusLabel(status: ControlAccess): String =
    when (status) {
        ControlAccess.ENABLED -> stringResource(R.string.status_connected)
        ControlAccess.USER_PERMISSION_REQUIRED -> stringResource(R.string.permission_title)
        ControlAccess.SERVICE_UNAVAILABLE -> stringResource(R.string.control_service_title)
    }

@Composable
private fun SettingsActionCard(
    glyph: String,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().prismaticPanel(RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(modifier = Modifier.size(38.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Text(glyph, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun toHex(color: RgbColor): String = "#%02X%02X%02X".format(color.red, color.green, color.blue)
