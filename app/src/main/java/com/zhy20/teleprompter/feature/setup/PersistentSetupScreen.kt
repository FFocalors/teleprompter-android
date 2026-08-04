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

@Composable
fun PersistentSetupScreen(
    viewModel: SetupViewModel,
    appState: AppState,
    onBack: () -> Unit,
    onRemote: () -> Unit,
    onStart: (String) -> Unit,
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
            onRemote = onRemote,
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
        )
    }
}
