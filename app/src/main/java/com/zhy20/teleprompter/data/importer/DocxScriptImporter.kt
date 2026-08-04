package com.zhy20.teleprompter.data.importer

import com.zhy20.teleprompter.core.model.ScriptDocument
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Imports Word OpenXML (.docx) documents into a [ScriptDocument].
 *
 * A .docx is a ZIP package; this importer reads only `word/document.xml` and walks it with the
 * platform pull parser, so it needs no third-party dependency. It deliberately ignores images,
 * headers/footers, comments, footnotes, text boxes, math, SmartArt and page layout, and it maps
 * only bold/italic/underline run styles to the shared model.
 *
 * Security: the ZIP is read streaming with an uncompressed-size budget ([WordImportLimits]), the
 * XML parser disables external entities and DTDs, and every structural counter is capped. The
 * importer never writes to the database, navigates or touches the UI.
 */
class DocxScriptImporter(
    private val defaultTitle: String = "未命名台本",
) : ScriptImporter {

    override fun supports(metadata: ImportFileMetadata): Boolean =
        metadata.displayName.endsWith(".docx", ignoreCase = true) ||
            metadata.mimeType == MimeTypeWordOpenXml

    override suspend fun import(
        metadata: ImportFileMetadata,
        inputStreamProvider: suspend () -> InputStream,
    ): ImportedScript {
        metadata.sizeBytes?.let { size ->
            if (size > WordImportLimits.MAX_SOURCE_FILE_BYTES) {
                throw ScriptImportException(ScriptImportError.TooLarge)
            }
        }
        val document = parse(inputStreamProvider())
        return ImportedScript(
            suggestedTitle = suggestedTitle(metadata.displayName),
            document = document,
        )
    }

    /** Parses a DOCX package from [input], enforcing size, entry and structural limits. */
    internal fun parse(input: InputStream): ScriptDocument {
        var totalUncompressed = 0L
        var entries = 0
        var documentXml: ByteArray? = null
        input.use { zipStream ->
            val zip = ZipInputStream(zipStream)
            while (true) {
                val entry = zip.nextEntry ?: break
                entries += 1
                if (entries > WordImportLimits.MAX_ZIP_ENTRIES) {
                    throw ScriptImportException(ScriptImportError.TooComplex)
                }
                val size = entry.size
                if (size > WordImportLimits.MAX_ENTRY_BYTES) {
                    throw ScriptImportException(ScriptImportError.TooComplex)
                }
                if (nameIsMainDocument(entry.name)) {
                    documentXml = readEntryLimited(zip, entry)
                } else if (nameIsContentTypes(entry.name)) {
                    // Presence of [Content_Types].xml is the strongest package marker.
                    // We do not need its content; a docx always has it.
                } else {
                    // Consume the entry's data (ZipInputStream must advance) and count it.
                    drain(zip, entry)
                }
                totalUncompressed += entry.size.coerceAtLeast(0L)
                if (totalUncompressed > WordImportLimits.MAX_UNCOMPRESSED_BYTES) {
                    throw ScriptImportException(ScriptImportError.TooComplex)
                }
            }
        }
        val xml = documentXml
            ?: throw ScriptImportException(ScriptImportError.Corrupt)
        return parseDocumentXml(xml)
    }

    private fun nameIsMainDocument(name: String): Boolean =
        name == "word/document.xml" || name.startsWith("word/document") && name.endsWith(".xml")

    private fun nameIsContentTypes(name: String): Boolean = name == "[Content_Types].xml"

    /** Reads one ZIP entry with a byte cap; returns null when the entry is too large. */
    private fun readEntryLimited(zip: ZipInputStream, entry: ZipEntry): ByteArray? {
        if (entry.size > WordImportLimits.MAX_ENTRY_BYTES) {
            throw ScriptImportException(ScriptImportError.TooComplex)
        }
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(READ_CHUNK)
        var total = 0
        while (true) {
            val count = zip.read(chunk)
            if (count == -1) break
            total += count
            if (total > WordImportLimits.MAX_ENTRY_BYTES) {
                throw ScriptImportException(ScriptImportError.TooComplex)
            }
            buffer.write(chunk, 0, count)
        }
        return buffer.toByteArray()
    }

    /** Advances a ZIP entry we are not interested in, counting its uncompressed size. */
    private fun drain(zip: ZipInputStream, entry: ZipEntry) {
        val chunk = ByteArray(READ_CHUNK)
        var total = 0L
        while (true) {
            val count = zip.read(chunk)
            if (count == -1) break
            total += count
            if (total > WordImportLimits.MAX_UNCOMPRESSED_BYTES) {
                throw ScriptImportException(ScriptImportError.TooComplex)
            }
        }
    }

    private fun parseDocumentXml(xml: ByteArray): ScriptDocument {
        val parser = WordXmlPull.new()
        parser.setInput(java.io.ByteArrayInputStream(xml), "UTF-8")
        return DocxBodyParser().parse(parser)
    }

    private fun suggestedTitle(displayName: String): String {
        val trimmed = displayName.trim()
        val withoutExtension = if (trimmed.endsWith(DocxExtension, ignoreCase = true)) {
            trimmed.dropLast(DocxExtension.length)
        } else {
            trimmed
        }
        return withoutExtension.ifEmpty { defaultTitle }
    }

    companion object {
        const val MimeTypeWordOpenXml =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val DocxExtension = ".docx"
        private const val READ_CHUNK = 8 * 1024
    }
}
