package com.zhy20.teleprompter.feature.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus
import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.model.RemoteRole
import com.zhy20.teleprompter.remote.pairing.RemotePairingPayload
import com.zhy20.teleprompter.remote.pairing.RemotePairingPayloadCodec
import com.zhy20.teleprompter.remote.protocol.RemoteCommand
import com.zhy20.teleprompter.remote.session.RemoteSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pure UI state the remote screen renders. */
data class RemoteUiState(
    val status: RemoteConnectionStatus = RemoteConnectionStatus.Disabled,
    val snapshot: RemotePrompterSnapshot? = null,
    val commandInFlight: Boolean = false,
    val role: RemoteRole? = null,
    val pairingPayload: RemotePairingPayload? = null,
    val reconnecting: Boolean = false,
    val lastCommandError: String? = null,
)

/** Structured error after a failed QR scan or camera permission denial; the UI maps to strings. */
enum class RemoteScanError {
    InvalidPairing,
    ExpiredPairing,
    CameraDenied,
}

/** Actions the remote screen emits; the ViewModel turns them into commands/effects. */
sealed interface RemoteUiAction {
    data object SelectPrompterRole : RemoteUiAction
    data object SelectControllerRole : RemoteUiAction
    data object StartWaiting : RemoteUiAction
    data object CancelWaiting : RemoteUiAction
    data object RetryConnection : RemoteUiAction
    data class ConnectToPrompter(val payload: RemotePairingPayload) : RemoteUiAction
    data class ConnectManual(val host: String, val port: Int, val sessionId: String, val token: String) : RemoteUiAction
    data object Disconnect : RemoteUiAction
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
 *
 * QR scanning results arrive here via [onScannedContents]/[onCameraDenied], which are fed by
 * the app-level launchers created above the NavHost (a NavHost destination cannot create its
 * own ActivityResult launchers).
 */
class RemoteViewModel(
    private val repository: RemoteSessionRepository,
) : ViewModel() {

    private var commandCounter = 0L

    private val _scanError = MutableStateFlow<RemoteScanError?>(null)
    val scanError: StateFlow<RemoteScanError?> = _scanError.asStateFlow()

    val uiState: StateFlow<RemoteUiState> = combine(
        repository.sessionState,
        repository.snapshot,
    ) { session, snap ->
        RemoteUiState(
            status = session.status,
            snapshot = snap,
            commandInFlight = session.commandInFlight,
            role = session.role,
            pairingPayload = session.pairingPayload,
            reconnecting = session.reconnecting,
            lastCommandError = session.lastCommandError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RemoteUiState(),
    )

    fun handle(action: RemoteUiAction) {
        when (action) {
            RemoteUiAction.SelectPrompterRole -> viewModelScope.launch { repository.prepare(RemoteRole.Prompter) }
            RemoteUiAction.SelectControllerRole -> viewModelScope.launch { repository.prepare(RemoteRole.Controller) }
            RemoteUiAction.StartWaiting -> viewModelScope.launch { repository.startWaiting() }
            RemoteUiAction.CancelWaiting -> viewModelScope.launch { repository.stopWaiting() }
            RemoteUiAction.RetryConnection -> viewModelScope.launch {
                when (uiState.value.role) {
                    RemoteRole.Prompter -> repository.startWaiting()
                    RemoteRole.Controller -> uiState.value.pairingPayload?.let { repository.connectToPrompter(it) }
                    null -> Unit
                }
            }
            is RemoteUiAction.ConnectToPrompter -> viewModelScope.launch { repository.connectToPrompter(action.payload) }
            is RemoteUiAction.ConnectManual -> viewModelScope.launch {
                repository.connectManual(action.host, action.port, action.sessionId, action.token)
            }
            RemoteUiAction.Disconnect -> viewModelScope.launch { repository.disconnect() }

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

    /** Called by the app-level scan launcher when a QR was scanned. */
    fun onScannedContents(contents: String) {
        val parsed = RemotePairingPayloadCodec.parse(contents).getOrNull()
        if (parsed == null) {
            _scanError.value = RemoteScanError.InvalidPairing
            return
        }
        if (RemotePairingPayloadCodec.validateExpiry(parsed, System.currentTimeMillis()).isFailure) {
            _scanError.value = RemoteScanError.ExpiredPairing
            return
        }
        _scanError.value = null
        viewModelScope.launch { repository.connectToPrompter(parsed) }
    }

    /** Called by the app-level camera-permission launcher when the user denied. */
    fun onCameraDenied() {
        _scanError.value = RemoteScanError.CameraDenied
    }

    fun dismissScanError() {
        _scanError.value = null
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
