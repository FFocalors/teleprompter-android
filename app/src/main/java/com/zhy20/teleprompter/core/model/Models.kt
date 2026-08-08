package com.zhy20.teleprompter.core.model

/**
 * A single teleprompter script: title, rich-text body, folder membership and playback
 * settings, plus derived metadata (plain-text preview, word count, estimated duration).
 * The body is a [ScriptContent] document, so formatting survives without coupling the
 * model to Compose or to an HTML representation.
 */
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

/**
 * A rich-text document consisting of [ScriptBlock]s. When flattened to plain text,
 * paragraphs are joined by blank lines (see [plainText]).
 */
data class ScriptContent(val blocks: List<ScriptBlock>) {
    fun plainText(): String = blocks.joinToString("\n\n") { block ->
        when (block) {
            is ScriptBlock.Paragraph -> block.spans.joinToString("") { it.text }
        }
    }
}

/**
 * One block of a [ScriptContent] document. Only [Paragraph] exists today; future block
 * kinds (headings, lists) can be added without changing how inline spans are stored.
 */
sealed interface ScriptBlock {
    /**
     * A paragraph of inline [ScriptSpan]s. The [id] is stable within a document and
     * survives serialization, so editors and the network layer can address a paragraph.
     */
    data class Paragraph(val id: String, val spans: List<ScriptSpan>) : ScriptBlock
}

/**
 * Inline formatting deliberately stays small for the first rich-text phase. It can be
 * persisted without coupling scripts to Compose or to an HTML representation.
 */
enum class ScriptSpanStyle { Bold, Italic, Underline }

/**
 * An inline run of [text] that shares one set of [styles]. When a document is rebuilt,
 * adjacent characters with identical style sets are merged into a single span, so a
 * span is the smallest unit a consumer needs to render or persist.
 */
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
    val backgroundColor: String = "#121719",
    val textColor: String = "#F5F7FA",
    val fontSize: Int = 64,
    val orientation: PlaybackOrientation = PlaybackOrientation.Landscape,
    val textAlignment: PlaybackTextAlignment = PlaybackTextAlignment.Start,
    val mirrorEnabled: Boolean = false,
    val rhythmMode: RhythmMode = RhythmMode.Speed,
    val speedMultiplier: Float = 1f,
    val targetDurationSeconds: Int = 200,
    val countdown: CountdownOption = CountdownOption.ThreeSeconds,
    val guideMode: GuideMode = GuideMode.HighlightBar,
    val guideLinePosition: Float = 0.25f,
    /** Kept alongside resolved colors so playback never depends on a UI component. */
    val displayPresetId: String? = "black_white",
)

data class DisplayPreset(
    val id: String,
    /** A stable resource key; the UI resolves it through localized strings. */
    val name: String,
    val backgroundColor: String,
    val textColor: String,
    val previewLabel: String,
    val guideLineDarkModeColor: String,
    val guideLineLightModeColor: String,
)

object DisplayPresets {
    val BlackOnWhite = DisplayPreset(
        id = "black_white",
        name = "black_white",
        backgroundColor = "#121719",
        textColor = "#F5F7FA",
        previewLabel = "preset_black_white",
        guideLineDarkModeColor = "#FF453A",
        guideLineLightModeColor = "#C62828",
    )
    val WhiteOnBlack = DisplayPreset(
        id = "white_black",
        name = "white_black",
        backgroundColor = "#EFF2F4",
        textColor = "#202426",
        previewLabel = "preset_white_black",
        guideLineDarkModeColor = "#FF453A",
        guideLineLightModeColor = "#C62828",
    )
    val BlueOnWhite = DisplayPreset(
        id = "deep_blue_white",
        name = "deep_blue_white",
        backgroundColor = "#1D3550",
        textColor = "#F5F7FA",
        previewLabel = "preset_deep_blue_white",
        guideLineDarkModeColor = "#FF453A",
        guideLineLightModeColor = "#C62828",
    )
    val GreenOnWhite = DisplayPreset(
        id = "deep_green_white",
        name = "deep_green_white",
        backgroundColor = "#1E443B",
        textColor = "#F5F7FA",
        previewLabel = "preset_deep_green_white",
        guideLineDarkModeColor = "#FF453A",
        guideLineLightModeColor = "#C62828",
    )

