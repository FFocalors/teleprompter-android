package com.zhy20.teleprompter.app

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zhy20.teleprompter.core.design.AppTheme
import com.zhy20.teleprompter.core.model.PrompterSurface
import com.zhy20.teleprompter.core.navigation.AppRoutes
import com.zhy20.teleprompter.feature.editor.EditorScreen
import com.zhy20.teleprompter.feature.library.LibraryScreen
import com.zhy20.teleprompter.feature.prompter.PrompterScreen
import com.zhy20.teleprompter.feature.remote.RemoteScreen
import com.zhy20.teleprompter.feature.settings.LanguageScreen
import com.zhy20.teleprompter.feature.settings.SettingsScreen
import com.zhy20.teleprompter.feature.setup.SetupScreen
import java.util.Locale

@Composable
fun rememberAppState(): AppState = remember { AppState() }

@Composable
fun TeleprompterApp(appState: AppState = rememberAppState()) {
    val baseContext = LocalContext.current
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
            composable(AppRoutes.Library) {
                LaunchedEffect(Unit) { appState.setSurface(PrompterSurface.Library) }
                LibraryScreen(
                    appState = appState,
                    onNewScript = { navController.navigate(AppRoutes.editor("new")) },
                    onEdit = { navController.navigate(AppRoutes.editor(it)) },
                    onSetup = { id -> appState.selectScript(id); navController.navigate(AppRoutes.setup(id)) },
                    onRemote = { navController.navigate(AppRoutes.Remote) },
                    onSettings = { navController.navigate(AppRoutes.Settings) },
                )
            }
            composable(AppRoutes.Editor) { entry ->
                val id = entry.arguments?.getString("scriptId") ?: "new"
                LaunchedEffect(id) { appState.setSurface(PrompterSurface.Editor); appState.selectScript(id) }
                EditorScreen(id, appState, onBack = { navController.popBackStack() }, onSetup = { scriptId -> navController.navigate(AppRoutes.setup(scriptId)) })
            }
            composable(AppRoutes.Setup) { entry ->
                val id = entry.arguments?.getString("scriptId") ?: appState.selectedScriptId
                LaunchedEffect(id) { appState.setSurface(PrompterSurface.Setup); appState.selectScript(id) }
                SetupScreen(
                    scriptId = id,
                    appState = appState,
                    onBack = { navController.popBackStack() },
                    onRemote = { navController.navigate(AppRoutes.Remote) },
                    onStart = { scriptId -> appState.beginPlayback(scriptId); navController.navigate(AppRoutes.prompter(scriptId)) },
                )
            }
            composable(AppRoutes.Prompter) { entry ->
                val id = entry.arguments?.getString("scriptId") ?: appState.selectedScriptId
                LaunchedEffect(id) { appState.setSurface(PrompterSurface.Prompter) }
                PrompterScreen(id, appState) {
                    navController.popBackStack()
                }
            }
            composable(AppRoutes.Remote) { RemoteScreen(appState, onBack = { navController.popBackStack() }) }
            composable(AppRoutes.Settings) { SettingsScreen(appState, onBack = { navController.popBackStack() }, onLanguage = { navController.navigate(AppRoutes.Language) }) }
                composable(AppRoutes.Language) { LanguageScreen(appState, onBack = { navController.popBackStack() }) }
            }
        }
    }
    }
}
