package com.zhy20.teleprompter.feature.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.app.AppState
import com.zhy20.teleprompter.app.RemoteStartPlaybackHandler
import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus

@Composable
fun PersistentSetupScreen(
    viewModel: SetupViewModel,
    appState: AppState,
    remoteConnectionStatus: RemoteConnectionStatus = RemoteConnectionStatus.Disabled,
    onBack: () -> Unit,
    onStart: (String) -> Unit,
    startPlaybackHandler: RemoteStartPlaybackHandler? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.flush() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flush()
        }
    }
    LaunchedEffect(state.script, state.settings) {
        state.script?.let { script -> appState.setActiveScript(script.copy(playbackSettings = state.settings)) }
    }

    // While this Setup page is visible, service controller-issued start-playback requests:
    // flush settings, and only on success navigate; then complete the request with the result.
    LaunchedEffect(startPlaybackHandler, state.script?.id) {
        val scriptId = state.script?.id
        if (startPlaybackHandler == null || scriptId == null) return@LaunchedEffect
        while (true) {
            val request = startPlaybackHandler.awaitRequest()
            if (request.scriptId != scriptId) {
                startPlaybackHandler.complete(request, false)
                continue
            }
            val saved = viewModel.flushNow()
            if (saved) {
                val latest = viewModel.uiState.value
                appState.setActiveScript(latest.script!!.copy(playbackSettings = latest.settings))
                appState.beginPlayback(scriptId)
                onStart(scriptId)
            }
            startPlaybackHandler.complete(request, saved)
        }
    }

    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.script == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(if (state.error == SetupError.ScriptNotFound) R.string.script_not_found else R.string.load_failed))
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
        }
        else -> SetupScreen(
            scriptId = state.script!!.id,
            appState = appState,
            onBack = { viewModel.flush { saved -> if (saved) onBack() } },
            onStart = { id ->
                viewModel.flush { saved ->
                    if (saved) {
                        appState.setActiveScript(state.script!!.copy(playbackSettings = state.settings))
                        appState.beginPlayback(id)
                        onStart(id)
                    }
                }
            },
            scriptOverride = state.script,
            settingsOverride = state.settings,
            onSettingsChange = viewModel::updateSettings,
            remoteConnectionStatus = remoteConnectionStatus,
        )
    }
}
