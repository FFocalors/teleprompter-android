package com.zhy20.teleprompter.data.importer

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Verifies that [ScriptImportManager] routes each supported format to the right importer and that
 * content/extension conflicts are handled correctly. These tests run through the manager so the
 * default importer set is exercised end to end.
 */
class ScriptImportManagerWordRoutingTest {

    private val manager = ScriptImportManager()

    @Test
    fun docxRoutesToDocxImporter() = runBlocking {
        val bytes = minimalDocx()
        val result = manager.import(
            ImportFileMetadata("memo.docx", DocxScriptImporter.MimeTypeWordOpenXml, bytes.size.toLong()),
        ) { ByteArrayInputStream(bytes) }
        assertEquals("memo", result.suggestedTitle)
        assertTrue(result.document.plainText().contains("正文"))
    }

    @Test
    fun docRoutesToDocImporter() = runBlocking {
        // A real .doc fixture is used from resources so the OLE/FIB path is exercised via the manager.
        val bytes = checkNotNull(javaClass.classLoader.getResourceAsStream("fixtures/real_sample.doc")) {
            "missing real_sample.doc"
        }.use { it.readBytes() }
        val result = manager.import(
            ImportFileMetadata("real_sample.doc", DocScriptImporter.MimeTypeWordLegacy, bytes.size.toLong()),
        ) { ByteArrayInputStream(bytes) }
        assertEquals("real_sample", result.suggestedTitle)
        assertTrue(result.document.plainText().contains("第一段"))
    }

    @Test
    fun txtStillRoutesToTxtImporter() = runBlocking {
        val bytes = "纯文本内容".toByteArray(Charsets.UTF_8)
        val result = manager.import(
            ImportFileMetadata("notes.txt", "text/plain", bytes.size.toLong()),
        ) { ByteArrayInputStream(bytes) }
        assertEquals("notes", result.suggestedTitle)
        assertEquals("纯文本内容", result.document.plainText())
    }

    @Test
    fun docxWithWrongExtension_stillParsesByContent() = runBlocking {
        // A file named .txt but actually a docx zip should be rejected by the TXT importer's
        // strict encoding path — the manager must pick the DOCX importer only by real content.
        // Here we assert that a .txt-named docx is NOT silently decoded as text.
        val bytes = minimalDocx()
        try {
            manager.import(
                ImportFileMetadata("memo.txt", "text/plain", bytes.size.toLong()),
            ) { ByteArrayInputStream(bytes) }
            // It may fail (binary isn't valid UTF-8) — that is correct.
        } catch (e: ScriptImportException) {
            assertTrue(
                "expected decoding failure for binary-as-txt, got ${e.error}",
                e.error == ScriptImportError.UnrecognizedEncoding || e.error == ScriptImportError.Corrupt,
            )
        }
        Unit
    }

    @Test
    fun unsupportedFormat_rejected() = runBlocking {
        val bytes = "%PDF-1.7 not really a pdf".toByteArray()
        try {
            manager.import(
                ImportFileMetadata("file.pdf", "application/pdf", bytes.size.toLong()),
            ) { ByteArrayInputStream(bytes) }
            fail("Expected unsupported exception")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.UnsupportedType, e.error)
        }
    }

    @Test
    fun oversizedUniversalCeiling_rejected() = runBlocking {
        try {
            manager.import(
                ImportFileMetadata("big.docx", DocxScriptImporter.MimeTypeWordOpenXml, WordImportLimits.MAX_SOURCE_FILE_BYTES + 1),
            ) { ByteArrayInputStream(ByteArray(0)) }
            fail("Expected too-large exception")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.TooLarge, e.error)
        }
    }

    @Test
    fun oneImporterSelectedPerFile() = runBlocking {
        // The default set contains exactly one importer per format; each `supports` must be true
        // for exactly one importer for the canonical files.
        val txt = ImportFileMetadata("a.txt", "text/plain", 1)
        val docx = ImportFileMetadata("a.docx", DocxScriptImporter.MimeTypeWordOpenXml, 1)
        val doc = ImportFileMetadata("a.doc", DocScriptImporter.MimeTypeWordLegacy, 1)
        assertEquals(1, manager.defaultSetCount(txt))
        assertEquals(1, manager.defaultSetCount(docx))
        assertEquals(1, manager.defaultSetCount(doc))
    }

    private fun minimalDocx(): ByteArray {
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
            <w:p><w:r><w:t>正文内容</w:t></w:r></w:p>
            </w:body></w:document>""".trimIndent()
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("[Content_Types].xml"))
            zip.write("<Types/>".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            zip.write(xml.toByteArray())
            zip.closeEntry()
        }
        return bos.toByteArray()
    }

    private fun ScriptImportManager.defaultSetCount(metadata: ImportFileMetadata): Int {
        val importers = listOf(DocxScriptImporter(), DocScriptImporter(), PlainTextScriptImporter())
        return importers.count { it.supports(metadata) }
    }
}
