package com.zhy20.teleprompter.feature.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus
import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.protocol.RemoteCommand
import com.zhy20.teleprompter.remote.session.RemoteSessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pure UI state the remote screen renders. */
data class RemoteUiState(
    val status: RemoteConnectionStatus = RemoteConnectionStatus.Disabled,
    val snapshot: RemotePrompterSnapshot? = null,
    val commandInFlight: Boolean = false,
)

/** Actions the remote screen emits; the ViewModel turns them into commands/effects. */
sealed interface RemoteUiAction {
    data object StartWaiting : RemoteUiAction
    data object CancelWaiting : RemoteUiAction
    data object RetryConnection : RemoteUiAction
    data object StartPlayback : RemoteUiAction
    data object Pause : RemoteUiAction
    data object ResumeImmediately : RemoteUiAction
    data object ResumeWithCountdown : RemoteUiAction
    data object SeekBackward : RemoteUiAction
    data object SeekForward : RemoteUiAction
    data object DecreaseSpeed : RemoteUiAction
    data object IncreaseSpeed : RemoteUiAction
    data object EndPlayback : RemoteUiAction
}

/**
 * Bridges the remote screen to [RemoteSessionRepository]. It never touches [AppState], never
 * talks to the transport directly, and only maps UI actions into commands or lifecycle calls.
 */
class RemoteViewModel(
    private val repository: RemoteSessionRepository,
) : ViewModel() {

    private var commandCounter = 0L

    val uiState: StateFlow<RemoteUiState> = repository.sessionState
        .map { state ->
            RemoteUiState(
                status = state.status,
                snapshot = state.snapshot,
                commandInFlight = state.commandInFlight,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RemoteUiState(),
        )

    fun handle(action: RemoteUiAction) {
        when (action) {
            RemoteUiAction.StartWaiting,
            RemoteUiAction.RetryConnection,
            -> viewModelScope.launch { repository.startWaiting() }

            RemoteUiAction.CancelWaiting -> viewModelScope.launch { repository.stopWaiting() }

            RemoteUiAction.StartPlayback -> sendCommand(
                RemoteCommand.StartPlayback(
                    commandId = nextCommandId(),
                    scriptId = snapshotScriptId(),
                ),
            )
            RemoteUiAction.Pause -> sendCommand(RemoteCommand.PausePlayback(nextCommandId()))
            RemoteUiAction.ResumeImmediately -> sendCommand(RemoteCommand.ResumeImmediately(nextCommandId()))
            RemoteUiAction.ResumeWithCountdown -> sendCommand(RemoteCommand.ResumeWithCountdown(nextCommandId()))
            RemoteUiAction.SeekBackward -> sendCommand(RemoteCommand.SeekBy(nextCommandId(), delta = -0.03f))
            RemoteUiAction.SeekForward -> sendCommand(RemoteCommand.SeekBy(nextCommandId(), delta = 0.03f))
            RemoteUiAction.DecreaseSpeed -> sendCommand(RemoteCommand.ChangeSpeed(nextCommandId(), delta = -0.1f))
            RemoteUiAction.IncreaseSpeed -> sendCommand(RemoteCommand.ChangeSpeed(nextCommandId(), delta = 0.1f))
            RemoteUiAction.EndPlayback -> sendCommand(RemoteCommand.EndPlayback(nextCommandId()))
        }
    }

    private fun sendCommand(command: RemoteCommand) {
        viewModelScope.launch { repository.sendCommand(command) }
    }

    private fun snapshotScriptId(): String {
        val snapshot = uiState.value.snapshot
        if (snapshot?.scriptId != null && snapshot.scriptId.isNotBlank()) return snapshot.scriptId
        return "1"
    }

    private fun nextCommandId(): String {
        commandCounter += 1
        return "remote-ui-$commandCounter"
    }
}
