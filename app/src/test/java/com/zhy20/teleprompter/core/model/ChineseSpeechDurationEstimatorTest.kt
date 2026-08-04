package com.zhy20.teleprompter.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseSpeechDurationEstimatorTest {
    @Test
    fun emptyAndWhitespaceOnlyTextTakeZeroSeconds() {
        assertEquals(0, ChineseSpeechDurationEstimator.estimate(""))
        assertEquals(0, ChineseSpeechDurationEstimator.estimate("  \n\t  "))
    }

    @Test
    fun chineseBaseRateUses255UnitsPerMinute() {
        assertEquals(60, ChineseSpeechDurationEstimator.estimate("字".repeat(255)))
        assertEquals(200, ChineseSpeechDurationEstimator.estimate("字".repeat(850)))
    }

    @Test
    fun punctuationAndParagraphsAddPredictableNaturalPauses() {
        val plain = ChineseSpeechDurationEstimator.estimate("字".repeat(255))
        val withSentencePause = ChineseSpeechDurationEstimator.estimate("字".repeat(255) + "。")
        val withParagraph = ChineseSpeechDurationEstimator.estimate("字".repeat(255) + "\n\n字")
        val withWhitespaceWrappedParagraph = ChineseSpeechDurationEstimator.estimate("字".repeat(255) + "  \n  字")

        assertTrue(withSentencePause > plain)
        assertEquals(61, withParagraph)
        assertEquals(withParagraph, withWhitespaceWrappedParagraph)
    }

    @Test
    fun englishWordsAndDigitsAreCountedWithoutWhitespace() {
        assertEquals(1, ChineseSpeechDurationEstimator.estimate("hello world"))
        assertEquals(60, ChineseSpeechDurationEstimator.estimate("1".repeat(255)))
    }

    @Test
    fun richTextStylesDoNotChangeReadableDuration() {
        val plain = document(ScriptSpan("普通加粗斜体下划线"))
        val formatted = document(
            ScriptSpan("普通"),
            ScriptSpan("加粗", bold = true),
            ScriptSpan("斜体", italic = true),
            ScriptSpan("下划线", underline = true),
        )

        assertEquals(
            ChineseSpeechDurationEstimator.estimate(plain),
            ChineseSpeechDurationEstimator.estimate(formatted),
        )
    }

    private fun document(vararg spans: ScriptSpan): ScriptContent = ScriptContent(
        listOf(ScriptBlock.Paragraph("p", spans.toList())),
    )
}
