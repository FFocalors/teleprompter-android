package com.zhy20.teleprompter.data.importer

import com.zhy20.teleprompter.core.model.ScriptDocument

/** Metadata read from the system file picker, kept free of Android Uri so it stays JVM-testable. */
data class ImportFileMetadata(
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
)

/** Result of a successful import, before any database write happens. */
data class ImportedScript(
    val suggestedTitle: String,
    val document: ScriptDocument,
)

/**
 * User-facing reasons for a failed import. UI maps each reason to a localized message;
 * [Cancelled] is never shown because cancelling the system picker is a normal no-op.
 */
enum class ScriptImportError {
    UnsupportedType,
    Empty,
    TooLarge,
    Unreadable,
    UnrecognizedEncoding,
    Corrupt,
    SaveFailed,
    Cancelled,
}

/**
 * Raised by importers to carry a user-facing reason to the import coordinator. The coordinator
 * converts it into [ScriptImportState.Error] without exposing exception internals to the UI.
 */
class ScriptImportException(val error: ScriptImportError) : Exception(error.name)

/**
 * Import flow state exposed to the screen. Guards against double submission (only [Idle] may
 * start a new import) and gives the UI a reading/importing indicator and an error to show.
 */
sealed interface ScriptImportState {
    data object Idle : ScriptImportState
    data object Reading : ScriptImportState
    data class Success(val scriptId: String) : ScriptImportState
    data class Error(val reason: ScriptImportError) : ScriptImportState
}
