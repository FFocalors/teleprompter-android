package com.zhy20.teleprompter.data.importer

import com.zhy20.teleprompter.core.model.ScriptBlock
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Verifies the Markdown importer: extension/MIME routing, the shared text-decoding pipeline
 * (UTF-8, UTF-16, GB18030, BOM), size enforcement, and the title rules (ATX/Setext level-1 heading
 * vs file name fallback).
 */
class MarkdownScriptImporterTest {

    private val importer = MarkdownScriptImporter()

    private suspend fun importText(text: String, name: String = "台本.md", mime: String = "text/markdown", size: Long? = null): ImportedScript =
        importer.import(
            ImportFileMetadata(name, mime, size),
            inputStreamProvider = { ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8)) },
        )

    private suspend fun importBytes(bytes: ByteArray, name: String = "台本.md", mime: String? = "text/markdown", size: Long? = null): ImportedScript =
        importer.import(
            ImportFileMetadata(name, mime, size),
            inputStreamProvider = { ByteArrayInputStream(bytes) },
        )

    private fun bodyText(document: com.zhy20.teleprompter.core.model.ScriptDocument): String =
        document.blocks.joinToString("\n") { (it as ScriptBlock.Paragraph).spans.joinToString("") { s -> s.text } }

    // --- Routing ---

    @Test
    fun supports_mdExtension() {
        assertTrue(importer.supports(ImportFileMetadata("a.md", null, 1)))
    }

    @Test
    fun supports_markdownExtension() {
        assertTrue(importer.supports(ImportFileMetadata("a.markdown", null, 1)))
    }

    @Test
    fun supports_mimeTextMarkdown() {
        assertTrue(importer.supports(ImportFileMetadata("a", "text/markdown", 1)))
    }

    @Test
    fun supports_mimeTextXMarkdown() {
        assertTrue(importer.supports(ImportFileMetadata("a", "text/x-markdown", 1)))
    }

    @Test
    fun doesNotSupport_txtExtension() {
        assertTrue(!importer.supports(ImportFileMetadata("a.txt", "text/plain", 1)))
    }

    @Test
    fun mdExtension_matchIsCaseInsensitive() {
        assertTrue(importer.supports(ImportFileMetadata("a.MD", null, 1)))
    }

    // --- Encoding ---

    @Test
    fun utf8_readsCorrectly() = runTest {
        val result = importText("# 标题\n\n正文内容。")
        assertEquals("标题", result.suggestedTitle)
        assertEquals("正文内容。", bodyText(result.document))
    }

    @Test
    fun utf8WithBom_stripsBom() = runTest {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + "# BOM 标题\n\nBOM 正文".toByteArray(StandardCharsets.UTF_8)
        val result = importBytes(bytes)
        assertEquals("BOM 标题", result.suggestedTitle)
        assertEquals("BOM 正文", bodyText(result.document))
    }

    @Test
    fun utf16Le_readsCorrectly() = runTest {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val bytes = bom + "# UTF16 标题\n\n正文".toByteArray(StandardCharsets.UTF_16LE)
        val result = importBytes(bytes)
        assertEquals("UTF16 标题", result.suggestedTitle)
    }

    @Test
    fun utf16Be_readsCorrectly() = runTest {
        val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val bytes = bom + "# UTF16 大端\n\n正文".toByteArray(StandardCharsets.UTF_16BE)
        val result = importBytes(bytes)
        assertEquals("UTF16 大端", result.suggestedTitle)
    }

    @Test
    fun gb18030_readsChineseFallback() = runTest {
        val charset = TextEncodingDetector.compatFallback()
        if (charset == null) return@runTest
        val bytes = "# 国标标题\n\n国标编码正文".toByteArray(charset)
        val result = importBytes(bytes, mime = null)
        assertEquals("国标标题", result.suggestedTitle)
        assertEquals("国标编码正文", bodyText(result.document))
    }

    @Test
    fun windowsLineEndings_normalize() = runTest {
        val result = importText("# 标题\r\n\r\n第一段\r\n第二段")
        assertEquals("标题", result.suggestedTitle)
        assertEquals("第一段\n第二段", bodyText(result.document))
    }

    // --- Title rules ---

    @Test
    fun noH1_titleIsFileNameWithoutExtension() = runTest {
        val result = importText("普通正文", name = "我的笔记.md")
        assertEquals("我的笔记", result.suggestedTitle)
    }

    @Test
    fun noH1_markdownExtensionDropped() = runTest {
        val result = importText("正文", name = "speech.markdown")
        assertEquals("speech", result.suggestedTitle)
    }

    @Test
    fun atxH1_titleOverridesFileName() = runTest {
        val result = importText("# 台本标题\n\n正文", name = "文件名.md")
        assertEquals("台本标题", result.suggestedTitle)
    }

    @Test
    fun setextH1_titleOverridesFileName() = runTest {
        val result = importText("台本标题\n========\n\n正文", name = "文件名.md")
        assertEquals("台本标题", result.suggestedTitle)
    }

    @Test
    fun h1_doesNotRepeatIntoBody() = runTest {
        val result = importText("# 标题\n\n正文")
        assertEquals("正文", bodyText(result.document))
    }

    @Test
    fun blankFileName_fallsBackToDefaultTitle() = runTest {
        val result = importText("正文", name = ".md")
        assertEquals("未命名台本", result.suggestedTitle)
    }

    // --- Edge cases ---

    @Test
    fun emptyFile_returnsEmptyError() = runTest {
        try {
            importText("")
            fail("Expected empty error")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.Empty, e.error)
        }
    }

    @Test
    fun whitespaceOnlyFile_returnsEmptyError() = runTest {
        try {
            importText("  \n\t\n  ")
            fail("Expected empty error")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.Empty, e.error)
        }
    }

    @Test
    fun titleOnly_noBody_returnsEmptyError() = runTest {
        try {
            importText("# 只有标题")
            fail("Expected empty error")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.Empty, e.error)
        }
    }

    @Test
    fun oversizedByMetadata_returnsTooLarge() = runTest {
        try {
            importText("正文", size = PlainTextScriptImporter.MaxImportBytes.toLong() + 1)
            fail("Expected too-large")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.TooLarge, e.error)
        }
    }

    @Test
    fun oversizedByStreamingRead_returnsTooLarge() = runTest {
        val big = ByteArray(PlainTextScriptImporter.MaxImportBytes + 10) { '字'.code.toByte() }
        try {
            importBytes(big, size = null)
            fail("Expected too-large")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.TooLarge, e.error)
        }
    }

    @Test
    fun unrecognizedEncoding_returnsError() = runTest {
        val bytes = byteArrayOf(0x80.toByte(), 0x81.toByte(), 0x82.toByte())
        try {
            importBytes(bytes, mime = null)
            fail("Expected encoding error")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.UnrecognizedEncoding, e.error)
        }
    }

    @Test
    fun unsupportedMarkdown_returnsSyntaxError() = runTest {
        try {
            importText("- 列表项")
            fail("Expected markdown syntax error")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.UnsupportedMarkdownSyntax, e.error)
        }
    }
}
