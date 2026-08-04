package com.zhy20.teleprompter.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.zhy20.teleprompter.core.design.components.SecondaryButton
import com.zhy20.teleprompter.core.design.components.roundedClickable
import com.zhy20.teleprompter.core.model.Script
import com.zhy20.teleprompter.core.model.ScriptFolder
import com.zhy20.teleprompter.core.model.currentNormalEstimatedDurationSeconds
import com.zhy20.teleprompter.core.util.formatDuration
import com.zhy20.teleprompter.core.util.formatModifiedAt
import com.zhy20.teleprompter.data.importer.ScriptImportError
import com.zhy20.teleprompter.data.importer.ScriptImportState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    appState: AppState,
    onNewScript: (String?) -> Unit,
    onEdit: (String) -> Unit,
    onSetup: (String) -> Unit,
    onRemote: () -> Unit,
    onSettings: () -> Unit,
    uiState: LibraryUiState? = null,
    importState: ScriptImportState? = null,
    onImportFile: (String?) -> Unit = {},
    onImportErrorDismiss: () -> Unit = {},
    onCreateFolder: (String) -> Unit = {},
    onRenameFolder: (String, String) -> Unit = { _, _ -> },
    onDeleteFolder: (String) -> Unit = {},
    onRenameScript: (String, String) -> Unit = { _, _ -> },
    onMoveScript: (String, String?) -> Unit = { _, _ -> },
    onDeleteScript: (String) -> Unit = {},
    onClearError: () -> Unit = {},
) {
    val state = uiState ?: LibraryUiState(
        loadState = if (appState.scripts.isEmpty() && appState.folders.isEmpty()) LibraryLoadState.Empty else LibraryLoadState.Content,
        scripts = appState.scripts,
        folders = appState.folders,
    )
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<LibraryAction?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val visibleScripts = when (selectedFolder) {
        null -> state.scripts
        "uncategorized" -> state.scripts.filter { it.folderId == null }
        else -> state.scripts.filter { it.folderId == selectedFolder }
    }

    LaunchedEffect(state.folders, selectedFolder) {
        if (selectedFolder != null && selectedFolder != "uncategorized" && state.folders.none { it.id == selectedFolder }) {
            selectedFolder = null
        }
    }
    val errorText = state.operationError?.let { error -> libraryErrorText(error) }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            onClearError()
        }
    }
    val importErrorText = (importState as? ScriptImportState.Error)?.let { scriptImportErrorText(it.reason) }
    LaunchedEffect(importErrorText) {
        if (importErrorText != null) {
            snackbarHostState.showSnackbar(importErrorText)
            onImportErrorDismiss()
        }
    }

    LibraryActionDialog(
        action = action,
        folders = state.folders,
        onDismiss = { action = null },
        onCreateFolder = onCreateFolder,
        onRenameFolder = onRenameFolder,
        onDeleteFolder = onDeleteFolder,
        onRenameScript = onRenameScript,
        onMoveScript = onMoveScript,
        onDeleteScript = onDeleteScript,
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.Background)) {
        val expanded = maxWidth >= 840.dp
        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                LibrarySidebar(
                    appState = appState,
                    folders = state.folders,
                    selectedFolder = selectedFolder,
                    onFolder = { selectedFolder = it },
                    onNewFolder = { action = LibraryAction.NewFolder },
                    onFolderAction = { action = it },
                    onSettings = onSettings,
                    onRemote = onRemote,
                )
                LibraryContent(
                    scripts = visibleScripts,
                    folders = state.folders,
                    loading = state.loadState == LibraryLoadState.Loading,
                    selectedFolder = selectedFolder,
                    importInProgress = importState != null && importState != ScriptImportState.Idle,
                    onNewScript = onNewScript,
                    onImportFile = onImportFile,
                    onEdit = onEdit,
                    onSetup = onSetup,
                    onScriptAction = { action = it },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Scaffold(
                containerColor = AppColors.Background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.library_title), fontWeight = FontWeight.Bold) },
                        actions = {
                            TextButton(onClick = onRemote, modifier = Modifier.clip(MaterialTheme.shapes.medium)) {
                                Text(stringResource(R.string.remote_controller))
                            }
                            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface),
                    )
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    ) {
                        item { FilterChipLabel(stringResource(R.string.all_scripts), selectedFolder == null) { selectedFolder = null } }
                        item { FilterChipLabel(stringResource(R.string.uncategorized), selectedFolder == "uncategorized") { selectedFolder = "uncategorized" } }
                        items(state.folders.size) { index ->
                            val folder = state.folders[index]
                            FolderFilterLabel(
                                folder = folder,
                                selected = selectedFolder == folder.id,
                                onClick = { selectedFolder = folder.id },
                                onRename = { action = LibraryAction.RenameFolder(folder) },
                                onDelete = { action = LibraryAction.DeleteFolder(folder) },
                            )
                        }
                        item { FilterChipLabel(stringResource(R.string.new_folder), false) { action = LibraryAction.NewFolder } }
                    }
                    RemoteStatusCard(appState.remoteConnectionState, onRemote, Modifier.padding(horizontal = AppSpacing.md))
                    LibraryGrid(
                        scripts = visibleScripts,
                        folders = state.folders,
                        loading = state.loadState == LibraryLoadState.Loading,
                        onEdit = onEdit,
                        onSetup = onSetup,
                        onScriptAction = { action = it },
                        modifier = Modifier.weight(1f),
                    )
                    LibraryBottomActions(
                        importInProgress = importState != null && importState != ScriptImportState.Idle,
                        onImportFile = { onImportFile(selectedFolder.takeUnless { it == "uncategorized" }) },
                        onNewScript = { onNewScript(selectedFolder.takeUnless { it == "uncategorized" }) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (expanded) SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
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
private fun FolderFilterLabel(
    folder: ScriptFolder,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilterChipLabel(folder.name, selected, onClick)
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions))
            }
            DropdownMenu(menuOpen, { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    onClick = { menuOpen = false; onRename() },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = { menuOpen = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                )
            }
        }
    }
}

