package com.zhy20.teleprompter.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.zhy20.teleprompter.app.AppState
import com.zhy20.teleprompter.core.design.AppTheme
import com.zhy20.teleprompter.core.model.PlaybackState
import com.zhy20.teleprompter.core.model.PrompterSurface
import com.zhy20.teleprompter.core.model.RemoteConnectionState
import com.zhy20.teleprompter.feature.editor.EditorScreen
import com.zhy20.teleprompter.feature.library.LibraryScreen
import com.zhy20.teleprompter.feature.prompter.PrompterScreen
import com.zhy20.teleprompter.feature.remote.RemoteScreen
import com.zhy20.teleprompter.feature.settings.SettingsScreen
import com.zhy20.teleprompter.feature.setup.SetupScreen

@Composable
private fun PreviewState(
    initialize: AppState.() -> Unit = {},
    content: @Composable (AppState) -> Unit,
) = AppTheme { content(remember { AppState().apply(initialize) }) }

@Preview(name = "手机首页", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun PhoneLibraryPreview() = PreviewState { LibraryScreen(it, {}, {}, {}, {}, {}) }

@Preview(name = "平板横屏首页", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable private fun TabletLibraryPreview() = PreviewState { LibraryScreen(it, {}, {}, {}, {}, {}) }

@Preview(name = "编辑页", widthDp = 900, heightDp = 720, showBackground = true)
@Composable private fun EditorPreview() = PreviewState { EditorScreen("1", it, {}, {}) }

@Preview(name = "手机样式页", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun PhoneSetupPreview() = PreviewState { SetupScreen("1", it, {}, {}, {}) }

@Preview(name = "平板样式页", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable private fun TabletSetupPreview() = PreviewState { SetupScreen("1", it, {}, {}, {}) }

@Preview(name = "播放中", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable private fun PlayingPreview() = PreviewState(
    initialize = { playbackState = PlaybackState.Playing },
) { PrompterScreen("1", it, {}) }

@Preview(name = "暂停状态", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable private fun PausedPreview() = PreviewState(
    initialize = { playbackState = PlaybackState.Paused },
) { PrompterScreen("1", it, {}) }

@Preview(name = "控制端播放中", widthDp = 480, heightDp = 900, showBackground = true)
@Composable private fun RemotePlayingPreview() = PreviewState(
    initialize = {
        remoteConnectionState = RemoteConnectionState.Connected
        setSurface(PrompterSurface.Prompter)
        playbackState = PlaybackState.Playing
    },
) { RemoteScreen(it, {}) }

@Preview(name = "设置页", widthDp = 900, heightDp = 800, showBackground = true)
@Composable private fun SettingsPreview() = PreviewState { SettingsScreen(it, {}, {}) }
