package com.hooandee.colores.ui

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
) {
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
                        .widthIn(max = 900.dp)
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
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text(stringResource(R.string.report_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text(stringResource(R.string.report_intro), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Text(
                text = stringResource(R.string.report_categories).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(REPORT_CATEGORIES) { category ->
                    FilterChip(
                        selected = category in selected,
                        onClick = { if (!lockedCategories) onToggle(category) },
                        enabled = !lockedCategories || category in selected,
                        label = { Text(reportCategoryLabel(category)) },
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                label = { Text(stringResource(R.string.report_description)) },
                supportingText = { if (!canSubmitReport(text)) Text(stringResource(R.string.report_description_hint)) },
            )
        }
        item { ReportPrivacyCard() }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                OutlinedButton(onClick = onClose) { Text(stringResource(R.string.report_close)) }
                Button(onClick = onSubmit, enabled = canSubmitReport(text)) { Text(stringResource(R.string.report_send)) }
            }
        }
    }
}

@Composable
private fun ReportPrivacyCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(R.string.report_privacy_title), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.report_privacy_public), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.report_privacy_private), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.report_privacy_no_pii), style = MaterialTheme.typography.bodySmall)
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
        Text("✓", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.report_done_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.report_done_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                code,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