@Composable
private fun LibrarySidebar(
    appState: AppState,
    folders: List<ScriptFolder>,
    selectedFolder: String?,
    onFolder: (String?) -> Unit,
    onNewFolder: () -> Unit,
    onFolderAction: (LibraryAction) -> Unit,
    onSettings: () -> Unit,
    onRemote: () -> Unit,
) {
    Surface(color = AppColors.Surface, border = BorderStroke(1.dp, AppColors.Border)) {
        Column(Modifier.width(268.dp).fillMaxHeight().padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(36.dp), shape = MaterialTheme.shapes.small, color = AppColors.Primary) {
                    Icon(Icons.Default.Description, null, Modifier.padding(7.dp), tint = AppColors.TextPrimary)
                }
                Spacer(Modifier.width(AppSpacing.sm))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            }
            SidebarGroupTitle(stringResource(R.string.resources_group))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = AppSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                item(key = "all_scripts") {
                    SidebarItem(stringResource(R.string.all_scripts), selectedFolder == null, Icons.Default.Description) { onFolder(null) }
                }
                item(key = "uncategorized") {
                    SidebarItem(stringResource(R.string.uncategorized), selectedFolder == "uncategorized", Icons.Default.Folder) { onFolder("uncategorized") }
                }
                lazyColumnItems(folders, key = { it.id }) { folder ->
                    FolderSidebarItem(
                        folder = folder,
                        selected = selectedFolder == folder.id,
                        onClick = { onFolder(folder.id) },
                        onRename = { onFolderAction(LibraryAction.RenameFolder(folder)) },
                        onDelete = { onFolderAction(LibraryAction.DeleteFolder(folder)) },
                    )
                }
            }
            HorizontalDivider(color = AppColors.Border.copy(alpha = .7f))
            SidebarGroupTitle(stringResource(R.string.management_group))
            SidebarItem(stringResource(R.string.new_folder), false, Icons.Default.Add, onNewFolder)
            SidebarItem(stringResource(R.string.settings), false, Icons.Default.Settings, onSettings)
            RemoteStatusCard(appState.remoteConnectionState, onRemote)
        }
    }
}

@Composable
private fun FolderSidebarItem(
    folder: ScriptFolder,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier.fillMaxWidth().height(48.dp).roundedClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = if (selected) AppColors.Secondary.copy(alpha = .45f) else Color.Transparent,
    ) {
        Row(Modifier.padding(start = AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, null, tint = if (selected) AppColors.Primary else AppColors.TextWeak)
            Spacer(Modifier.width(AppSpacing.sm))
            Text(folder.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions)) }
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menuOpen = false; onRename() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuOpen = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                }
            }
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
private fun LibraryContent(
    scripts: List<Script>,
    folders: List<ScriptFolder>,
    loading: Boolean,
    selectedFolder: String?,
    importInProgress: Boolean,
    onNewScript: (String?) -> Unit,
    onImportFile: (String?) -> Unit,
    onEdit: (String) -> Unit,
    onSetup: (String) -> Unit,
    onScriptAction: (LibraryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxHeight().padding(AppSpacing.xl)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineLarge, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
            SecondaryButton(
                stringResource(if (importInProgress) R.string.import_in_progress else R.string.import_file),
                { onImportFile(selectedFolder.takeUnless { it == "uncategorized" }) },
                enabled = !importInProgress,
            ) {
                if (importInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = AppColors.TextSecondary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.FileOpen, stringResource(R.string.import_file))
                }
            }
            Spacer(Modifier.width(AppSpacing.sm))
            PrimaryButton(stringResource(R.string.new_script), { onNewScript(selectedFolder.takeUnless { it == "uncategorized" }) }) { Icon(Icons.Default.Add, null) }        }
        Spacer(Modifier.height(AppSpacing.lg))
        LibraryGrid(scripts, folders, loading, onEdit, onSetup, onScriptAction, Modifier.weight(1f))
    }
}

