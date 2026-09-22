package com.hooandee.colores.ui

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.profiles.ConfiguredProfile
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
    val configured = remember(state.configuredProfiles) { state.configuredProfiles.associateBy { it.packageName } }
    val configuredApps = remember(state.profileApps, configured) { state.profileApps.filter { it.packageName in configured } }
    val visibleApps = remember(state.profileApps, query) { filterApps(state.profileApps, query) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val landscape = isUsableLandscape(maxWidth, maxHeight)
            val shape = RoundedCornerShape(32.dp)
            Surface(
                modifier =
                    Modifier
                        .widthIn(max = if (landscape) 1180.dp else 620.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .prismaticPanel(shape, strong = true),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = shape,
            ) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
                    ProfilesHeader(onDismiss)
                    Spacer(Modifier.height(14.dp))
                    val editing: @Composable (Modifier) -> Unit = { modifier ->
                        EditingColumn(
                            state = state,
                            configured = configured,
                            configuredApps = configuredApps,
                            onGlobal = onGlobal,
                            onApp = onApp,
                            onFollowGlobal = onFollowGlobal,
                            onForget = onForget,
                            modifier = modifier,
                        )
                    }
                    val catalog: @Composable (Modifier) -> Unit = { modifier ->
                        AppCatalog(
                            apps = visibleApps,
                            query = query,
                            onQueryChange = { query = it },
                            configured = configured,
                            selectedPackage = (state.profileScope as? ProfileScope.App)?.packageName,
                            onApp = onApp,
                            modifier = modifier,
                        )
                    }
                    if (landscape) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            editing(Modifier.width(300.dp).fillMaxHeight())
                            catalog(Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            editing(Modifier.fillMaxWidth().heightIn(max = 300.dp))
                            catalog(Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilesHeader(onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_profiles_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.app_profiles_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onDismiss) { Text(stringResource(R.string.profile_done)) }
    }
}

@Composable
private fun EditingColumn(
    state: ColoresUiState,
    configured: Map<String, ConfiguredProfile>,
    configuredApps: List<LaunchableApp>,
    onGlobal: () -> Unit,
    onApp: (String) -> Unit,
    onFollowGlobal: (Boolean) -> Unit,
    onForget: () -> Unit,
    modifier: Modifier,
) {
    val scope = state.profileScope as? ProfileScope.App
    val selectedApp = state.selectedProfileApp()
    val listedApps = if (selectedApp != null && selectedApp !in configuredApps) listOf(selectedApp) + configuredApps else configuredApps
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { ProfileSectionLabel(stringResource(R.string.profile_section_saved)) }
        item {
            ProfileRow(
                title = stringResource(R.string.profile_global),
                subtitle = stringResource(R.string.profile_global_short),
                icon = null,
                selected = scope == null,
                swatch = null,
                onClick = onGlobal,
            )
        }
        items(listedApps, key = { "listed:${it.packageName}" }) { app ->
            val profile = configured[app.packageName]?.profile
            val selected = scope?.packageName == app.packageName
            val follows = selected && state.profileScopeState.followsGlobal
            val subtitle =
                if (follows || profile == null) {
                    stringResource(R.string.profile_same_as_global)
                } else {
                    navLabel(profile.mode)
                }
            if (selected) {
                SelectedAppProfileCard(
                    app = app,
                    subtitle = subtitle,
                    swatch = profile?.solidColor?.takeUnless { follows },
                    followsGlobal = state.profileScopeState.followsGlobal,
                    canDelete = state.profileScopeState.hasAppProfile,
                    onFollowGlobal = onFollowGlobal,
                    onForget = onForget,
                )
            } else {
                ProfileRow(
                    title = app.label,
                    subtitle = subtitle,
                    icon = app.icon,
                    selected = false,
                    swatch = profile?.solidColor,
                    onClick = { onApp(app.packageName) },
                )
            }
        }
        if (listedApps.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.profile_section_saved_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SelectedAppProfileCard(
    app: LaunchableApp,
    subtitle: String,
    swatch: RgbColor?,
    followsGlobal: Boolean,
    canDelete: Boolean,
    onFollowGlobal: (Boolean) -> Unit,
    onForget: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().semantics { selected = true },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileIcon(app.icon, app.label, 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                swatch?.let { ProfileSwatch(it) }
            }
            Text(
                stringResource(R.string.profile_lights_question, app.label),
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = 0.8f),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileChoiceButton(
                    label = stringResource(R.string.profile_same_as_global),
                    selected = followsGlobal,
                    onClick = { if (!followsGlobal) onFollowGlobal(true) },
                    modifier = Modifier.weight(1f),
                )
                ProfileChoiceButton(
                    label = stringResource(R.string.profile_custom),
                    selected = !followsGlobal,
                    onClick = { if (followsGlobal) onFollowGlobal(false) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (canDelete) {
                TextButton(onClick = onForget, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.profile_forget))
                }
            }
        }
    }
}

@Composable
private fun ProfileChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp).semantics { this.selected = selected },
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else LocalContentColor.current,
        border = if (selected) null else BorderStroke(1.dp, LocalContentColor.current.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) Text("✓ ", style = MaterialTheme.typography.labelLarge)
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProfileSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun AppCatalog(
    apps: List<LaunchableApp>,
    query: String,
    onQueryChange: (String) -> Unit,
    configured: Map<String, ConfiguredProfile>,
    selectedPackage: String?,
    onApp: (String) -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text(stringResource(R.string.profile_search)) },
            leadingIcon = { Text("⌕", style = MaterialTheme.typography.titleLarge) },
        )
        if (apps.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.profile_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 96.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                gridItems(apps, key = { it.packageName }) { app ->
                    AppTile(
                        app = app,
                        swatch = configured[app.packageName]?.profile?.solidColor,
                        selected = app.packageName == selectedPackage,
                        onClick = { onApp(app.packageName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTile(
    app: LaunchableApp,
    swatch: RgbColor?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                ProfileIcon(app.icon, app.label, 44.dp)
                swatch?.let {
                    ProfileSwatch(it, Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
                }
            }
            Text(
                app.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProfileSwatch(
    color: RgbColor,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(Color(color.red, color.green, color.blue))
            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
    )
}

@Composable
private fun ProfileRow(
    title: String,
    subtitle: String,
    icon: Drawable?,
    selected: Boolean,
    swatch: RgbColor?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileIcon(icon, title, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            swatch?.let { ProfileSwatch(it) }
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
