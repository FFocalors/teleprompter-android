package com.zhy20.teleprompter.core.design

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptAnnotatedStringMapperTest {
    @Test
    fun normalAndBoldRangesKeepIndependentFontWeights() {
        val annotated = ScriptAnnotatedStringMapper.map(document(
            ScriptSpan("普通"),
            ScriptSpan("加粗", bold = true),
            ScriptSpan("结尾"),
        ))

        assertEquals(FontWeight.Normal, styleAt(annotated, 0).fontWeight)
        assertEquals(FontWeight.Bold, styleAt(annotated, 2).fontWeight)
        assertEquals(FontWeight.Normal, styleAt(annotated, 4).fontWeight)
    }

    @Test
    fun italicUnderlineAndCombinedStylesMapExplicitly() {
        val annotated = ScriptAnnotatedStringMapper.map(document(
            ScriptSpan("斜", italic = true),
            ScriptSpan("线", underline = true),
            ScriptSpan("组", bold = true, italic = true, underline = true),
        ))

        assertEquals(FontStyle.Italic, styleAt(annotated, 0).fontStyle)
        assertEquals(TextDecoration.Underline, styleAt(annotated, 1).textDecoration)
        assertEquals(FontWeight.Bold, styleAt(annotated, 2).fontWeight)
        assertEquals(FontStyle.Italic, styleAt(annotated, 2).fontStyle)
        assertEquals(TextDecoration.Underline, styleAt(annotated, 2).textDecoration)
    }

    private fun styleAt(annotated: androidx.compose.ui.text.AnnotatedString, index: Int) =
        annotated.spanStyles.first { index in it.start until it.end }.item

    private fun document(vararg spans: ScriptSpan): ScriptContent = ScriptContent(
        listOf(ScriptBlock.Paragraph("p", spans.toList())),
    )
}