    val defaults = listOf(BlackOnWhite, WhiteOnBlack, BlueOnWhite, GreenOnWhite)

    fun matching(backgroundColor: String, textColor: String): DisplayPreset? = defaults.firstOrNull {
        it.backgroundColor.equals(backgroundColor, ignoreCase = true) &&
            it.textColor.equals(textColor, ignoreCase = true)
    }

    fun nearest(backgroundColor: String, textColor: String): DisplayPreset {
        val targetBackground = backgroundColor.toRgbOrNull()
        val targetText = textColor.toRgbOrNull()
        if (targetBackground == null || targetText == null) return BlackOnWhite
        return defaults.minByOrNull { preset ->
            preset.backgroundColor.toRgbOrNull()!!.distanceTo(targetBackground) +
                preset.textColor.toRgbOrNull()!!.distanceTo(targetText)
        } ?: BlackOnWhite
    }
}

fun PlaybackSettings.applyDisplayPreset(preset: DisplayPreset): PlaybackSettings = copy(
    backgroundColor = preset.backgroundColor,
    textColor = preset.textColor,
    displayPresetId = preset.id,
)

fun PlaybackSettings.activeDisplayPreset(): DisplayPreset =
    DisplayPresets.matching(backgroundColor, textColor)
        ?: DisplayPresets.nearest(backgroundColor, textColor)

/** Converts legacy or unsupported colors to the closest supported display pair. */
fun PlaybackSettings.normalizedToDisplayPreset(): PlaybackSettings = applyDisplayPreset(activeDisplayPreset())

fun DisplayPreset.guideLineColorForBackground(): String = if (backgroundColor.toRgbOrNull()?.isLight() == true) {
    guideLineLightModeColor
} else {
    guideLineDarkModeColor
}

private data class Rgb(val red: Int, val green: Int, val blue: Int) {
    fun distanceTo(other: Rgb): Int =
        (red - other.red) * (red - other.red) +
            (green - other.green) * (green - other.green) +
            (blue - other.blue) * (blue - other.blue)

    fun isLight(): Boolean = (red * 299 + green * 587 + blue * 114) / 1000 >= 150
}

private fun String.toRgbOrNull(): Rgb? = runCatching {
    val normalized = removePrefix("#")
    require(normalized.length == 6)
    Rgb(
        normalized.substring(0, 2).toInt(16),
        normalized.substring(2, 4).toInt(16),
        normalized.substring(4, 6).toInt(16),
    )
}.getOrNull()

enum class PlaybackOrientation { Portrait, Landscape }
enum class PlaybackTextAlignment { Start, Center, End }
enum class RhythmMode { Speed, TargetDuration }
enum class CountdownOption(val seconds: Int) { Off(0), ThreeSeconds(3), FiveSeconds(5), TenSeconds(10) }
enum class GuideMode { Off, Line, HighlightBar }

/** The only visual contract consumed by setup and playback renderers. */
data class GuideVisualState(val lineVisible: Boolean, val highlightBarVisible: Boolean)

fun GuideMode.visualState(): GuideVisualState = when (this) {
    GuideMode.Off -> GuideVisualState(lineVisible = false, highlightBarVisible = false)
    GuideMode.Line -> GuideVisualState(lineVisible = true, highlightBarVisible = false)
    GuideMode.HighlightBar -> GuideVisualState(lineVisible = false, highlightBarVisible = true)
}

/** One-way migration helper for data written before GuideMode became the source of truth. */
fun guideModeFromLegacy(enabled: Boolean, highlighted: Boolean): GuideMode = when {
    !enabled -> GuideMode.Off
    highlighted -> GuideMode.HighlightBar
    else -> GuideMode.Line
}
enum class RemoteConnectionState { Disconnected, Waiting, Connected, ConnectionLost }
enum class PrompterSurface { Library, Editor, Setup, Prompter }
enum class SaveState { Initial, Saving, Saved, Error }

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
    data object CancelResumeCountdown : PlaybackEvent
    data object IncreaseSpeed : PlaybackEvent
    data object DecreaseSpeed : PlaybackEvent
    data object SeekForwardSmall : PlaybackEvent
    data object SeekBackwardSmall : PlaybackEvent
    data class SeekTo(val progress: Float) : PlaybackEvent
    data object EndPlayback : PlaybackEvent
    data class ChangeGuideMode(val mode: GuideMode) : PlaybackEvent
}
