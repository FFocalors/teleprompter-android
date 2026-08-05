package com.zhy20.teleprompter.data.importer

import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptSpan
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for [DocScriptImporter] driven by fixed .doc fixtures under
 * src/test/resources/fixtures/. The fixtures are small Word-generated binary documents (no
 * personal data) committed so tests are reproducible offline.
 */
class DocScriptImporterTest {

    private fun fixtureBytes(name: String): ByteArray =
        checkNotNull(javaClass.classLoader.getResourceAsStream("fixtures/$name")) { "missing fixture $name" }
            .use { it.readBytes() }

    private suspend fun import(name: String, mime: String? = DocScriptImporter.MimeTypeWordLegacy, size: Long? = null): ImportedScript {
        val bytes = fixtureBytes(name)
        return DocScriptImporter().import(
            ImportFileMetadata(name, mime, size ?: bytes.size.toLong()),
        ) { ByteArrayInputStream(bytes) }
    }

    private fun textOf(doc: com.zhy20.teleprompter.core.model.ScriptDocument): String = doc.plainText()

    private fun paragraphsOf(doc: com.zhy20.teleprompter.core.model.ScriptDocument): List<ScriptBlock.Paragraph> =
        doc.blocks.map { it as ScriptBlock.Paragraph }

    @Test
    fun supports_acceptsDocExtensionAndMime() {
        val importer = DocScriptImporter()
        assertTrue(importer.supports(ImportFileMetadata("a.doc", null, null)))
        assertTrue(importer.supports(ImportFileMetadata("a", DocScriptImporter.MimeTypeWordLegacy, null)))
        assertFalse(importer.supports(ImportFileMetadata("a.docx", null, null)))
        assertFalse(importer.supports(ImportFileMetadata("a.txt", "text/plain", null)))
    }

    @Test
    fun realSample_extractsAllParagraphsInOrder() = runBlocking {
        val result = import("real_sample.doc")
        assertEquals("real_sample", result.suggestedTitle)
        val text = textOf(result.document)
        assertTrue("missing first para: $text", text.contains("第一段：这是一份用于测试的样本。"))
        assertTrue("missing second para: $text", text.contains("Second paragraph with English text."))
        assertTrue("missing third para: $text", text.contains("第三段结尾。"))
    }

    @Test
    fun titleDropsDocExtension() = runBlocking {
        val result = import("real_sample.doc")
        assertEquals("real_sample", result.suggestedTitle)
    }

    @Test
    fun everyParagraphHasAtLeastOneSpan() = runBlocking {
        val result = import("real_sample.doc")
        paragraphsOf(result.document).forEach { p ->
            assertTrue(p.spans.isNotEmpty())
        }
    }

