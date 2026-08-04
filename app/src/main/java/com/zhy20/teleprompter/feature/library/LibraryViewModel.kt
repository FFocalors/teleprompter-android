package com.zhy20.teleprompter.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhy20.teleprompter.core.model.Script
import com.zhy20.teleprompter.core.model.ScriptFolder
import com.zhy20.teleprompter.data.repository.DataOperationException
import com.zhy20.teleprompter.data.repository.EmptyFolderNameException
import com.zhy20.teleprompter.data.repository.FolderNameConflictException
import com.zhy20.teleprompter.data.repository.ScriptFolderRepository
import com.zhy20.teleprompter.data.repository.ScriptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
) : ViewModel() {
    private val operationError = MutableStateFlow<LibraryError?>(null)

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
