package com.hooandee.colores.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
import com.hooandee.colores.settings.AppAppearance
import com.hooandee.colores.settings.AppLanguage
import com.hooandee.colores.settings.ThemeMode
import kotlin.math.roundToInt

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
) {
    var reportOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
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
            if (state.configuredProfiles.isEmpty()) {
                item { EmptyProfilesCard() }
            } else {
                item { ProfileSummaryRow(state) }
            }
            item { SettingsSectionLabel(stringResource(R.string.settings_app_section)) }
            item {
                LanguageCard(
                    selected = AppLanguage.fromLanguageTag(currentLanguageTag),
                    onSelected = onLanguageChange,
                )
            }
            item {
                GeneralBehaviorCard(
                    state = state,
                    onChargerOnlyChange = onChargerOnlyChange,
                    onProfileAutomationChange = onProfileAutomationChange,
                    onGrantUsage = onGrantUsage,
                )
            }
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
            item { SettingsSectionLabel(stringResource(R.string.settings_help_section)) }
            item { DiagnosticsCard(state) }
            item {
                SettingsActionCard(
                    glyph = "!",
                    title = stringResource(R.string.report_button),
                    description = stringResource(R.string.report_button_description),
                    onClick = { reportOpen = true },
                )
            }
            item { Spacer(Modifier.height(10.dp)) }
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
            modifier = Modifier.size(42.dp).semantics { contentDescription = backLabel },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f,
    )
}

@Composable
private fun EmptyProfilesCard() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settings_profiles_empty), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.settings_profiles_empty_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileSummaryRow(state: ColoresUiState) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(state.configuredProfiles, key = ConfiguredProfile::packageName) { configured ->
            val app = state.profileApps.firstOrNull { it.packageName == configured.packageName }
            ProfileSummaryCard(
                profile = configured,
                label = app?.label ?: configured.packageName,
            )
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
        modifier = Modifier.width(230.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(34.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface) {
                    Box(contentAlignment = Alignment.Center) { Text(label.take(1).uppercase(), fontWeight = FontWeight.Bold) }
                }
                Column(Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        profile.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.66f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProfileValue(profileModeLabel(profile.profile.mode))
                ProfileValue(stringResource(R.string.settings_profile_brightness, profile.profile.brightness))
                if (profile.profile.mode == AppMode.EFFECT || profile.profile.mode == AppMode.GRADIENT) {
                    val speed = if (profile.profile.mode == AppMode.GRADIENT) profile.profile.gradientSpeed else profile.profile.speed
                    ProfileValue(stringResource(R.string.settings_profile_speed, speed))
                }
            }
            Row(Modifier.fillMaxWidth().height(7.dp)) {
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
private fun LanguageCard(
    selected: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
) {
    SettingsCard(title = stringResource(R.string.settings_language), subtitle = stringResource(R.string.settings_language_description)) {
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
    SettingsCard(title = stringResource(R.string.settings_appearance), subtitle = stringResource(R.string.settings_appearance_description)) {
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
private fun GeneralBehaviorCard(
    state: ColoresUiState,
    onChargerOnlyChange: (Boolean) -> Unit,
    onProfileAutomationChange: (Boolean) -> Unit,
    onGrantUsage: () -> Unit,
) {
    SettingsCard(
        title = stringResource(R.string.settings_behavior),
        subtitle = stringResource(R.string.settings_behavior_description),
    ) {
        SettingsToggleRow(
            title = stringResource(R.string.charger_only_title),
            description = stringResource(R.string.charger_only_description),
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
private fun SettingsToggleRow(
    title: String,
    description: String,
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
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun DiagnosticsCard(state: ColoresUiState) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    SettingsCard(title = stringResource(R.string.settings_diagnostics), subtitle = stringResource(R.string.settings_diagnostics_description)) {
        DiagnosticLine(stringResource(R.string.settings_diagnostic_device), state.detected?.friendlyName ?: stringResource(R.string.device_unknown))
        DiagnosticLine(stringResource(R.string.settings_diagnostic_control), controlStatusLabel(state.controlAccess))
        val unavailable = stringResource(R.string.settings_diagnostic_unavailable)
        DiagnosticLine(stringResource(R.string.settings_diagnostic_driver), state.detected?.led?.diagnosticDriver() ?: unavailable)
        DiagnosticLine(stringResource(R.string.settings_diagnostic_route), state.detected?.led?.diagnosticRoute() ?: unavailable)
        DiagnosticLine(
            stringResource(R.string.settings_diagnostic_zones),
            state.detected?.capabilities?.zones?.toString() ?: "0",
        )
        DiagnosticLine(stringResource(R.string.settings_diagnostic_profiles), state.configuredProfiles.size.toString())
        DiagnosticLine(stringResource(R.string.settings_diagnostic_version), version)
    }
}

@Composable
private fun DiagnosticLine(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
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
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
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