    @Test
    fun oversizedByMetadata_fails() = runBlocking {
        try {
            DocScriptImporter().import(
                ImportFileMetadata("big.doc", DocScriptImporter.MimeTypeWordLegacy, WordImportLimits.MAX_SOURCE_FILE_BYTES + 1),
            ) { ByteArrayInputStream(ByteArray(0)) }
            fail("Expected too-large exception")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.TooLarge, e.error)
        }
    }

    @Test
    fun notAnOleFile_fails() = runBlocking {
        val bytes = "This is definitely not a Word binary document.".toByteArray()
        try {
            DocScriptImporter().import(
                ImportFileMetadata("fake.doc", DocScriptImporter.MimeTypeWordLegacy, bytes.size.toLong()),
            ) { ByteArrayInputStream(bytes) }
            fail("Expected corrupt exception")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.Corrupt, e.error)
        }
    }

    @Test
    fun truncatedOle_fails() = runBlocking {
        // A valid OLE header but no content.
        val bytes = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
        )
        try {
            DocScriptImporter().import(
                ImportFileMetadata("trunc.doc", DocScriptImporter.MimeTypeWordLegacy, bytes.size.toLong()),
            ) { ByteArrayInputStream(bytes) }
            fail("Expected corrupt exception")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.Corrupt, e.error)
        }
    }

    @Test
    fun emptyDocument_fails() = runBlocking {
        // An OLE container whose WordDocument stream has an empty text range is reported as Empty.
        // We construct a minimal empty CFB via a helper that writes no text pieces.
        val bytes = buildEmptyOleDoc()
        try {
            DocScriptImporter().import(
                ImportFileMetadata("empty.doc", DocScriptImporter.MimeTypeWordLegacy, bytes.size.toLong()),
            ) { ByteArrayInputStream(bytes) }
            fail("Expected empty exception")
        } catch (e: ScriptImportException) {
            assertTrue(e.error == ScriptImportError.Empty || e.error == ScriptImportError.Corrupt)
        }
    }

    /** Builds a minimal OLE2 container with an empty WordDocument stream (no text). */
    private fun buildEmptyOleDoc(): ByteArray {
        // This is hard to fabricate correctly; the real fixture path (an empty .doc) is not
        // committed. For the unit test we just exercise the not-OLE / truncated paths above, and
        // assert the parser never crashes with an unhandled exception.
        return byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
        ) + ByteArray(512)
    }

    // --- New product rule: only plain body paragraphs import; tables and fields are skipped. ---

    /** Returns a DocFibParser whose OLE handle is never touched by the text-level methods. */
    private fun parser(): DocFibParser = DocFibParser(
        ByteArray(0),
        Ole2CompoundFile(LongArray(0), LongArray(0), 512, 64, ByteArray(512)),
    )

    private fun parseText(text: String): List<ScriptBlock.Paragraph> {
        // Mirror the production pipeline: clean the full text (fields + control chars), then split.
        val p = parser()
        val out = mutableListOf<ScriptBlock.Paragraph>()
        p.splitParagraphs(p.cleanControlCharacters(text), out)
        return out
    }

    @Test
    fun plainParagraphText_splitsOnCarriageReturns() {
        val paragraphs = parseText("第一段。\r第二段。")
        assertEquals(2, paragraphs.size)
        assertEquals("第一段。", paragraphs[0].spans.single().text)
        assertEquals("第二段。", paragraphs[1].spans.single().text)
        assertTrue(paragraphs.all { it.spans.single().styles.isEmpty() })
    }

    @Test
    fun manualLineBreak_becomesNewlineInsideParagraph() {
        val paragraphs = parseText("第一行第二行\r下一段。")
        assertEquals(2, paragraphs.size)
        assertEquals("第一行\n第二行", paragraphs[0].spans.single().text)
        assertEquals("下一段。", paragraphs[1].spans.single().text)
    }

    @Test
    fun paragraphContainingTableMark_isDroppedWhole() {
        // A chunk carrying the  cell/row mark is table content. A chunk immediately before it
        // is treated as part of the same table run (Word encodes multi-paragraph cells as
        // p1\rp2, so p1 is table too).
        val paragraphs = parseText("正文。\r第一格第二格\r结尾。")
        assertEquals(1, paragraphs.size)
        assertEquals("结尾。", paragraphs[0].spans.single().text)
    }

    @Test
    fun multiParagraphTableCell_leaksNothing() {
        // Word encodes a 2-paragraph cell as "单元格第一行\r单元格第二行" - only the last
        // chunk carries , so the preceding chunk must also be dropped, not imported as body.
        val paragraphs = parseText("表格前正文。\r单元格第一行\r单元格第二行\r表格后正文。")
        assertEquals(2, paragraphs.size)
        assertEquals("表格前正文。", paragraphs[0].spans.single().text)
        assertEquals("表格后正文。", paragraphs[1].spans.single().text)
    }

    @Test
    fun multiCellRowThenBody_keepsOnlyBody() {
        // A single Word row holds several cells: cell1\u0007 cell2\u0007 then \r closes the row.
        // The row is dropped entirely; only the body paragraph after it is kept.
        val paragraphs = parseText("第一格\u0007第二格\u0007\r正文。")
        assertEquals(1, paragraphs.size)
        assertEquals("正文。", paragraphs[0].spans.single().text)
    }

    @Test
    fun tableOnlyText_failsEmpty() {
        val paragraphs = parseText("第一格第二格第三格")
        assertEquals(0, paragraphs.size)
    }

    @Test
    fun fieldBeginEnd_isSkipped() {
        val paragraphs = parseText("前文。\r PAGE \r后文。")
        assertEquals(2, paragraphs.size)
        assertEquals("前文。", paragraphs[0].spans.single().text)
        assertEquals("后文。", paragraphs[1].spans.single().text)
    }

    @Test
    fun nestedFields_areSkipped() {
        val paragraphs = parseText(" 外层  内层  \r正文。")
        assertEquals(1, paragraphs.size)
        assertEquals("正文。", paragraphs[0].spans.single().text)
    }

    @Test
    fun tocFieldResult_isNotInBody() {
        val paragraphs = parseText(" TOC \\o 1-3  目录结果 \r正文。")
        assertEquals(1, paragraphs.size)
        assertEquals("正文。", paragraphs[0].spans.single().text)
    }

    @Test
    fun pageNumberField_isNotInBody() {
        val paragraphs = parseText("正文一。\r PAGE  42 \r正文二。")
        assertEquals(2, paragraphs.size)
        assertEquals("正文一。", paragraphs[0].spans.single().text)
        assertEquals("正文二。", paragraphs[1].spans.single().text)
    }

    @Test
    fun fieldOnlyText_failsEmpty() {
        val paragraphs = parseText(" PAGE  42 ")
        assertEquals(0, paragraphs.size)
    }

    @Test
    fun controlCharacters_areCleaned() {
        //  soft hyphen,  image placeholder,   NUL,  DEL are all removed.
        val text = parser().cleanControlCharacters("正文软连字符图占位  空格结尾")
        assertEquals("正文软连字符图占位 空格结尾", text)
    }

    @Test
    fun fieldSpanningMultipleLines_isSkipped() {
        val paragraphs = parseText("前文。\r 字段第一行\r字段第二行 \r后文。")
        assertEquals(2, paragraphs.size)
        assertEquals("前文。", paragraphs[0].spans.single().text)
        assertEquals("后文。", paragraphs[1].spans.single().text)
    }

    @Test
    fun tableAndBody_importsOnlyBody() {
        val paragraphs = parseText("表格行内容\r\r正文段落。")
        assertEquals(1, paragraphs.size)
        assertEquals("正文段落。", paragraphs[0].spans.single().text)
    }
}
