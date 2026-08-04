package com.zhy20.teleprompter.data.importer

import com.zhy20.teleprompter.data.repository.DataOperationException
import com.zhy20.teleprompter.data.repository.ScriptRepository
import kotlinx.coroutines.CancellationException

/**
 * Coordinates one import flow end to end: metadata → manager → repository. Runs entirely on the
 * caller's [kotlinx.coroutines.Dispatchers.IO] because it blocks on stream reads and DB writes.
 * It never touches the UI; the ViewModel maps [ScriptImportState] for the screen.
 *
 * Kept a plain class (not a ViewModel) so the whole flow is unit-testable with fakes and the
 * ViewModel only delegates, matching the project's preference for hand-written test doubles.
 */
class ScriptImportCoordinator(
    private val manager: ScriptImportManager,
    private val repository: ScriptRepository,
) {
    /** Returns the new script id, or null when the operation was cancelled. */
    suspend fun importFile(
        metadata: ImportFileMetadata,
        inputStreamProvider: suspend () -> java.io.InputStream,
        folderId: String?,
    ): String? = try {
        val imported = manager.import(metadata, inputStreamProvider)
        repository.createFromDocument(imported.suggestedTitle, imported.document, folderId).id
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (alreadyMapped: ScriptImportException) {
        throw alreadyMapped
    } catch (dataError: DataOperationException) {
        throw ScriptImportException(ScriptImportError.SaveFailed)
    } catch (_: Exception) {
        throw ScriptImportException(ScriptImportError.SaveFailed)
    }
}
