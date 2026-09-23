package com.hooandee.colores.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hooandee.colores.R
import com.hooandee.colores.report.REPORT_CATEGORIES
import com.hooandee.colores.report.ReportResult
import com.hooandee.colores.report.canSubmitReport

@Composable
internal fun AndroidReportDialog(
    state: ColoresUiState,
    onDismiss: () -> Unit,
    onSubmit: (List<String>, String) -> Unit,
    initialCategories: Set<String> = emptySet(),
    initialText: String = "",
    lockedCategories: Boolean = false,
    onOpen: () -> Unit = {},
) {
    LaunchedEffect(Unit) { onOpen() }
    var selected by remember(initialCategories) { mutableStateOf(initialCategories) }
    var text by remember(initialText) { mutableStateOf(initialText) }
    val submission = state.reportSubmission
    val context = LocalContext.current
    Dialog(
        onDismissRequest = { if (!submission.sending) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .widthIn(max = if (submission.result == null && !submission.sending) 900.dp else 560.dp)
                        .fillMaxWidth()
                        .heightIn(max = 680.dp)
                        .prismaticPanel(RoundedCornerShape(28.dp), strong = true),
                shape = RoundedCornerShape(28.dp),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                when (val result = submission.result) {
                    is ReportResult.Success ->
                        ReportResultBody(
                            code = result.code,
                            onCopy = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard.setPrimaryClip(ClipData.newPlainText("Colores", result.code))
                            },
                            onClose = onDismiss,
                        )
                    is ReportResult.Failure ->
                        ReportFailureBody(
                            saved = result.savedPath != null,
                            onRetry = { onSubmit(selected.toList(), text) },
                            onClose = onDismiss,
                        )
                    null ->
                        if (submission.sending) {
                            Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.report_sending), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            ReportForm(
                                selected = selected,
                                text = text,
                                onToggle = { category -> selected = selected.toggle(category) },
                                onTextChange = { text = it },
                                onSubmit = { onSubmit(selected.toList(), text) },
                                onClose = onDismiss,
                                lockedCategories = lockedCategories,
                            )
                        }
                }
            }
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportForm(
    selected: Set<String>,
    text: String,
    onToggle: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    lockedCategories: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 720.dp
        val compact = maxWidth < 480.dp
        val gutter = if (compact) 18.dp else 24.dp
        val scrollState = rememberScrollState()
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = gutter, end = gutter - 8.dp, top = gutter - 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.report_title),
                    modifier = Modifier.weight(1f),
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.report_close),
                    )
                }
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .scrollFadeEdges(scrollState)
                        .verticalScroll(scrollState)
                        .padding(horizontal = gutter, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val intro: @Composable () -> Unit = {
                    Text(
                        stringResource(R.string.report_intro),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val categories: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.report_categories).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 1.2.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            REPORT_CATEGORIES.forEach { category ->
                                ReportCategoryChip(
                                    label = reportCategoryLabel(category),
                                    selected = category in selected,
                                    enabled = !lockedCategories || category in selected,
                                    onClick = { if (!lockedCategories) onToggle(category) },
                                )
                            }
                        }
                    }
                }
                val description: @Composable (Int) -> Unit = { lines ->
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = lines,
                        maxLines = lines + 4,
                        shape = RoundedCornerShape(18.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                                focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            ),
                        label = { Text(stringResource(R.string.report_description)) },
                        supportingText = { if (!canSubmitReport(text)) Text(stringResource(R.string.report_description_hint)) },
                    )
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(0.5f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            intro()
                            categories()
                        }
                        Column(Modifier.weight(0.5f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            description(4)
                            ReportPrivacyCard()
                        }
                    }
                } else {
                    intro()
                    categories()
                    description(if (compact) 3 else 4)
                    ReportPrivacyCard()
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = gutter)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = if (compact) Modifier.weight(1f).heightIn(min = 48.dp) else Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.report_close)) }
                Button(
                    onClick = onSubmit,
                    enabled = canSubmitReport(text),
                    modifier = if (compact) Modifier.weight(1f).heightIn(min = 48.dp) else Modifier.widthIn(min = 160.dp).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.report_send)) }
            }
        }
    }
}

@Composable
private fun ReportCategoryChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) },
        modifier = Modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(999.dp),
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = Color.Transparent,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (LocalPrismaticStyle.current.light) 0.16f else 0.26f),
                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = Color.Transparent,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = enabled,
                selected = selected,
                borderColor = MaterialTheme.colorScheme.outline,
                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                disabledSelectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                borderWidth = 1.dp,
                selectedBorderWidth = 1.dp,
            ),
    )
}

@Composable
private fun ReportPrivacyCard() {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .glassTint(MaterialTheme.colorScheme.onSurface, shape, emphasis = 0.2f)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.report_privacy_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        listOf(R.string.report_privacy_public, R.string.report_privacy_private, R.string.report_privacy_no_pii).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .size(5.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
                Text(
                    stringResource(line),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun reportCategoryLabel(category: String): String =
    when (category) {
        "color" -> stringResource(R.string.report_category_color)
        "brightness" -> stringResource(R.string.report_category_brightness)
        "effects" -> stringResource(R.string.report_category_effects)
        "sensors" -> stringResource(R.string.report_category_sensors)
        "audio" -> stringResource(R.string.report_category_audio)
        "ambilight" -> stringResource(R.string.report_category_ambilight)
        "profiles" -> stringResource(R.string.report_category_profiles)
        "learning" -> stringResource(R.string.report_category_learning)
        else -> stringResource(R.string.report_category_other)
    }

@Composable
private fun ReportResultBody(
    code: String,
    onCopy: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(64.dp).glassTint(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Text(stringResource(R.string.report_done_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.report_done_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.glassTint(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp), emphasis = 0.7f)) {
            Text(
                code,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 1.5.sp,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCopy) { Text(stringResource(R.string.report_copy)) }
            Button(onClick = onClose) { Text(stringResource(R.string.report_close)) }
        }
    }
}

@Composable
private fun ReportFailureBody(
    saved: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.report_error_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            if (saved) stringResource(R.string.report_error_saved) else stringResource(R.string.report_error_not_saved),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onRetry) { Text(stringResource(R.string.report_retry)) }
            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.report_close)) }
        }
    }
}
