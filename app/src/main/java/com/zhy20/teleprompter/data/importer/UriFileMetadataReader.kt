package com.zhy20.teleprompter.data.importer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Reads picker-provided file metadata and opens read streams via [ContentResolver]. Deliberately
 * tiny and Android-bound: it only supplies the pure-JVM importer pipeline with what it needs and
 * never parses content itself.
 *
 * [contentResolver] is nullable only so JVM tests can subclass this and override both methods;
 * production construction (in [com.zhy20.teleprompter.app.DefaultAppContainer]) always passes a
 * real resolver.
 */
open class UriFileMetadataReader(private val contentResolver: ContentResolver?) {
    open fun readMetadata(uri: Uri): ImportFileMetadata {
        val cursor = contentResolver?.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )
        var displayName: String? = null
        var size: Long? = null
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) displayName = c.getString(nameIndex)
                if (sizeIndex >= 0 && !c.isNull(sizeIndex)) size = c.getLong(sizeIndex)
            }
        }
        val name = displayName?.takeIf(String::isNotBlank) ?: uri.lastPathSegment.orEmpty()
        return ImportFileMetadata(
            displayName = name,
            mimeType = contentResolver?.getType(uri),
            sizeBytes = size,
        )
    }

    open fun openInputStream(uri: Uri): java.io.InputStream? = runCatching {
        contentResolver?.openInputStream(uri)
    }.getOrNull()
}
