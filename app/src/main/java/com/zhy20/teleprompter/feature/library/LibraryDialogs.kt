package com.zhy20.teleprompter.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.core.design.AppSpacing
import com.zhy20.teleprompter.core.model.Script
import com.zhy20.teleprompter.core.model.ScriptFolder

internal sealed interface LibraryAction {
    data object NewFolder : LibraryAction
    data class RenameFolder(val folder: ScriptFolder) : LibraryAction
    data class DeleteFolder(val folder: ScriptFolder) : LibraryAction
    data class RenameScript(val script: Script) : LibraryAction
    data class MoveScript(val script: Script) : LibraryAction
    data class DeleteScript(val script: Script) : LibraryAction
}

@Composable
internal fun libraryErrorText(error: LibraryError): String = when (error) {
    LibraryError.ReadFailed -> stringResource(R.string.library_read_failed)
    LibraryError.OperationFailed -> stringResource(R.string.library_operation_failed)
    LibraryError.EmptyFolderName -> stringResource(R.string.folder_name_empty)
    LibraryError.FolderNameConflict -> stringResource(R.string.folder_name_conflict)
}

@Composable
internal fun LibraryActionDialog(
    action: LibraryAction?,
    folders: List<ScriptFolder>,
    onDismiss: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onRenameScript: (String, String) -> Unit,
    onMoveScript: (String, String?) -> Unit,
    onDeleteScript: (String) -> Unit,
) {
    when (val current = action) {
        null -> Unit
        LibraryAction.NewFolder -> NameDialog(
            title = stringResource(R.string.new_folder),
            initialValue = "",
            label = stringResource(R.string.folder_name),
            confirmLabel = stringResource(R.string.create),
            onDismiss = onDismiss,
        ) { onCreateFolder(it); onDismiss() }
        is LibraryAction.RenameFolder -> NameDialog(
            title = stringResource(R.string.rename_folder),
            initialValue = current.folder.name,
            label = stringResource(R.string.folder_name),
            confirmLabel = stringResource(R.string.rename),
            onDismiss = onDismiss,
        ) { onRenameFolder(current.folder.id, it); onDismiss() }
        is LibraryAction.RenameScript -> NameDialog(
            title = stringResource(R.string.rename_script),
            initialValue = current.script.title,
            label = stringResource(R.string.script_title_hint),
            confirmLabel = stringResource(R.string.rename),
            onDismiss = onDismiss,
        ) { onRenameScript(current.script.id, it); onDismiss() }
        is LibraryAction.DeleteScript -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.delete_script)) },
            text = { Text(stringResource(R.string.delete_script_confirm, current.script.title)) },
            confirmButton = { TextButton(onClick = { onDeleteScript(current.script.id); onDismiss() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        is LibraryAction.DeleteFolder -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.delete_folder)) },
            text = { Text(stringResource(R.string.delete_folder_confirm, current.folder.name)) },
            confirmButton = { TextButton(onClick = { onDeleteFolder(current.folder.id); onDismiss() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        is LibraryAction.MoveScript -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.move_script)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    MoveTarget(stringResource(R.string.uncategorized), current.script.folderId == null) {
                        onMoveScript(current.script.id, null); onDismiss()
                    }
                    folders.forEach { folder ->
                        MoveTarget(folder.name, current.script.folderId == folder.id) {
                            onMoveScript(current.script.id, folder.id); onDismiss()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initialValue: String,
    label: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(title, initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MoveTarget(label: String, current: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = !current, modifier = Modifier.fillMaxWidth()) {
        Text(if (current) stringResource(R.string.current_folder_format, label) else label)
    }
}
