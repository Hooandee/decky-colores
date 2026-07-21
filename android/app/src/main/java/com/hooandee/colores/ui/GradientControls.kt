package com.hooandee.colores.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hooandee.colores.R
import com.hooandee.colores.gradient.GradientPreset
import com.hooandee.colores.gradient.SavedGradient
import com.hooandee.colores.led.RgbColor

data class GradientActions(
    val onStopChange: (Int) -> Unit,
    val onPresetChange: (GradientPreset) -> Unit,
    val onSavedChange: (String) -> Unit,
    val onRestore: () -> Unit,
    val onSave: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onColorChange: (RgbColor) -> Unit,
    val onSaturationChange: (Float) -> Unit,
)

@Composable
fun GradientControls(
    state: ColoresUiState,
    actions: GradientActions,
) {
    val gradient = state.gradient
    val projection = state.ledColorProjection
    val projectedStops = gradient.stops.map(projection::display)
    var editorOpen by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GradientPreviewBar(projectedStops)
        SectionLabel(stringResource(R.string.gradient_presets))
        LazyRow(
            modifier = Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(gradient.presets, key = { "preset:${it.id}" }) { preset ->
                GradientTile(
                    label = presetLabel(preset.id),
                    colors = preset.stops.map(projection::display),
                    selected = preset.id == gradient.selectedPresetId,
                    onClick = { actions.onPresetChange(preset) },
                )
            }
            items(gradient.savedGradients, key = { "saved:${it.name}" }) { saved ->
                SavedGradientTile(
                    gradient = saved,
                    colors = saved.stops.map(projection::display),
                    onClick = { actions.onSavedChange(saved.name) },
                    onDelete = { actions.onDelete(saved.name) },
                )
            }
        }
        OutlinedButton(
            onClick = { editorOpen = true },
            enabled = state.canWrite,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.gradient_edit_custom), fontWeight = FontWeight.SemiBold)
        }
    }
    if (editorOpen) {
        GradientEditorDialog(
            state = state,
            actions = actions,
            onDismiss = { editorOpen = false },
        )
    }
}

@Composable
internal fun GradientPreviewBar(colors: List<RgbColor>) {
    val shown = colors.ifEmpty { listOf(RgbColor(34, 35, 43), RgbColor(34, 35, 43)) }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(Brush.horizontalGradient(shown.map(RgbColor::toComposeColor)), RoundedCornerShape(15.dp))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(15.dp)),
    )
}

@Composable
private fun GradientTile(
    label: String,
    colors: List<RgbColor>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shown = colors.ifEmpty { listOf(RgbColor(40, 40, 48), RgbColor(40, 40, 48)) }
    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .width(116.dp)
                .height(68.dp)
                .onFocusChanged { focused = it.isFocused }
                .semantics { this.selected = selected },
        color = Color.Transparent,
        shape = RoundedCornerShape(15.dp),
        border =
            BorderStroke(
                if (selected || focused) 2.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = if (selected) 0.65f else 0.12f),
            ),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Brush.horizontalGradient(shown.map(RgbColor::toComposeColor))),
            )
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SavedGradientTile(
    gradient: SavedGradient,
    colors: List<RgbColor>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val deleteDescription = stringResource(R.string.gradient_delete_named, gradient.name)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GradientTile(gradient.name, colors, selected = false, onClick = onClick)
        TextButton(
            onClick = onDelete,
            modifier = Modifier.height(48.dp).semantics {
                contentDescription = deleteDescription
            },
        ) {
            Text(stringResource(R.string.gradient_delete), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun SectionLabel(label: String) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun presetLabel(id: String): String =
    when (id) {
        "sunset" -> stringResource(R.string.gradient_preset_sunset)
        "ocean" -> stringResource(R.string.gradient_preset_ocean)
        "aurora" -> stringResource(R.string.gradient_preset_aurora)
        "neon" -> stringResource(R.string.gradient_preset_neon)
        "lava" -> stringResource(R.string.gradient_preset_lava)
        "mint" -> stringResource(R.string.gradient_preset_mint)
        "vaporwave" -> stringResource(R.string.gradient_preset_vaporwave)
        "forest" -> stringResource(R.string.gradient_preset_forest)
        "galaxy" -> stringResource(R.string.gradient_preset_galaxy)
        "ember" -> stringResource(R.string.gradient_preset_ember)
        "ice" -> stringResource(R.string.gradient_preset_ice)
        "candy" -> stringResource(R.string.gradient_preset_candy)
        else -> id
    }
