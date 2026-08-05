package com.zhy20.teleprompter.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zhy20.teleprompter.core.model.CountdownOption
import com.zhy20.teleprompter.core.model.ChineseSpeechDurationEstimator
import com.zhy20.teleprompter.core.model.PlaybackEvent
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.PlaybackState
import com.zhy20.teleprompter.core.model.PrompterSurface
import com.zhy20.teleprompter.core.model.RichTextEditorState
import com.zhy20.teleprompter.core.model.SaveState
import com.zhy20.teleprompter.core.model.Script
import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptFolder
import com.zhy20.teleprompter.core.model.ScriptSpan
import com.zhy20.teleprompter.core.model.currentNormalEstimatedDurationSeconds
import com.zhy20.teleprompter.core.model.normalizedToDisplayPreset
import com.zhy20.teleprompter.core.util.PlaybackEngine
import com.zhy20.teleprompter.core.util.PlaybackEngineState

class AppState(
    private val clockNanos: () -> Long = System::nanoTime,
    initialScripts: List<Script> = emptyList(),
    initialFolders: List<ScriptFolder> = emptyList(),
    initialDefaults: PlaybackSettings = PlaybackSettings(),
) {
    var scripts by mutableStateOf(initialScripts)
        private set
    var folders by mutableStateOf(initialFolders)
        private set

    var selectedScriptId by mutableStateOf(initialScripts.firstOrNull()?.id.orEmpty())
        private set
    var draftScript by mutableStateOf(emptyScript("new", initialDefaults))
        private set
    var playbackSettings by mutableStateOf(initialScripts.firstOrNull()?.playbackSettings ?: initialDefaults)
    var globalDefaults by mutableStateOf(initialDefaults)
    var playbackSession by mutableStateOf(PlaybackEngineState())
        private set
    var playbackState: PlaybackState
        get() = playbackSession.playbackState
        set(value) {
            playbackSession = PlaybackEngine.setPlaybackState(playbackSession, value, clockNanos())
        }
    var prompterSurface by mutableStateOf(PrompterSurface.Library)
    var progress: Float
        get() = playbackSession.currentSemanticProgress
        set(value) {
            playbackSession = PlaybackEngine.seek(playbackSession, value, clockNanos())
        }
    var saveState by mutableStateOf(SaveState.Initial)
    var selectedLanguage by mutableStateOf("zh-CN")
    private val editorStates = mutableMapOf<String, RichTextEditorState>()

    fun script(id: String): Script = scripts.firstOrNull { it.id == id }
        ?: if (id == "new") draftScript else emptyScript(id, playbackSettings)

    fun setLibraryData(scripts: List<Script>, folders: List<ScriptFolder>) {
        this.scripts = scripts
        this.folders = folders
    }

    fun setActiveScript(script: Script) {
        scripts = (scripts.filterNot { it.id == script.id } + script).sortedByDescending { it.lastModifiedAt }
        selectedScriptId = script.id
        playbackSettings = script.playbackSettings.normalizedToDisplayPreset()
    }

    /** Always derived from the current ScriptDocument; the model field is only a cache. */
    fun normalEstimatedDurationSeconds(id: String): Int = script(id).currentNormalEstimatedDurationSeconds()

    fun selectScript(id: String) {
        selectedScriptId = id
        if (id == "new" && draftScript.wordCount == 0) {
            draftScript = draftScript.copy(playbackSettings = globalDefaults)
        }
        playbackSettings = script(id).playbackSettings.normalizedToDisplayPreset()
    }

    fun editorState(id: String): RichTextEditorState = editorStates.getOrPut(id) {
        val script = script(id)
        RichTextEditorState(script.content)
    }

    fun updateEditor(id: String, title: String, editorState: RichTextEditorState) {
        editorStates[id] = editorState
        updateScript(id, title, editorState.document)
    }

    fun updateScript(id: String, title: String, content: com.zhy20.teleprompter.core.model.ScriptContent) {
        val old = script(id)
        val updated = old.copy(
            title = title,
            plainTextPreview = content.plainText().replace('\n', ' ').take(140),
            content = content,
            wordCount = content.plainText().count { !it.isWhitespace() },
            normalEstimatedDurationSeconds = ChineseSpeechDurationEstimator.estimate(content),
            lastModifiedAt = System.currentTimeMillis(),
        )
        if (id == "new") draftScript = updated else scripts = scripts.map { if (it.id == id) updated else it }
    }

    fun updatePlaybackSettings(settings: PlaybackSettings) {
        val now = clockNanos()
        playbackSettings = settings.normalizedToDisplayPreset()
        val id = selectedScriptId
        val old = script(id)
        val updated = old.copy(playbackSettings = playbackSettings, lastModifiedAt = System.currentTimeMillis())
        if (id == "new") draftScript = updated else scripts = scripts.map { if (it.id == id) updated else it }
        playbackSession = PlaybackEngine.reconfigure(
            playbackSession,
            playbackSettings,
            normalEstimatedDurationSeconds(id),
            now,
        )
    }

    fun setSurface(surface: PrompterSurface) { prompterSurface = surface }

    fun beginPlayback(scriptId: String) {
        selectScript(scriptId)
        startPlaybackFromBeginning()
    }

    /**
     * Starts a new session for the selected script. This is deliberately separate from resume:
     * the setup page and remote-control entry point must never inherit a prior session position.
     */
    private fun startPlaybackFromBeginning() {
        playbackSession = PlaybackEngine.prepare(playbackSettings, normalEstimatedDurationSeconds(selectedScriptId))
        val initialState = if (playbackSettings.countdown == CountdownOption.Off) {
            PlaybackState.Playing
        } else {
            PlaybackState.Countdown(playbackSettings.countdown.seconds)
        }
        playbackSession = PlaybackEngine.setPlaybackState(playbackSession, initialState, clockNanos())
    }

    fun finishCountdown() {
        val now = clockNanos()
        playbackSession = if (playbackSession.isStartingFromBeginning) {
            PlaybackEngine.playFromBeginning(playbackSession, now)
        } else {
            // Resume countdowns retain their semantic progress and effective elapsed time.
            PlaybackEngine.setPlaybackState(playbackSession, PlaybackState.Playing, now)
        }
    }

    fun updatePlaybackLayout(viewportHeightPx: Float, textHeightPx: Float) {
        playbackSession = PlaybackEngine.updateLayout(
            playbackSession,
            viewportHeightPx,
            textHeightPx,
            playbackSettings,
            normalEstimatedDurationSeconds(selectedScriptId),
            clockNanos(),
        )
    }

    fun onPlaybackFrame(frameTimeNanos: Long) {
        playbackSession = PlaybackEngine.tick(playbackSession, frameTimeNanos)
    }

    fun beginManualProgressAdjustment() {
        playbackSession = PlaybackEngine.beginManualAdjustment(playbackSession, clockNanos())
    }

    fun endManualProgressAdjustment() {
        playbackSession = PlaybackEngine.endManualAdjustment(playbackSession, clockNanos())
    }

    fun updateGuidePosition(position: Float) {
        updatePlaybackSettings(playbackSettings.copy(guideLinePosition = position.coerceIn(0.15f, 0.75f)))
    }

    fun onPlaybackEvent(event: PlaybackEvent) {
        when (event) {
            PlaybackEvent.StartPlayback -> startPlaybackFromBeginning()
            PlaybackEvent.PausePlayback -> playbackState = PlaybackState.Paused
            PlaybackEvent.ResumeImmediately -> playbackState = PlaybackState.Playing
            PlaybackEvent.ResumeWithCountdown -> playbackState = PlaybackState.Countdown(3)
            PlaybackEvent.CancelResumeCountdown -> playbackState = PlaybackState.Paused
            PlaybackEvent.IncreaseSpeed -> updatePlaybackSettings(playbackSettings.copy(speedMultiplier = (playbackSettings.speedMultiplier + 0.1f).coerceAtMost(2f)))
            PlaybackEvent.DecreaseSpeed -> updatePlaybackSettings(playbackSettings.copy(speedMultiplier = (playbackSettings.speedMultiplier - 0.1f).coerceAtLeast(0.5f)))
            PlaybackEvent.SeekForwardSmall -> progress = (progress + 0.03f).coerceAtMost(1f)
            PlaybackEvent.SeekBackwardSmall -> progress = (progress - 0.03f).coerceAtLeast(0f)
            is PlaybackEvent.SeekTo -> progress = event.progress.coerceIn(0f, 1f)
            PlaybackEvent.EndPlayback -> playbackState = PlaybackState.Finished
            is PlaybackEvent.ChangeGuideMode -> updatePlaybackSettings(playbackSettings.copy(guideMode = event.mode))
        }
    }

    fun resetPlayback() {
        playbackSession = PlaybackEngine.reset()
    }

    fun restorePlaybackSession(scriptId: String, settings: PlaybackSettings, session: PlaybackEngineState) {
        selectedScriptId = scriptId
        playbackSettings = settings.normalizedToDisplayPreset()
        val old = script(scriptId)
        val updated = old.copy(playbackSettings = playbackSettings)
        if (scriptId == "new") draftScript = updated else scripts = scripts.map { if (it.id == scriptId) updated else it }
        playbackSession = session
    }
}

private fun emptyScript(id: String, settings: PlaybackSettings): Script = Script(
    id = id,
    title = "",
    plainTextPreview = "",
    content = ScriptContent(listOf(ScriptBlock.Paragraph("paragraph-0", listOf(ScriptSpan(""))))),
    folderId = null,
    wordCount = 0,
    normalEstimatedDurationSeconds = 1,
    lastModifiedAt = 0L,
    playbackSettings = settings,
)
