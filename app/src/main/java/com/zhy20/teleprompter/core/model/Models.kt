package com.zhy20.teleprompter.core.model

data class Script(
    val id: String,
    val title: String,
    val plainTextPreview: String,
    val content: ScriptContent,
    val folderId: String?,
    val wordCount: Int,
    val normalEstimatedDurationSeconds: Int,
    val lastModifiedAt: Long,
    val playbackSettings: PlaybackSettings,
)

data class ScriptContent(val blocks: List<ScriptBlock>) {
    fun plainText(): String = blocks.joinToString("\n\n") { block ->
        when (block) {
            is ScriptBlock.Paragraph -> block.spans.joinToString("") { it.text }
        }
    }
}

sealed interface ScriptBlock {
    data class Paragraph(val id: String, val spans: List<ScriptSpan>) : ScriptBlock
}

data class ScriptSpan(val text: String, val bold: Boolean = false)

data class ScriptFolder(
    val id: String,
    val name: String,
    val createdAt: Long,
    val scriptCount: Int,
)

data class PlaybackSettings(
    val backgroundColor: String = "#141622",
    val textColor: String = "#F2F5FA",
    val fontSize: Int = 64,
    val orientation: PlaybackOrientation = PlaybackOrientation.Landscape,
    val mirrorEnabled: Boolean = false,
    val rhythmMode: RhythmMode = RhythmMode.Speed,
    val speedMultiplier: Float = 1f,
    val targetDurationSeconds: Int = 200,
    val countdown: CountdownOption = CountdownOption.ThreeSeconds,
    val guideLineEnabled: Boolean = true,
    val guideLineStyle: GuideLineStyle = GuideLineStyle.Highlight,
    val guideLinePosition: Float = 0.25f,
)

enum class PlaybackOrientation { Portrait, Landscape }
enum class RhythmMode { Speed, TargetDuration }
enum class CountdownOption(val seconds: Int) { Off(0), ThreeSeconds(3), FiveSeconds(5), TenSeconds(10) }
enum class GuideLineStyle { Highlight, Line }
enum class RemoteConnectionState { Disconnected, Waiting, Connected, ConnectionLost }
enum class PrompterSurface { Library, Editor, Setup, Prompter }
enum class SaveState { Saving, Saved, Error }

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Preparing : PlaybackState
    data class Countdown(val secondsRemaining: Int) : PlaybackState
    data object Playing : PlaybackState
    data object Paused : PlaybackState
    data object Finished : PlaybackState
    data object Exited : PlaybackState
}

sealed interface PlaybackEvent {
    data object StartPlayback : PlaybackEvent
    data object PausePlayback : PlaybackEvent
    data object ResumeImmediately : PlaybackEvent
    data object ResumeWithCountdown : PlaybackEvent
    data object IncreaseSpeed : PlaybackEvent
    data object DecreaseSpeed : PlaybackEvent
    data object SeekForwardSmall : PlaybackEvent
    data object SeekBackwardSmall : PlaybackEvent
    data class SeekTo(val progress: Float) : PlaybackEvent
    data object EndPlayback : PlaybackEvent
    data object ToggleGuideLine : PlaybackEvent
    data class ChangeGuideLineStyle(val style: GuideLineStyle) : PlaybackEvent
}
