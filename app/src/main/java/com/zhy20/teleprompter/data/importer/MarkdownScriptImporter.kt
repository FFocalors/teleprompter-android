package com.zhy20.teleprompter.data.importer

import com.zhy20.teleprompter.core.model.ScriptDocument
import java.io.InputStream

/**
 * Imports Markdown (`.md` / `.markdown`) into a [ScriptDocument].
 *
 * Text decoding reuses the same pipeline as [PlainTextScriptImporter] (UTF-8, UTF-16, GB18030
 * fallback, BOM stripping) and the same 5 MiB ceiling; parsing then goes through
 * [MarkdownSubsetParser]. The first level-1 heading becomes the suggested title; any Markdown
 * construct outside the subset aborts the import instead of being partially parsed.
 *
 * This importer never writes to the database, navigates or touches the UI.
 */
class MarkdownScriptImporter(
    private val defaultTitle: String = "未命名台本",
) : ScriptImporter {
    private val parser = MarkdownSubsetParser()

    override fun supports(metadata: ImportFileMetadata): Boolean =
        metadata.displayName.endsWith(".md", ignoreCase = true) ||
            metadata.displayName.endsWith(".markdown", ignoreCase = true) ||
            metadata.mimeType == MimeTypeMarkdown ||
            metadata.mimeType == MimeTypeXMarkdown

    override suspend fun import(
        metadata: ImportFileMetadata,
        inputStreamProvider: suspend () -> InputStream,
    ): ImportedScript {
        metadata.sizeBytes?.let { size ->
            if (size > PlainTextScriptImporter.MaxImportBytes) {
                throw ScriptImportException(ScriptImportError.TooLarge)
            }
        }
        val bytes = readLimited(inputStreamProvider())
        val decoded = TextEncodingDetector.detect(bytes)
        val text = when (decoded) {
            is TextEncodingDetector.Result.Decoded -> decoded.text
            TextEncodingDetector.Result.Failed -> throw ScriptImportException(ScriptImportError.UnrecognizedEncoding)
        }
        val parsed = parser.parse(text)
        val title = parsed.title?.takeIf { it.isNotBlank() }
            ?: suggestedTitle(metadata.displayName)
        if (parsed.document.blocks.isEmpty()) {
            // A level-1 heading with no body content is a script with nothing to read.
            throw ScriptImportException(ScriptImportError.Empty)
        }
        return ImportedScript(suggestedTitle = title, document = parsed.document)
    }

    private fun suggestedTitle(displayName: String): String {
        val trimmed = displayName.trim()
        val withoutExtension = if (trimmed.endsWith(MdExtension, ignoreCase = true)) {
            trimmed.dropLast(MdExtension.length)
        } else if (trimmed.endsWith(MarkdownExtension, ignoreCase = true)) {
            trimmed.dropLast(MarkdownExtension.length)
        } else {
            trimmed
        }
        return withoutExtension.ifEmpty { defaultTitle }
    }

    private fun readLimited(input: InputStream): ByteArray = input.use { stream ->
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(ReadChunkSize)
        var total = 0
        while (true) {
            val count = stream.read(chunk)
            if (count == -1) break
            total += count
            if (total > PlainTextScriptImporter.MaxImportBytes) {
                throw ScriptImportException(ScriptImportError.TooLarge)
            }
            buffer.write(chunk, 0, count)
        }
        buffer.toByteArray()
    }

    companion object {
        const val MimeTypeMarkdown = "text/markdown"
        const val MimeTypeXMarkdown = "text/x-markdown"
        const val MdExtension = ".md"
        const val MarkdownExtension = ".markdown"
        private const val ReadChunkSize = 8 * 1024
    }
}
