package com.zhy20.teleprompter.core.model

import kotlin.math.roundToInt

/**
 * Product-wide estimate for normal Mandarin reading speed.  This intentionally measures
 * readable source text rather than visual layout, so changing font size or alignment never
 * changes the estimate.
 */
object ChineseSpeechDurationEstimator {
    const val STANDARD_CHINESE_UNITS_PER_MINUTE = 255

    private const val CommaPauseMillis = 180L
    private const val SentencePauseMillis = 350L
    private const val ParagraphPauseMillis = 500L

    fun estimate(document: ScriptDocument): Int = estimate(document.plainText())

    fun estimate(text: CharSequence): Int {
        if (text.isEmpty()) return 0

        var units = 0.0
        var pausesMillis = 0L
        var index = 0
        while (index < text.length) {
            val character = text[index]
            when {
                character.isWhitespace() -> {
                    // Treat any contiguous whitespace containing a line break as one paragraph
                    // transition. Spaces themselves are not readable units and must not hide the
                    // paragraph pause when they precede or follow the line break.
                    var containsLineBreak = character == '\n' || character == '\r'
                    while (index + 1 < text.length && text[index + 1].isWhitespace()) {
                        index += 1
                        containsLineBreak = containsLineBreak ||
                            text[index] == '\n' || text[index] == '\r'
                    }
                    if (containsLineBreak) pausesMillis += ParagraphPauseMillis
                }
                character.isChineseIdeograph() || character.isDigit() -> units += 1.0
                character.isEnglishLetter() -> {
                    while (index + 1 < text.length && text[index + 1].isEnglishLetter()) index += 1
                    units += 1.5
                }
                else -> {
                    units += 1.0
                    pausesMillis += punctuationPause(character)
                }
            }
            index += 1
        }

        if (units == 0.0) return 0
        val speechMillis = units / STANDARD_CHINESE_UNITS_PER_MINUTE * 60_000.0
        return ((speechMillis + pausesMillis) / 1_000.0)
            .roundToInt()
            .coerceAtLeast(1)
    }

    private fun punctuationPause(character: Char): Long = when (character) {
        ',', '，', '、', ';', '；' -> CommaPauseMillis
        '.', '。', '?', '？', '!', '！', ':', '：' -> SentencePauseMillis
        else -> 0L
    }

    private fun Char.isChineseIdeograph(): Boolean =
        Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN

    private fun Char.isEnglishLetter(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z'
}

/** The UI should use this rather than trusting a stale cached value from an importer. */
fun Script.currentNormalEstimatedDurationSeconds(): Int =
    ChineseSpeechDurationEstimator.estimate(content)
