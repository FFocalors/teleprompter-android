package com.zhy20.teleprompter.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zhy20.teleprompter.core.model.CountdownOption
import com.zhy20.teleprompter.core.model.GuideLineStyle
import com.zhy20.teleprompter.core.model.PlaybackEvent
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.PlaybackState
import com.zhy20.teleprompter.core.model.PrompterSurface
import com.zhy20.teleprompter.core.model.RemoteConnectionState
import com.zhy20.teleprompter.core.model.SaveState
import com.zhy20.teleprompter.core.model.Script
import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptSpan
import com.zhy20.teleprompter.data.fake.FakeData

class AppState {
    var scripts by mutableStateOf(FakeData.scripts)
        private set
    val folders = FakeData.folders

    var selectedScriptId by mutableStateOf("1")
        private set
    var draftScript by mutableStateOf(FakeData.blankScript)
        private set
    var playbackSettings by mutableStateOf(FakeData.defaultPlaybackSettings)
    var globalDefaults by mutableStateOf(FakeData.defaultPlaybackSettings)
    var playbackState: PlaybackState by mutableStateOf(PlaybackState.Idle)
    var remoteConnectionState by mutableStateOf(RemoteConnectionState.Disconnected)
    var prompterSurface by mutableStateOf(PrompterSurface.Library)
    var progress by mutableFloatStateOf(0.38f)
    var saveState by mutableStateOf(SaveState.Saved)
    var selectedLanguage by mutableStateOf("zh-CN")

    fun script(id: String): Script = scripts.firstOrNull { it.id == id } ?: draftScript

    fun selectScript(id: String) {
        selectedScriptId = id
        if (id == "new" && draftScript.wordCount == 0) {
            draftScript = draftScript.copy(playbackSettings = globalDefaults)
        }
        playbackSettings = script(id).playbackSettings
    }

    fun updateScript(id: String, title: String, text: String) {
        val old = script(id)
        val updated = old.copy(
            title = title,
            plainTextPreview = text.replace('\n', ' ').take(140),
            content = ScriptContent(text.split("\n\n").mapIndexed { index, paragraph ->
                ScriptBlock.Paragraph("edited-$index", listOf(ScriptSpan(paragraph)))
            }),
            wordCount = text.count { !it.isWhitespace() },
            lastModifiedAt = System.currentTimeMillis(),
        )
        if (id == "new") draftScript = updated else scripts = scripts.map { if (it.id == id) updated else it }
    }

    fun setSurface(surface: PrompterSurface) { prompterSurface = surface }

    fun beginPlayback(scriptId: String) {
        selectScript(scriptId)
        playbackState = PlaybackState.Preparing
        onPlaybackEvent(PlaybackEvent.StartPlayback)
    }

    fun finishCountdown() { playbackState = PlaybackState.Playing }

    fun updateGuidePosition(position: Float) {
        playbackSettings = playbackSettings.copy(guideLinePosition = position.coerceIn(0.15f, 0.75f))
    }

    fun onPlaybackEvent(event: PlaybackEvent) {
        when (event) {
            PlaybackEvent.StartPlayback -> playbackState = if (playbackSettings.countdown == CountdownOption.Off) PlaybackState.Playing else PlaybackState.Countdown(playbackSettings.countdown.seconds)
            PlaybackEvent.PausePlayback -> playbackState = PlaybackState.Paused
            PlaybackEvent.ResumeImmediately -> playbackState = PlaybackState.Playing
            PlaybackEvent.ResumeWithCountdown -> playbackState = PlaybackState.Countdown(3)
            PlaybackEvent.IncreaseSpeed -> playbackSettings = playbackSettings.copy(speedMultiplier = (playbackSettings.speedMultiplier + 0.1f).coerceAtMost(2f))
            PlaybackEvent.DecreaseSpeed -> playbackSettings = playbackSettings.copy(speedMultiplier = (playbackSettings.speedMultiplier - 0.1f).coerceAtLeast(0.5f))
            PlaybackEvent.SeekForwardSmall -> progress = (progress + 0.03f).coerceAtMost(1f)
            PlaybackEvent.SeekBackwardSmall -> progress = (progress - 0.03f).coerceAtLeast(0f)
            is PlaybackEvent.SeekTo -> progress = event.progress.coerceIn(0f, 1f)
            PlaybackEvent.EndPlayback -> playbackState = PlaybackState.Finished
            PlaybackEvent.ToggleGuideLine -> playbackSettings = playbackSettings.copy(guideLineEnabled = !playbackSettings.guideLineEnabled)
            is PlaybackEvent.ChangeGuideLineStyle -> playbackSettings = playbackSettings.copy(guideLineStyle = event.style)
        }
    }

    fun resetPlayback() {
        playbackState = PlaybackState.Exited
        progress = 0f
    }
}
