package com.zhy20.teleprompter.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhy20.teleprompter.core.model.Script
import com.zhy20.teleprompter.core.model.ScriptFolder
import com.zhy20.teleprompter.data.importer.ImportFileMetadata
import com.zhy20.teleprompter.data.importer.ScriptImportCoordinator
import com.zhy20.teleprompter.data.importer.ScriptImportError
import com.zhy20.teleprompter.data.importer.ScriptImportException
import com.zhy20.teleprompter.data.importer.ScriptImportState
import com.zhy20.teleprompter.data.importer.UriFileMetadataReader
import com.zhy20.teleprompter.data.repository.DataOperationException
import com.zhy20.teleprompter.data.repository.EmptyFolderNameException
import com.zhy20.teleprompter.data.repository.FolderNameConflictException
import com.zhy20.teleprompter.data.repository.ScriptFolderRepository
import com.zhy20.teleprompter.data.repository.ScriptRepository
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryError { ReadFailed, OperationFailed, EmptyFolderName, FolderNameConflict }

sealed interface LibraryLoadState {
    data object Loading : LibraryLoadState
    data object Empty : LibraryLoadState
    data object Content : LibraryLoadState
    data object Error : LibraryLoadState
}

data class LibraryUiState(
    val loadState: LibraryLoadState = LibraryLoadState.Loading,
    val scripts: List<Script> = emptyList(),
    val folders: List<ScriptFolder> = emptyList(),
    val operationError: LibraryError? = null,
)

class LibraryViewModel(
    private val scriptRepository: ScriptRepository,
    private val folderRepository: ScriptFolderRepository,
    private val importCoordinator: ScriptImportCoordinator? = null,
    private val uriFileMetadataReader: UriFileMetadataReader? = null,
    private val importDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val operationError = MutableStateFlow<LibraryError?>(null)
    private val _importState = MutableStateFlow<ScriptImportState>(ScriptImportState.Idle)

    /** Import progress/result; the screen observes it for loading feedback and error Snackbars. */
    val importState: StateFlow<ScriptImportState> = _importState

    val uiState: StateFlow<LibraryUiState> = combine(
        scriptRepository.observeAll(),
        folderRepository.observeAll(),
        operationError,
    ) { scripts, folders, error ->
        LibraryUiState(
            loadState = if (scripts.isEmpty() && folders.isEmpty()) LibraryLoadState.Empty else LibraryLoadState.Content,
            scripts = scripts,
            folders = folders,
            operationError = error,
        )
    }.catch {
        emit(LibraryUiState(loadState = LibraryLoadState.Error, operationError = LibraryError.ReadFailed))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    /**
     * Entry point used by the system file picker result. The Composable only passes the [Uri];
     * metadata reading, stream opening and persistence all happen off the main thread here.
     */
    fun importUri(uri: Uri, folderId: String?, onSuccess: (String) -> Unit) {
        val reader = uriFileMetadataReader ?: return
        runImport(
            metadataProvider = { reader.readMetadata(uri) },
            inputStreamProvider = {
                reader.openInputStream(uri)
                    ?: throw ScriptImportException(ScriptImportError.Unreadable)
            },
            folderId = folderId,
            onSuccess = onSuccess,
        )
    }

    /**
     * Pure-JVM entry point kept for unit tests: metadata and stream are supplied by the caller so
     * no Android dependency is required to exercise the whole import flow.
     */
    fun startImport(
        metadata: ImportFileMetadata,
        inputStreamProvider: () -> InputStream,
        folderId: String?,
        onSuccess: (String) -> Unit,
    ) = runImport(
        metadataProvider = { metadata },
        inputStreamProvider = inputStreamProvider,
        folderId = folderId,
        onSuccess = onSuccess,
    )

    private fun runImport(
        metadataProvider: () -> ImportFileMetadata,
        inputStreamProvider: () -> InputStream,
        folderId: String?,
        onSuccess: (String) -> Unit,
    ) {
        if (_importState.value != ScriptImportState.Idle) return
        val coordinator = importCoordinator ?: return
        // Set synchronously before launching so the double-submit guard also holds between the
        // synchronous return of this method and the coroutine's first execution step.
        _importState.value = ScriptImportState.Reading
        viewModelScope.launch {
            val newId = try {
                withContext(importDispatcher) {
                    val metadata = metadataProvider()
                    coordinator.importFile(metadata, inputStreamProvider, folderId)
                }
            } catch (error: ScriptImportException) {
                if (error.error != ScriptImportError.Cancelled) {
                    _importState.value = ScriptImportState.Error(error.error)
                } else {
                    _importState.value = ScriptImportState.Idle
                }
                null
            } catch (error: Exception) {
                _importState.value = ScriptImportState.Error(ScriptImportError.Unreadable)
                null
            }
            if (newId != null) {
                _importState.value = ScriptImportState.Success(newId)
                onSuccess(newId)
                _importState.value = ScriptImportState.Idle
            }
        }
    }

    fun clearImportError() {
        if (_importState.value is ScriptImportState.Error) _importState.value = ScriptImportState.Idle
    }

    fun createScript(folderId: String?, onCreated: (String) -> Unit) = launchOperation {
        onCreated(scriptRepository.create(folderId).id)
    }

    fun createFolder(name: String) = launchOperation { folderRepository.create(name) }
    fun renameFolder(id: String, name: String) = launchOperation { folderRepository.rename(id, name) }
    fun deleteFolder(id: String) = launchOperation { folderRepository.deleteAndUncategorizeScripts(id) }
    fun renameScript(id: String, title: String) = launchOperation { scriptRepository.updateTitle(id, title) }
    fun moveScript(id: String, folderId: String?) = launchOperation { scriptRepository.move(id, folderId) }
    fun deleteScript(id: String) = launchOperation { scriptRepository.delete(id) }
    fun clearError() { operationError.value = null }

    private fun launchOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            operationError.value = null
            runCatching { block() }.onFailure { error ->
                operationError.value = when (error) {
                    is EmptyFolderNameException -> LibraryError.EmptyFolderName
                    is FolderNameConflictException -> LibraryError.FolderNameConflict
                    is DataOperationException -> LibraryError.OperationFailed
                    else -> LibraryError.OperationFailed
                }
            }
        }
    }
}
