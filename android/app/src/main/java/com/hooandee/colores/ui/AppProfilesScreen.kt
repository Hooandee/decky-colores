package com.hooandee.colores.ui

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.hooandee.colores.R
import com.hooandee.colores.apps.LaunchableApp
import com.hooandee.colores.apps.filterApps
import com.hooandee.colores.profiles.ProfileScope

@Composable
fun ProfileSelectorPill(
    state: ColoresUiState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val app = state.selectedProfileApp()
    val label = app?.label ?: stringResource(R.string.profile_global)
    val description = stringResource(R.string.profile_editing, label)
    val shape = RoundedCornerShape(999.dp)
    Surface(
        onClick = onOpen,
        modifier = modifier.widthIn(max = 220.dp).semantics { contentDescription = description },
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .prismaticPanel(shape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileIcon(app?.icon, label, 24.dp)
            Text(
                text = if (compact) label else description,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("›", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun AppProfilesDialog(
    state: ColoresUiState,
    onDismiss: () -> Unit,
    onGlobal: () -> Unit,
    onApp: (String) -> Unit,
    onFollowGlobal: (Boolean) -> Unit,
    onForget: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val selectedApp = state.profileScope as? ProfileScope.App
    val selectedProfileApp = state.selectedProfileApp()
    val selectedLabel = selectedProfileApp?.label ?: stringResource(R.string.profile_global)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .prismaticPanel(RoundedCornerShape(28.dp), strong = true),
        ) {
            LazyColumn(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(stringResource(R.string.app_profiles_title), style = MaterialTheme.typography.headlineSmall)
                }
                item {
                    SelectedProfileSummary(
                        icon = selectedProfileApp?.icon,
                        label = selectedLabel,
                        subtitle =
                            when {
                                selectedApp == null -> stringResource(R.string.profile_global_description)
                                state.profileScopeState.followsGlobal -> stringResource(R.string.profile_using_global)
                                else -> stringResource(R.string.profile_own_active)
                            },
                    )
                }
                selectedApp?.let {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onFollowGlobal(!state.profileScopeState.followsGlobal) }) {
                                Text(
                                    if (state.profileScopeState.followsGlobal) {
                                        stringResource(R.string.profile_use_own)
                                    } else {
                                        stringResource(R.string.profile_follow_global)
                                    },
                                )
                            }
                            if (state.profileScopeState.hasAppProfile) {
                                OutlinedButton(onClick = onForget) { Text(stringResource(R.string.profile_forget)) }
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.profile_search)) },
                    )
                }
                item {
                    ProfileRow(
                        title = stringResource(R.string.profile_global),
                        subtitle = stringResource(R.string.profile_global_description),
                        icon = null,
                        selected = state.profileScope == ProfileScope.Global,
                        onClick = onGlobal,
                    )
                }
                items(filterApps(state.profileApps, query), key = { it.packageName }) { app ->
                    ProfileRow(
                        title = app.label,
                        subtitle = app.packageName,
                        icon = app.icon,
                        selected = selectedApp?.packageName == app.packageName,
                        onClick = { onApp(app.packageName) },
                    )
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = onDismiss) {
                            Text(stringResource(R.string.profile_done))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    title: String,
    subtitle: String,
    icon: Drawable?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileIcon(icon, title, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SelectedProfileSummary(
    icon: Drawable?,
    label: String,
    subtitle: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileIcon(icon, label, 46.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.profile_editing_context),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                )
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun ProfileIcon(
    icon: Drawable?,
    label: String,
    iconSize: Dp,
) {
    Surface(
        modifier = Modifier.size(iconSize),
        shape = RoundedCornerShape(iconSize * 0.28f),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        if (icon == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(label.take(1).uppercase(), fontWeight = FontWeight.Bold)
            }
        } else {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { view ->
                    view.setImageDrawable(icon)
                    view.contentDescription = label
                },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .semantics { contentDescription = label },
            )
        }
    }
}

private fun ColoresUiState.selectedProfileApp(): LaunchableApp? {
    val packageName = (profileScope as? ProfileScope.App)?.packageName ?: return null
    return profileApps.firstOrNull { it.packageName == packageName }
}
