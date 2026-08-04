package com.zhy20.teleprompter.data.importer

import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptDocument
import com.zhy20.teleprompter.core.model.ScriptSpan
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Imports UTF-8 / UTF-16 / GB18030 plain text into a [ScriptDocument].
 *
 * Only parses text; it never creates Room entities, navigates, or shows UI. Size limits,
 * encoding detection and paragraph normalization happen here so they stay JVM-testable.
 *
 * Paragraph rules follow the shared editor model: consecutive blank lines separate paragraphs,
 * a single line break inside a paragraph is preserved as an in-paragraph line break, and the
 * trailing whitespace of every line is trimmed.
 */
class PlainTextScriptImporter(
    private val defaultTitle: String = "未命名台本",
) : ScriptImporter {
    override fun supports(metadata: ImportFileMetadata): Boolean =
        metadata.displayName.endsWith(".txt", ignoreCase = true) ||
            metadata.mimeType == MimeTypeTextPlain ||
            metadata.mimeType == MimeTypeOctetStream

    override suspend fun import(
        metadata: ImportFileMetadata,
        inputStreamProvider: suspend () -> InputStream,
    ): ImportedScript {
        metadata.sizeBytes?.let { size ->
            if (size > MaxImportBytes) throw ScriptImportException(ScriptImportError.TooLarge)
        }
        val bytes = readLimited(inputStreamProvider())
        val decoded = TextEncodingDetector.detect(bytes)
        val text = when (decoded) {
            is TextEncodingDetector.Result.Decoded -> decoded.text
            TextEncodingDetector.Result.Failed -> throw ScriptImportException(ScriptImportError.UnrecognizedEncoding)
        }
        return ImportedScript(
            suggestedTitle = suggestedTitle(metadata.displayName),
            document = documentFromText(text),
        )
    }

    private fun suggestedTitle(displayName: String): String {
        val trimmed = displayName.trim()
        val withoutExtension = if (trimmed.endsWith(TxtExtension, ignoreCase = true)) {
            trimmed.dropLast(TxtExtension.length)
        } else {
            trimmed
        }
        return withoutExtension.ifEmpty { defaultTitle }
    }

    private fun documentFromText(text: String): ScriptDocument {
        // Normalize Windows (\r\n) and old Mac (\r) line breaks to Unix (\n).
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val paragraphs = mutableListOf<ScriptBlock.Paragraph>()
        var current = mutableListOf<String>()
        normalized.split('\n').forEach { line ->
            if (line.isBlank()) {
                if (current.isNotEmpty()) {
                    paragraphs += buildParagraph(current)
                    current = mutableListOf()
                }
            } else {
                current += line.trimEnd()
            }
        }
        if (current.isNotEmpty()) paragraphs += buildParagraph(current)

        if (paragraphs.isEmpty()) throw ScriptImportException(ScriptImportError.Empty)
        return ScriptContent(paragraphs)
    }

    private fun buildParagraph(lines: List<String>): ScriptBlock.Paragraph = ScriptBlock.Paragraph(
        id = "imported-${paragraphCounter.getAndIncrement()}",
        spans = listOf(ScriptSpan(lines.joinToString("\n"))),
    )

    private fun readLimited(input: InputStream): ByteArray = input.use { stream ->
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(ReadChunkSize)
        var total = 0
        while (true) {
            val count = stream.read(chunk)
            if (count == -1) break
            total += count
            if (total > MaxImportBytes) throw ScriptImportException(ScriptImportError.TooLarge)
            buffer.write(chunk, 0, count)
        }
        buffer.toByteArray()
    }

    private val paragraphCounter = AtomicInteger(0)

    companion object {
        const val MaxImportBytes = 5 * 1024 * 1024
        const val MimeTypeTextPlain = "text/plain"
        const val MimeTypeOctetStream = "application/octet-stream"
        const val TxtExtension = ".txt"
        private const val ReadChunkSize = 8 * 1024
    }
}
