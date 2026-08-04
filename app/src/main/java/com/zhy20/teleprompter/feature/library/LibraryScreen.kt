package com.zhy20.teleprompter.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.app.AppState
import com.zhy20.teleprompter.core.design.AppColors
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.design.components.AppCard
import com.zhy20.teleprompter.core.design.components.PrimaryButton
import com.zhy20.teleprompter.core.design.components.RemoteStatusCard
import com.zhy20.teleprompter.core.design.components.roundedClickable
import com.zhy20.teleprompter.core.design.components.SecondaryButton
import com.zhy20.teleprompter.core.model.Script
import com.zhy20.teleprompter.core.model.currentNormalEstimatedDurationSeconds
import com.zhy20.teleprompter.core.util.formatDuration
import com.zhy20.teleprompter.core.util.formatModifiedAt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    appState: AppState,
    onNewScript: () -> Unit,
    onEdit: (String) -> Unit,
    onSetup: (String) -> Unit,
    onRemote: () -> Unit,
    onSettings: () -> Unit,
) {
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var showFolderDialog by remember { mutableStateOf(false) }
    val visibleScripts = when (selectedFolder) {
        null -> appState.scripts
        "uncategorized" -> appState.scripts.filter { it.folderId == null }
        else -> appState.scripts.filter { it.folderId == selectedFolder }
    }

    if (showFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text(stringResource(R.string.new_folder)) },
            text = { TextField(folderName, { folderName = it }, label = { Text(stringResource(R.string.folder_name)) }) },
            confirmButton = {
                TextButton(
                    onClick = { showFolderDialog = false },
                    modifier = Modifier.clip(MaterialTheme.shapes.medium),
                    enabled = folderName.isNotBlank(),
                ) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFolderDialog = false },
                    modifier = Modifier.clip(MaterialTheme.shapes.medium),
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.Background)) {
        val expanded = maxWidth >= 840.dp
        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                LibrarySidebar(
                    appState = appState,
                    selectedFolder = selectedFolder,
                    onFolder = { selectedFolder = it },
                    onNewFolder = { showFolderDialog = true },
                    onSettings = onSettings,
                    onRemote = onRemote,
                )
                LibraryContent(
                    scripts = visibleScripts,
                    onNewScript = onNewScript,
                    onEdit = onEdit,
                    onSetup = onSetup,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Scaffold(
                containerColor = AppColors.Background,
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.library_title), fontWeight = FontWeight.Bold) },
                        actions = {
                            TextButton(
                                onClick = onRemote,
                                modifier = Modifier.clip(MaterialTheme.shapes.medium),
                            ) { Text(stringResource(R.string.remote_controller)) }
                            TextButton(
                                onClick = onSettings,
                                modifier = Modifier.clip(MaterialTheme.shapes.medium),
                            ) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface),
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(onClick = onNewScript, containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary) {
                        Icon(Icons.Default.Add, stringResource(R.string.new_script))
                    }
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    ) {
                        item { FilterChipLabel(stringResource(R.string.all_scripts), selectedFolder == null) { selectedFolder = null } }
                        item { FilterChipLabel(stringResource(R.string.uncategorized), selectedFolder == "uncategorized") { selectedFolder = "uncategorized" } }
                        items(appState.folders.size) { index ->
                            val folder = appState.folders[index]
                            FilterChipLabel(folder.name, selectedFolder == folder.id) { selectedFolder = folder.id }
                        }
                        item { FilterChipLabel(stringResource(R.string.new_folder), false) { showFolderDialog = true } }
                    }
                    RemoteStatusCard(appState.remoteConnectionState, onRemote, Modifier.padding(horizontal = AppSpacing.md))
                    LibraryGrid(visibleScripts, onEdit, onSetup, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FilterChipLabel(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        border = BorderStroke(1.dp, if (selected) AppColors.Primary else AppColors.Border),
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = if (selected) AppColors.Secondary.copy(alpha = .45f) else Color.Transparent,
            labelColor = if (selected) AppColors.TextPrimary else AppColors.TextSecondary,
        ),
    )
}

