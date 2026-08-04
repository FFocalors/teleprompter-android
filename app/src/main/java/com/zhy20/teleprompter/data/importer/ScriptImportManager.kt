package com.zhy20.teleprompter.data.importer

import java.io.InputStream
import kotlinx.coroutines.CancellationException

/**
 * Chooses the right [ScriptImporter] for a file, enforces the size limit, maps failures to
 * user-facing [ScriptImportError] values, and never writes to the database.
 *
 * The coordinator layer (ViewModel) is responsible for opening the stream and persisting a
 * successful result; this manager is deliberately UI- and database-free.
 */
class ScriptImportManager(
    private val importers: List<ScriptImporter>,
) {
    constructor() : this(defaultImporters())

    fun supportsAny(metadata: ImportFileMetadata): Boolean = importers.any { it.supports(metadata) }

    suspend fun import(
        metadata: ImportFileMetadata,
        inputStreamProvider: suspend () -> InputStream,
    ): ImportedScript {
        // Hard universal ceiling before any importer runs. Each importer enforces its own stricter
        // limit (TXT: 5 MiB; DOC/DOCX: 20 MiB) inside its own read path.
        if (metadata.sizeBytes != null && metadata.sizeBytes > WordImportLimits.MAX_SOURCE_FILE_BYTES) {
            throw ScriptImportException(ScriptImportError.TooLarge)
        }
        val importer = importers.firstOrNull { it.supports(metadata) }
            ?: throw ScriptImportException(ScriptImportError.UnsupportedType)
        return try {
            importer.import(metadata, inputStreamProvider)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (alreadyMapped: ScriptImportException) {
            throw alreadyMapped
        } catch (_: Exception) {
            throw ScriptImportException(ScriptImportError.Unreadable)
        }
    }

    private companion object {
        fun defaultImporters(): List<ScriptImporter> =
            listOf(
                DocxScriptImporter(),
                DocScriptImporter(),
                MarkdownScriptImporter(),
                PlainTextScriptImporter(),
            )
    }
}
