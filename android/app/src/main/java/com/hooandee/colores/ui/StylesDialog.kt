package com.hooandee.colores.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hooandee.colores.R
import com.hooandee.colores.control.AppMode
import com.hooandee.colores.profiles.SavedStyle
import com.hooandee.colores.profiles.SavedStyles

data class StyleActions(
    val onOpen: () -> Unit = {},
    val onSave: (String) -> Unit = {},
    val onApply: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onExport: (Uri) -> Unit = {},
    val onImport: (Uri) -> Unit = {},
)

@Composable
internal fun StylesDialog(
    styles: List<SavedStyle>,
    notice: StyleNotice?,
    enabled: Boolean,
    actions: StyleActions,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { actions.onOpen() }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(actions.onExport) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(actions.onImport) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.styles_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.styles_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (styles.isEmpty()) {
                    Text(stringResource(R.string.styles_empty), style = MaterialTheme.typography.bodyMedium)
                }
                styles.forEach { style ->
                    StyleRow(style, enabled, actions)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(SavedStyles.NAME_MAX) },
                    label = { Text(stringResource(R.string.styles_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        actions.onSave(name)
                        name = ""
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.styles_save))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exporter.launch(STYLES_FILE_NAME) },
                        enabled = styles.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.styles_export))
                    }
                    OutlinedButton(
                        onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.styles_import))
                    }
                }
                notice?.let {
                    Text(noticeText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.styles_close)) }
        },
    )
}

@Composable
private fun StyleRow(
    style: SavedStyle,
    enabled: Boolean,
    actions: StyleActions,
) {
    val deleteLabel = stringResource(R.string.styles_delete, style.name)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { actions.onApply(style.name) }, enabled = enabled, modifier = Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(16.dp).background(swatch(style), CircleShape))
                Text(style.name, maxLines = 1)
            }
        }
        IconButton(onClick = { actions.onDelete(style.name) }, modifier = Modifier.semantics { contentDescription = deleteLabel }) {
            Text("✕")
        }
    }
}

private fun swatch(style: SavedStyle): Brush {
    val stops = if (style.mode == AppMode.GRADIENT && !style.gradient.isNullOrEmpty()) style.gradient else style.colors
    val colors = stops.orEmpty().map { Color(it.red, it.green, it.blue) }
    return when {
        colors.isEmpty() -> Brush.linearGradient(listOf(Color.Gray, Color.Gray))
        colors.size == 1 -> Brush.linearGradient(listOf(colors[0], colors[0]))
        else -> Brush.linearGradient(colors)
    }
}

@Composable
private fun noticeText(notice: StyleNotice): String =
    when (notice) {
        StyleNotice.Saved -> stringResource(R.string.styles_saved)
        is StyleNotice.Applied -> stringResource(R.string.styles_applied, notice.name)
        is StyleNotice.Exported -> pluralStringResource(R.plurals.styles_exported, notice.count, notice.count)
        is StyleNotice.Imported -> stringResource(R.string.styles_imported, notice.added, notice.replaced)
        StyleNotice.Full -> stringResource(R.string.styles_full)
        StyleNotice.Failed -> stringResource(R.string.styles_error)
    }

private const val STYLES_FILE_NAME = "colores-estilos.json"
