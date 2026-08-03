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

/**
 * Inline formatting deliberately stays small for the first rich-text phase. It can be
 * persisted without coupling scripts to Compose or to an HTML representation.
 */
enum class ScriptSpanStyle { Bold, Italic, Underline }

data class ScriptSpan(
    val text: String,
    val styles: Set<ScriptSpanStyle> = emptySet(),
) {
    /** Compatibility constructor for the initial Mock data and future simple importers. */
    constructor(
        text: String,
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false,
    ) : this(
        text = text,
        styles = buildSet {
            if (bold) add(ScriptSpanStyle.Bold)
            if (italic) add(ScriptSpanStyle.Italic)
            if (underline) add(ScriptSpanStyle.Underline)
        },
    )

    val bold: Boolean get() = ScriptSpanStyle.Bold in styles
    val italic: Boolean get() = ScriptSpanStyle.Italic in styles
    val underline: Boolean get() = ScriptSpanStyle.Underline in styles
}

/** Compatibility aliases used by the richer editor terminology. */
typealias ScriptDocument = ScriptContent
typealias ScriptParagraph = ScriptBlock.Paragraph

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
    /** Null keeps older saved settings fully compatible. */
    val displayPresetId: String? = null,
)

data class DisplayPreset(
    val id: String,
    /** A stable resource key; the UI resolves it through localized strings. */
    val name: String,
    val backgroundColor: String,
    val textColor: String,
    val previewAccentColor: String? = null,
    val isCustom: Boolean = false,
)

object DisplayPresets {
    const val CustomId = "custom"

    val BlackOnWhite = DisplayPreset(
        id = "black_white",
        name = "black_white",
        backgroundColor = "#111319",
        textColor = "#F2F5FA",
        previewAccentColor = "#3E4C6B",
    )
    val WhiteOnBlack = DisplayPreset(
        id = "white_black",
        name = "white_black",
        backgroundColor = "#F1F3F6",
        textColor = "#171A22",
        previewAccentColor = "#3F6987",
    )
    val BlueOnWhite = DisplayPreset(
        id = "blue_white",
        name = "blue_white",
        backgroundColor = "#29465E",
        textColor = "#F2F5FA",
        previewAccentColor = "#81A9C5",
    )
    val GreenOnWhite = DisplayPreset(
        id = "green_white",
        name = "green_white",
        backgroundColor = "#294C42",
        textColor = "#F2F5FA",
        previewAccentColor = "#88AA97",
    )
    val Custom = DisplayPreset(
        id = CustomId,
        name = "custom",
        backgroundColor = "",
        textColor = "",
        isCustom = true,
    )

    val defaults = listOf(BlackOnWhite, WhiteOnBlack, BlueOnWhite, GreenOnWhite)

    fun matching(backgroundColor: String, textColor: String): DisplayPreset? = defaults.firstOrNull {
        it.backgroundColor.equals(backgroundColor, ignoreCase = true) &&
            it.textColor.equals(textColor, ignoreCase = true)
    }
}

fun PlaybackSettings.applyDisplayPreset(preset: DisplayPreset): PlaybackSettings = copy(
    backgroundColor = preset.backgroundColor,
    textColor = preset.textColor,
    displayPresetId = preset.id,
)

fun PlaybackSettings.withCustomColors(backgroundColor: String = this.backgroundColor, textColor: String = this.textColor): PlaybackSettings = copy(
    backgroundColor = backgroundColor,
    textColor = textColor,
    displayPresetId = DisplayPresets.CustomId,
)

fun PlaybackSettings.activeDisplayPreset(): DisplayPreset = when {
    displayPresetId == DisplayPresets.CustomId -> DisplayPresets.Custom
    displayPresetId != null -> DisplayPresets.defaults.firstOrNull { it.id == displayPresetId }
        ?: DisplayPresets.matching(backgroundColor, textColor)
        ?: DisplayPresets.Custom
    else -> DisplayPresets.matching(backgroundColor, textColor) ?: DisplayPresets.Custom
}

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
