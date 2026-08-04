package com.zhy20.teleprompter.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhy20.teleprompter.core.design.AppTheme
import com.zhy20.teleprompter.core.model.PrompterSurface
import com.zhy20.teleprompter.core.model.CountdownOption
import com.zhy20.teleprompter.core.model.GuideMode
import com.zhy20.teleprompter.core.model.PlaybackOrientation
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.PlaybackState
import com.zhy20.teleprompter.core.model.PlaybackTextAlignment
import com.zhy20.teleprompter.core.model.RhythmMode
import com.zhy20.teleprompter.core.navigation.AppRoutes
import com.zhy20.teleprompter.core.util.PlaybackEngineState
import com.zhy20.teleprompter.feature.editor.EditorScreen
import com.zhy20.teleprompter.feature.editor.EditorViewModel
import com.zhy20.teleprompter.feature.editor.PersistentEditorScreen
import com.zhy20.teleprompter.feature.library.LibraryScreen
import com.zhy20.teleprompter.feature.library.LibraryViewModel
import com.zhy20.teleprompter.feature.prompter.PrompterScreen
import com.zhy20.teleprompter.feature.remote.RemoteScreen
import com.zhy20.teleprompter.feature.settings.LanguageScreen
import com.zhy20.teleprompter.feature.settings.SettingsScreen
import com.zhy20.teleprompter.feature.settings.SettingsViewModel
import com.zhy20.teleprompter.feature.setup.SetupScreen
import com.zhy20.teleprompter.feature.setup.SetupViewModel
import com.zhy20.teleprompter.feature.setup.PersistentSetupScreen
import java.util.Locale

@Composable
fun rememberAppState(): AppState = rememberSaveable(saver = AppStateSaver) { AppState() }

private val AppStateSaver = Saver<AppState, Bundle>(
    save = { state ->
        Bundle().apply {
            putString("scriptId", state.selectedScriptId)
            putString("background", state.playbackSettings.backgroundColor)
            putString("text", state.playbackSettings.textColor)
            putInt("fontSize", state.playbackSettings.fontSize)
            putString("orientation", state.playbackSettings.orientation.name)
            putString("alignment", state.playbackSettings.textAlignment.name)
            putBoolean("mirror", state.playbackSettings.mirrorEnabled)
            putString("rhythm", state.playbackSettings.rhythmMode.name)
            putFloat("speed", state.playbackSettings.speedMultiplier)
            putInt("target", state.playbackSettings.targetDurationSeconds)
            putString("countdown", state.playbackSettings.countdown.name)
            putString("guideMode", state.playbackSettings.guideMode.name)
            putFloat("guidePosition", state.playbackSettings.guideLinePosition)
            putString("preset", state.playbackSettings.displayPresetId)
            putString("playbackState", state.playbackState.stateKey())
            putInt("countdownRemaining", (state.playbackState as? PlaybackState.Countdown)?.secondsRemaining ?: 0)
            putLong("elapsed", state.playbackSession.elapsedTimeMillis)
            putFloat("progress", state.progress)
            putBoolean("startingFromBeginning", state.playbackSession.isStartingFromBeginning)
        }
    },
    restore = { bundle ->
        val settings = PlaybackSettings(
            backgroundColor = bundle.getString("background") ?: "#121719",
            textColor = bundle.getString("text") ?: "#F5F7FA",
            fontSize = bundle.getInt("fontSize", 64),
            orientation = bundle.enumOrDefault("orientation", PlaybackOrientation.Landscape),
            textAlignment = bundle.enumOrDefault("alignment", PlaybackTextAlignment.Start),
            mirrorEnabled = bundle.getBoolean("mirror", false),
            rhythmMode = bundle.enumOrDefault("rhythm", RhythmMode.Speed),
            speedMultiplier = bundle.getFloat("speed", 1f),
            targetDurationSeconds = bundle.getInt("target", 200),
            countdown = bundle.enumOrDefault("countdown", CountdownOption.ThreeSeconds),
            guideMode = bundle.enumOrDefault("guideMode", GuideMode.HighlightBar),
            guideLinePosition = bundle.getFloat("guidePosition", .25f),
            displayPresetId = bundle.getString("preset"),
        )
        val playbackState = bundle.playbackState()
        val restoredSession = PlaybackEngineState(
            playbackState = playbackState,
            elapsedTimeMillis = bundle.getLong("elapsed", 0L).coerceAtLeast(0L),
            currentSemanticProgress = bundle.getFloat("progress", 0f).coerceIn(0f, 1f),
            isStartingFromBeginning = bundle.getBoolean("startingFromBeginning", false),
            currentSpeedMultiplier = settings.speedMultiplier,
            configuredTargetDurationMillis = settings.targetDurationSeconds.coerceAtLeast(1) * 1_000L,
            actualScrollDurationMillis = settings.targetDurationSeconds.coerceAtLeast(1) * 1_000L,
        )
        AppState().apply {
            restorePlaybackSession(bundle.getString("scriptId") ?: "1", settings, restoredSession)
        }
    },
)

