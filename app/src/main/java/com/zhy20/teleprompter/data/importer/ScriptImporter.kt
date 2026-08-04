package com.zhy20.teleprompter.data.importer

import java.io.InputStream

/**
 * Unified importer contract. Later Markdown/DOCX importers implement the same interface and
 * register themselves in [ScriptImportManager] without touching navigation or the database.
 */
interface ScriptImporter {
    /** True when this importer accepts the given file metadata (extension and/or MIME type). */
    fun supports(metadata: ImportFileMetadata): Boolean

    /** Parses the stream into an [ImportedScript]. Never writes to the database. */
    suspend fun import(
        metadata: ImportFileMetadata,
        inputStreamProvider: suspend () -> InputStream,
    ): ImportedScript
}
