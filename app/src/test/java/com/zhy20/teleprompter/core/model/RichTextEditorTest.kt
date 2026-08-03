package com.zhy20.teleprompter.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextEditorTest {
    @Test
    fun addingAndRemovingBold_onlyChangesSelectedRange() {
        val initial = editor("普通文字", TextSelection(0, 2))
        val bold = initial.toggleStyle(ScriptSpanStyle.Bold)

        assertEquals(listOf(ScriptSpan("普通", bold = true), ScriptSpan("文字")), bold.document.firstParagraph().spans)

        val removed = bold.toggleStyle(ScriptSpanStyle.Bold)
        assertEquals(listOf(ScriptSpan("普通文字")), removed.document.firstParagraph().spans)
    }

    @Test
    fun addingItalicAndUnderline_preservesExistingStyles() {
        val bold = editor("格式", TextSelection(0, 2)).toggleStyle(ScriptSpanStyle.Bold)
        val italic = bold.toggleStyle(ScriptSpanStyle.Italic)
        val underlined = italic.toggleStyle(ScriptSpanStyle.Underline)

        assertEquals(
            setOf(ScriptSpanStyle.Bold, ScriptSpanStyle.Italic, ScriptSpanStyle.Underline),
            underlined.document.firstParagraph().spans.single().styles,
        )
    }

    @Test
    fun mixedBoldSelection_becomesUniformlyBold() {
        val document = documentOf(
            ScriptSpan("甲", bold = true),
            ScriptSpan("乙"),
            ScriptSpan("丙", bold = true),
        )

        val result = RichTextEditorState(document, TextSelection(0, 3)).toggleStyle(ScriptSpanStyle.Bold)

        assertEquals(listOf(ScriptSpan("甲乙丙", bold = true)), result.document.firstParagraph().spans)
    }

    @Test
    fun insertionAndDeletion_keepRemainingStyleRangesCorrect() {
        val boldMiddle = RichTextEditorState(documentOf(ScriptSpan("a"), ScriptSpan("bc", bold = true), ScriptSpan("d")))
        val inserted = boldMiddle.replaceText("aXbcd", TextSelection(2, 2))
        assertEquals("aX", inserted.document.firstParagraph().spans[0].text)
        assertEquals(setOf(ScriptSpanStyle.Bold), inserted.document.firstParagraph().spans[1].styles)
        assertEquals("bc", inserted.document.firstParagraph().spans[1].text)

        val deleted = boldMiddle.replaceText("ad", TextSelection(1, 1))
        assertEquals(listOf(ScriptSpan("ad")), deleted.document.firstParagraph().spans)
    }

    @Test
    fun adjacentIdenticalStyles_areMergedAndUndoRestoresDocumentAndSelection() {
        val initial = editor("合并", TextSelection(0, 2))
        val styled = initial.toggleStyle(ScriptSpanStyle.Bold)
        val undone = styled.undo()

        assertEquals(1, styled.document.firstParagraph().spans.size)
        assertEquals(initial.document, undone.document)
        assertEquals(initial.selection, undone.selection)
        assertFalse(undone.canUndo)
    }

    private fun editor(text: String, selection: TextSelection): RichTextEditorState =
        RichTextEditorState(documentOf(ScriptSpan(text)), selection)

    private fun documentOf(vararg spans: ScriptSpan): ScriptContent = ScriptContent(
        listOf(ScriptBlock.Paragraph("p", spans.toList())),
    )

    private fun ScriptContent.firstParagraph(): ScriptBlock.Paragraph = blocks.single() as ScriptBlock.Paragraph
}