@Composable
private fun LibraryBottomActions(
    importInProgress: Boolean,
    onImportFile: (String?) -> Unit,
    onNewScript: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.navigationBarsPadding(),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.Border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            SecondaryButton(
                stringResource(if (importInProgress) R.string.import_in_progress else R.string.import_file),
                { onImportFile(null) },
                Modifier.weight(1f),
                enabled = !importInProgress,
            ) {
                if (importInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = AppColors.TextSecondary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.FileOpen, stringResource(R.string.import_file))
                }
            }
            PrimaryButton(stringResource(R.string.new_script), onNewScript, Modifier.weight(1f)) { Icon(Icons.Default.Add, null) }
        }
    }
}

@Composable
private fun scriptImportErrorText(error: ScriptImportError): String = when (error) {
    ScriptImportError.UnsupportedType -> stringResource(R.string.import_unsupported_type)
    ScriptImportError.Empty -> stringResource(R.string.import_empty)
    ScriptImportError.TooLarge -> stringResource(R.string.import_too_large)
    ScriptImportError.Unreadable -> stringResource(R.string.import_unreadable)
    ScriptImportError.UnrecognizedEncoding -> stringResource(R.string.import_unrecognized_encoding)
    ScriptImportError.Corrupt -> stringResource(R.string.import_corrupt)
    ScriptImportError.SaveFailed -> stringResource(R.string.import_save_failed)
    ScriptImportError.Cancelled -> stringResource(R.string.import_cancelled)
}

@Composable
private fun LibraryGrid(
    scripts: List<Script>,
    folders: List<ScriptFolder>,
    loading: Boolean,
    onEdit: (String) -> Unit,
    onSetup: (String) -> Unit,
    onScriptAction: (LibraryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(360.dp),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(AppSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        if (loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(AppSpacing.xxl), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        } else if (scripts.isEmpty()) {
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
        items(scripts, key = { it.id }) { script ->
            ScriptCard(
                script = script,
                folderName = folders.firstOrNull { it.id == script.folderId }?.name,
                onEdit = onEdit,
                onSetup = onSetup,
                onAction = onScriptAction,
            )
        }
    }
}

@Composable
private fun ScriptCard(
    script: Script,
    folderName: String?,
    onEdit: (String) -> Unit,
    onSetup: (String) -> Unit,
    onAction: (LibraryAction) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth().heightIn(min = AppSpacing.ScriptCardHeight)) {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    script.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                folderName?.let { folder ->
                    Surface(
                        modifier = Modifier.padding(start = AppSpacing.xs).widthIn(max = 120.dp),
                        shape = CircleShape,
                        color = AppColors.Secondary.copy(alpha = .35f),
                        border = BorderStroke(1.dp, AppColors.Border),
                    ) {
                        Text(
                            folder,
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = AppColors.TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions)) }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menuOpen = false; onAction(LibraryAction.RenameScript(script)) }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.move_script)) }, onClick = { menuOpen = false; onAction(LibraryAction.MoveScript(script)) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuOpen = false; onAction(LibraryAction.DeleteScript(script)) }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                    }
                }
            }
            Text(
                script.plainTextPreview,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                color = AppColors.TextSecondary,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(
                    stringResource(R.string.words_format, script.wordCount),
                    stringResource(R.string.estimated_duration_format, formatDuration(script.currentNormalEstimatedDurationSeconds())),
                    stringResource(R.string.modified_format, formatModifiedAt(script.lastModifiedAt)),
                ).joinToString("  ·  "),
                color = AppColors.TextWeak,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().height(42.dp),
            )
            HorizontalDivider(color = AppColors.Border)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                SecondaryButton(stringResource(R.string.edit), { onEdit(script.id) }, Modifier.weight(1f)) { Icon(Icons.Default.Edit, null) }
                PrimaryButton(stringResource(R.string.start_prompting), { onSetup(script.id) }, Modifier.weight(1.6f)) { Icon(Icons.Default.PlayArrow, null) }
            }
        }
    }
}
