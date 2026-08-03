package com.zhy20.teleprompter.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.zhy20.teleprompter.app.AppState
import com.zhy20.teleprompter.core.design.AppTheme
import com.zhy20.teleprompter.core.model.PlaybackState
import com.zhy20.teleprompter.core.model.PlaybackOrientation
import com.zhy20.teleprompter.core.model.PlaybackTextAlignment
import com.zhy20.teleprompter.core.model.PrompterSurface
import com.zhy20.teleprompter.core.model.RemoteConnectionState
import com.zhy20.teleprompter.core.model.DisplayPresets
import com.zhy20.teleprompter.core.model.GuideLineStyle
import com.zhy20.teleprompter.core.model.applyDisplayPreset
import com.zhy20.teleprompter.core.model.SaveState
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

@Preview(name = "冷灰主题编辑页", widthDp = 900, heightDp = 720, showBackground = true)
@Composable private fun EditorPreview() = PreviewState { EditorScreen("1", it, {}, {}) }

@Preview(name = "编辑页短文本留白", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun ShortEditorPreview() = PreviewState { EditorScreen("3", it, {}, {}) }

@Preview(name = "保存图标初始状态", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun InitialSavePreview() = PreviewState { EditorScreen("1", it, {}, {}, previewSaveState = SaveState.Initial) }

@Preview(name = "保存图标已保存状态", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun SavedIconPreview() = PreviewState { EditorScreen("1", it, {}, {}, previewSaveState = SaveState.Saved) }

@Preview(name = "保存图标错误状态", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun ErrorSavePreview() = PreviewState { EditorScreen("1", it, {}, {}, previewSaveState = SaveState.Error) }

@Preview(name = "提词设置：黑底白字", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun DarkSetupPreview() = PreviewState { SetupScreen("1", it, {}, {}, {}) }

@Preview(name = "提词设置：白底黑字", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun LightSetupPreview() = PreviewState(
    initialize = { playbackSettings = playbackSettings.applyDisplayPreset(DisplayPresets.WhiteOnBlack) },
) { SetupScreen("1", it, {}, {}, {}) }

@Preview(name = "提词设置：深蓝底白字", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable private fun BlueSetupPreview() = PreviewState(
    initialize = { playbackSettings = playbackSettings.applyDisplayPreset(DisplayPresets.BlueOnWhite) },
) { SetupScreen("1", it, {}, {}, {}) }

@Preview(name = "提词设置：深绿底白字", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable private fun GreenSetupPreview() = PreviewState(
    initialize = { playbackSettings = playbackSettings.applyDisplayPreset(DisplayPresets.GreenOnWhite) },
) { SetupScreen("1", it, {}, {}, {}) }

@Preview(name = "预览：左对齐", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable private fun StartAlignedSetupPreview() = PreviewState(
    initialize = { playbackSettings = playbackSettings.copy(textAlignment = PlaybackTextAlignment.Start) },
) { SetupScreen("1", it, {}, {}, {}) }

@Preview(name = "预览：居中对齐", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable private fun CenterAlignedSetupPreview() = PreviewState(
    initialize = { playbackSettings = playbackSettings.copy(textAlignment = PlaybackTextAlignment.Center) },
) { SetupScreen("1", it, {}, {}, {}) }

@Preview(name = "预览：右对齐", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable private fun EndAlignedSetupPreview() = PreviewState(
    initialize = { playbackSettings = playbackSettings.copy(textAlignment = PlaybackTextAlignment.End) },
) { SetupScreen("1", it, {}, {}, {}) }

@Preview(name = "横屏播放", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable private fun LandscapePlayingPreview() = PreviewState(
    initialize = {
        playbackState = PlaybackState.Playing
        playbackSettings = playbackSettings.copy(orientation = PlaybackOrientation.Landscape)
    },
) { PrompterScreen("1", it, {}) }

@Preview(name = "竖屏播放", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun PortraitPlayingPreview() = PreviewState(
    initialize = {
        playbackState = PlaybackState.Playing
        playbackSettings = playbackSettings.copy(orientation = PlaybackOrientation.Portrait)
    },
) { PrompterScreen("1", it, {}) }

@Preview(name = "黑底白字红色横线", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable private fun DarkLinePrompterPreview() = PreviewState(
    initialize = {
        playbackState = PlaybackState.Playing
        playbackSettings = playbackSettings.applyDisplayPreset(DisplayPresets.BlackOnWhite).copy(guideLineStyle = GuideLineStyle.Line)
    },
) { PrompterScreen("1", it, {}) }

@Preview(name = "白底黑字红色提示条", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable private fun LightHighlightPrompterPreview() = PreviewState(
    initialize = {
        playbackState = PlaybackState.Playing
        playbackSettings = playbackSettings.applyDisplayPreset(DisplayPresets.WhiteOnBlack).copy(guideLineStyle = GuideLineStyle.Highlight)
    },
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