private inline fun <reified T : Enum<T>> Bundle.enumOrDefault(key: String, fallback: T): T =
    getString(key)?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: fallback

private fun PlaybackState.stateKey(): String = when (this) {
    PlaybackState.Idle -> "idle"
    PlaybackState.Preparing -> "preparing"
    is PlaybackState.Countdown -> "countdown"
    PlaybackState.Playing -> "playing"
    PlaybackState.Paused -> "paused"
    PlaybackState.Finished -> "finished"
    PlaybackState.Exited -> "exited"
}

private fun Bundle.playbackState(): PlaybackState = when (getString("playbackState")) {
    "preparing" -> PlaybackState.Preparing
    "countdown" -> PlaybackState.Countdown(getInt("countdownRemaining", 1).coerceAtLeast(1))
    "playing" -> PlaybackState.Playing
    "paused" -> PlaybackState.Paused
    "finished" -> PlaybackState.Finished
    "exited" -> PlaybackState.Exited
    else -> PlaybackState.Idle
}

@Composable
fun TeleprompterApp(appState: AppState = rememberAppState()) {
    val baseContext = LocalContext.current
    val container = (baseContext.applicationContext as TeleprompterApplication).container
    val settingsFactory = remember(container) {
        viewModelFactory { initializer { SettingsViewModel(container.settingsRepository) } }
    }
    val settingsViewModel: SettingsViewModel = viewModel(factory = settingsFactory)
    val persistedSettings by settingsViewModel.settings.collectAsStateWithLifecycle()
    LaunchedEffect(persistedSettings) {
        appState.globalDefaults = persistedSettings.playbackDefaults
        appState.selectedLanguage = persistedSettings.languageTag
    }
    val baseConfiguration = LocalConfiguration.current
    val localizedConfiguration = remember(baseConfiguration, appState.selectedLanguage) {
        Configuration(baseConfiguration).apply {
            setLocale(Locale.forLanguageTag(appState.selectedLanguage))
        }
    }
    val localizedContext = remember(baseContext, localizedConfiguration) {
        baseContext.createConfigurationContext(localizedConfiguration)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
    ) {
        AppTheme {
        val navController = rememberNavController()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NavHost(navController = navController, startDestination = AppRoutes.Library) {
            composable(AppRoutes.Library) { entry ->
                val factory = remember(container) {
                    viewModelFactory {
                        initializer { LibraryViewModel(container.scriptRepository, container.folderRepository) }
                    }
                }
                val libraryViewModel: LibraryViewModel = viewModel(viewModelStoreOwner = entry, factory = factory)
                val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(libraryState.scripts, libraryState.folders) {
                    appState.setLibraryData(libraryState.scripts, libraryState.folders)
                }
                LaunchedEffect(Unit) { appState.setSurface(PrompterSurface.Library) }
                LibraryScreen(
                    appState = appState,
                    onNewScript = { folderId ->
                        libraryViewModel.createScript(folderId) { id -> navController.navigate(AppRoutes.editor(id)) }
                    },
                    onEdit = { navController.navigate(AppRoutes.editor(it)) },
                    onSetup = { id -> appState.selectScript(id); navController.navigate(AppRoutes.setup(id)) },
                    onRemote = { navController.navigate(AppRoutes.Remote) },
                    onSettings = { navController.navigate(AppRoutes.Settings) },
                    uiState = libraryState,
                    onCreateFolder = libraryViewModel::createFolder,
                    onRenameFolder = libraryViewModel::renameFolder,
                    onDeleteFolder = libraryViewModel::deleteFolder,
                    onRenameScript = libraryViewModel::renameScript,
                    onMoveScript = libraryViewModel::moveScript,
                    onDeleteScript = libraryViewModel::deleteScript,
                    onClearError = libraryViewModel::clearError,
                )
            }
            composable(AppRoutes.Editor) { entry ->
                val factory = remember(entry, container) {
                    viewModelFactory {
                        initializer { EditorViewModel(createSavedStateHandle(), container.scriptRepository) }
                    }
                }
                val editorViewModel: EditorViewModel = viewModel(viewModelStoreOwner = entry, factory = factory)
                LaunchedEffect(Unit) { appState.setSurface(PrompterSurface.Editor) }
                PersistentEditorScreen(
                    viewModel = editorViewModel,
                    onBack = { navController.popBackStack() },
                    onSetup = { scriptId -> navController.navigate(AppRoutes.setup(scriptId)) },
                )
            }
            composable(AppRoutes.Setup) { entry ->
                val factory = remember(entry, container) {
                    viewModelFactory {
                        initializer { SetupViewModel(createSavedStateHandle(), container.scriptRepository) }
                    }
                }
                val setupViewModel: SetupViewModel = viewModel(viewModelStoreOwner = entry, factory = factory)
                LaunchedEffect(Unit) { appState.setSurface(PrompterSurface.Setup) }
                PersistentSetupScreen(
                    viewModel = setupViewModel,
                    appState = appState,
                    onBack = { navController.popBackStack() },
                    onRemote = { navController.navigate(AppRoutes.Remote) },
                    onStart = { scriptId -> navController.navigate(AppRoutes.prompter(scriptId)) },
                )
            }
            composable(AppRoutes.Prompter) { entry ->
                val id = entry.arguments?.getString("scriptId") ?: appState.selectedScriptId
                val factory = remember(entry, container) {
                    viewModelFactory {
                        initializer { SetupViewModel(createSavedStateHandle(), container.scriptRepository) }
                    }
                }
                val scriptViewModel: SetupViewModel = viewModel(viewModelStoreOwner = entry, factory = factory)
                val scriptState by scriptViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(id) { appState.setSurface(PrompterSurface.Prompter) }
                LaunchedEffect(scriptState.script) { scriptState.script?.let(appState::setActiveScript) }
                if (scriptState.script != null) {
                    PrompterScreen(
                        id,
                        appState,
                        onExit = { navController.popBackStack() },
                        scriptOverride = scriptState.script,
                    )
                } else if (!scriptState.isLoading) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        androidx.compose.material3.Text(stringResource(com.zhy20.teleprompter.R.string.script_not_found))
                    }
                }
            }
            composable(AppRoutes.Remote) { RemoteScreen(appState, onBack = { navController.popBackStack() }) }
            composable(AppRoutes.Settings) {
                SettingsScreen(
                    appState,
                    onBack = { navController.popBackStack() },
                    onLanguage = { navController.navigate(AppRoutes.Language) },
                    defaultsOverride = persistedSettings.playbackDefaults,
                    languageOverride = persistedSettings.languageTag,
                    onDefaultsChange = settingsViewModel::updateDefaults,
                )
            }
                composable(AppRoutes.Language) {
                    LanguageScreen(
                        appState,
                        onBack = { navController.popBackStack() },
                        languageOverride = persistedSettings.languageTag,
                        onLanguageChange = settingsViewModel::updateLanguage,
                    )
                }
            }
        }
    }
    }
}