@Composable
private fun LibrarySidebar(
    appState: AppState,
    selectedFolder: String?,
    onFolder: (String?) -> Unit,
    onNewFolder: () -> Unit,
    onSettings: () -> Unit,
    onRemote: () -> Unit,
) {
    Surface(color = AppColors.Surface, border = BorderStroke(1.dp, AppColors.Border)) {
        Column(Modifier.width(268.dp).fillMaxHeight().padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(36.dp), shape = MaterialTheme.shapes.small, color = AppColors.Primary) {
                    Icon(Icons.Default.Description, null, Modifier.padding(7.dp), tint = AppColors.TextPrimary)
                }
                Spacer(Modifier.width(AppSpacing.sm))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            }
            SidebarGroupTitle(stringResource(R.string.resources_group))
            SidebarItem(stringResource(R.string.all_scripts), selectedFolder == null, Icons.Default.Description) { onFolder(null) }
            SidebarItem(stringResource(R.string.uncategorized), selectedFolder == "uncategorized", Icons.Default.Folder) { onFolder("uncategorized") }
            appState.folders.forEach { folder -> SidebarItem(folder.name, selectedFolder == folder.id, Icons.Default.Folder) { onFolder(folder.id) } }
            HorizontalDivider(color = AppColors.Border.copy(alpha = .7f))
            SidebarGroupTitle(stringResource(R.string.management_group))
            SidebarItem(stringResource(R.string.new_folder), false, Icons.Default.Add, onNewFolder)
            SidebarItem(stringResource(R.string.settings), false, Icons.Default.Settings, onSettings)
            Spacer(Modifier.weight(1f))
            RemoteStatusCard(appState.remoteConnectionState, onRemote)
        }
    }
}

@Composable
private fun SidebarGroupTitle(text: String) = Text(text, color = AppColors.TextWeak, style = MaterialTheme.typography.labelMedium)

@Composable
private fun SidebarItem(text: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier.fillMaxWidth().height(48.dp).roundedClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = if (selected) AppColors.Secondary.copy(alpha = .45f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, AppColors.Border) else null,
    ) {
        Row(Modifier.padding(horizontal = AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) AppColors.Primary else AppColors.TextWeak)
            Spacer(Modifier.width(AppSpacing.sm))
            Text(text, color = if (selected) AppColors.TextPrimary else AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LibraryContent(scripts: List<Script>, onNewScript: () -> Unit, onEdit: (String) -> Unit, onSetup: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight().padding(AppSpacing.xl)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineLarge,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(stringResource(R.string.new_script), onNewScript) { Icon(Icons.Default.Add, null) }
        }
        Spacer(Modifier.height(AppSpacing.lg))
        LibraryGrid(scripts, onEdit, onSetup, Modifier.weight(1f))
    }
}

@Composable
private fun LibraryGrid(scripts: List<Script>, onEdit: (String) -> Unit, onSetup: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(360.dp),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(AppSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        if (scripts.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = AppSpacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    Icon(Icons.Default.Description, null, Modifier.size(48.dp), tint = AppColors.TextWeak)
                    Text(stringResource(R.string.empty_library_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.empty_library_body), color = AppColors.TextSecondary)
                }
            }
        }
        items(scripts, key = { it.id }) { script -> ScriptCard(script, onEdit, onSetup) }
    }
}

@Composable
private fun ScriptCard(script: Script, onEdit: (String) -> Unit, onSetup: (String) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(script.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                script.folderId?.let { folder ->
                    Surface(shape = CircleShape, color = AppColors.Secondary.copy(alpha = .35f), border = BorderStroke(1.dp, AppColors.Border)) {
                        Text(folder, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Text(script.plainTextPreview, color = AppColors.TextSecondary, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(
                    stringResource(R.string.words_format, script.wordCount),
                    stringResource(R.string.estimated_duration_format, formatDuration(script.currentNormalEstimatedDurationSeconds())),
                    stringResource(R.string.modified_format, formatModifiedAt(script.lastModifiedAt)),
                ).joinToString("  ·  "),
                color = AppColors.TextWeak,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            HorizontalDivider(color = AppColors.Border)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                SecondaryButton(stringResource(R.string.edit), { onEdit(script.id) }, Modifier.weight(1f)) { Icon(Icons.Default.Edit, null) }
                PrimaryButton(stringResource(R.string.start_prompting), { onSetup(script.id) }, Modifier.weight(1.6f)) { Icon(Icons.Default.PlayArrow, null) }
            }
        }
    }
}
