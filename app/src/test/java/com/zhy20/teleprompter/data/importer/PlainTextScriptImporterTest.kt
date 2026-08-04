package com.zhy20.teleprompter.data.importer

import com.zhy20.teleprompter.core.model.ScriptBlock
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlainTextScriptImporterTest {
    private val importer = PlainTextScriptImporter()

    private suspend fun importText(text: String, name: String = "台本.txt", mime: String = "text/plain", size: Long? = null): ImportedScript =
        importer.import(
            ImportFileMetadata(name, mime, size),
            inputStreamProvider = { ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8)) },
        )

    private suspend fun importBytes(bytes: ByteArray, name: String = "台本.txt", mime: String = "text/plain", size: Long? = null): ImportedScript =
        importer.import(
            ImportFileMetadata(name, mime, size),
            inputStreamProvider = { ByteArrayInputStream(bytes) },
        )

    private fun textOf(document: com.zhy20.teleprompter.core.model.ScriptDocument): String = document.plainText()

    @Test
    fun utf8WithoutBom_parsesTitleAndText() = runTest {
        val result = importText("第一行内容。\n第二行内容。")
        assertEquals("台本", result.suggestedTitle)
        assertEquals("第一行内容。\n第二行内容。", textOf(result.document))
    }

    @Test
    fun utf8WithBom_parsesAndStripsBom() = runTest {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + "BOM 开头文本".toByteArray(StandardCharsets.UTF_8)
        val result = importBytes(bytes)
        assertEquals("BOM 开头文本", textOf(result.document))
    }

    @Test
    fun utf16Le_parses() = runTest {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val bytes = bom + "UTF16 小端".toByteArray(StandardCharsets.UTF_16LE)
        val result = importBytes(bytes)
        assertEquals("UTF16 小端", textOf(result.document))
    }

    @Test
    fun utf16Be_parses() = runTest {
        val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val bytes = bom + "UTF16 大端".toByteArray(StandardCharsets.UTF_16BE)
        val result = importBytes(bytes)
        assertEquals("UTF16 大端", textOf(result.document))
    }

    @Test
    fun gb18030_parsesAsChineseFallback() = runTest {
        val charset = TextEncodingDetector.compatFallback()
        if (charset == null) return@runTest
        val bytes = "国标编码正文".toByteArray(charset)
        val result = importBytes(bytes)
        assertEquals("国标编码正文", textOf(result.document))
    }

    @Test
    fun windowsLineEndings_normalizeToUnix() = runTest {
        val result = importText("第一行\r\n第二行\r\n")
        assertEquals("第一行\n第二行", textOf(result.document))
    }

    @Test
    fun unixLineEndings_arePreserved() = runTest {
        val result = importText("第一行\n第二行")
        assertEquals("第一行\n第二行", textOf(result.document))
    }

    @Test
    fun oldMacLineEndings_normalizeToUnix() = runTest {
        val result = importText("第一行\r第二行")
        assertEquals("第一行\n第二行", textOf(result.document))
    }

    @Test
    fun multipleParagraphs_separatedByBlankLines() = runTest {
        val result = importText("第一段。\n\n第二段。\n\n\n第三段。")
        assertEquals(3, result.document.blocks.size)
        assertEquals("第一段。\n\n第二段。\n\n第三段。", textOf(result.document))
    }

    @Test
    fun singleLineBreakInsideParagraph_isKept() = runTest {
        val result = importText("第一行\n第二行")
        assertEquals(1, result.document.blocks.size)
        val paragraph = result.document.blocks.single() as ScriptBlock.Paragraph
        assertEquals(1, paragraph.spans.size)
        assertEquals("第一行\n第二行", paragraph.spans.single().text)
    }

    @Test
    fun titleDropsTxtExtension_andTrims() = runTest {
        assertEquals("我的台本", importText("正文", name = "  我的台本.TXT  ").suggestedTitle)
    }

    @Test
    fun titleFallsBackToDefaultWhenBlank() = runTest {
        assertEquals("未命名台本", importText("正文", name = ".txt").suggestedTitle)
        assertEquals("未命名台本", importText("正文", name = "  .txt  ").suggestedTitle)
    }

    @Test
    fun titleNeverUsesFullUriString() = runTest {
        val uriLike = "content://com.android.providers.downloads.documents/document/1234"
        val result = importText("正文", name = uriLike)
        assertEquals(uriLike, result.suggestedTitle)
    }

    @Test
    fun emptyFile_reportsEmptyError() = runTest {
        try {
            importText("")
            fail("Expected empty-file exception")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.Empty, e.error)
        }
    }

    @Test
    fun whitespaceOnlyFile_reportsEmptyError() = runTest {
        try {
            importText("  \n\t\n  ")
            fail("Expected empty-file exception")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.Empty, e.error)
        }
    }

    @Test
    fun oversizedByMetadata_reportsTooLarge() = runTest {
        try {
            importText("正文", size = PlainTextScriptImporter.MaxImportBytes.toLong() + 1)
            fail("Expected too-large exception")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.TooLarge, e.error)
        }
    }

    @Test
    fun oversizedByStreamingRead_reportsTooLarge() = runTest {
        val big = ByteArray(PlainTextScriptImporter.MaxImportBytes + 10) { '字'.code.toByte() }
        try {
            importBytes(big, size = null) // provider does not advertise size; stream must still enforce.
            fail("Expected too-large exception")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.TooLarge, e.error)
        }
    }

    @Test
    fun unrecognizedEncoding_reportsError() = runTest {
        val bytes = byteArrayOf(0x80.toByte(), 0x81.toByte(), 0x82.toByte())
        try {
            importBytes(bytes)
            fail("Expected encoding error")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.UnrecognizedEncoding, e.error)
        }
    }

    @Test
    fun mixedChineseEnglishText_parsesBoth() = runTest {
        val result = importText("欢迎来到提词器。Welcome to the teleprompter.\n第二段 English text.")
        assertEquals(1, result.document.blocks.size)
        assertEquals("欢迎来到提词器。Welcome to the teleprompter.\n第二段 English text.", textOf(result.document))
    }

    @Test
    fun everyParagraphHasAtLeastOneSpan_andNoInlineStyles() = runTest {
        val result = importText("一段。\n\n二段。")
        result.document.blocks.forEach { block ->
            val paragraph = block as ScriptBlock.Paragraph
            assertTrue(paragraph.spans.isNotEmpty())
            paragraph.spans.forEach { span ->
                assertTrue(span.styles.isEmpty())
            }
        }
    }

    @Test
    fun paragraphIdsAreStableAndUnique() = runTest {
        val result = importText("一\n\n二\n\n三")
        val ids = result.document.blocks.map { (it as ScriptBlock.Paragraph).id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("imported-") })
    }

    @Test
    fun trailingBlankLines_doNotCreateEmptyParagraphs() = runTest {
        val result = importText("正文\n\n\n")
        assertEquals(1, result.document.blocks.size)
    }
}
