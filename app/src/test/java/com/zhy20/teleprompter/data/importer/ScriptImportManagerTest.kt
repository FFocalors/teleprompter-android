package com.zhy20.teleprompter.data.importer

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ScriptImportManagerTest {
    @Test
    fun supportsAny_acceptsTxtExtensionPlainMimeAndOctetStream() {
        val manager = ScriptImportManager(listOf(PlainTextScriptImporter()))
        assertTrue(manager.supportsAny(ImportFileMetadata("a.txt", null, null)))
        assertTrue(manager.supportsAny(ImportFileMetadata("a", "text/plain", null)))
        assertTrue(manager.supportsAny(ImportFileMetadata("a", "application/octet-stream", null)))
    }

    @Test
    fun supportsAny_rejectsUnsupportedMime() {
        val manager = ScriptImportManager(listOf(PlainTextScriptImporter()))
        assertFalse(manager.supportsAny(ImportFileMetadata("a.pdf", "application/pdf", null)))
        assertFalse(manager.supportsAny(ImportFileMetadata("a", "text/html", null)))
    }

    @Test
    fun oversizedMetadata_isRejectedBeforeReadingStream() = runTest {
        val manager = ScriptImportManager(listOf(PlainTextScriptImporter()))
        try {
            manager.import(
                ImportFileMetadata("big.txt", "text/plain", PlainTextScriptImporter.MaxImportBytes.toLong() + 1),
                { ByteArrayInputStream(ByteArray(0)) },
            )
            fail("Expected too-large exception")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.TooLarge, e.error)
        }
    }

    @Test
    fun unknownImporterError_mapsToUnreadable() = runTest {
        val manager = ScriptImportManager(listOf(ThrowingImporter()))
        try {
            manager.import(
                ImportFileMetadata("a.txt", "text/plain", null),
                { ByteArrayInputStream("x".toByteArray(StandardCharsets.UTF_8)) },
            )
            fail("Expected unreadable exception")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.Unreadable, e.error)
        }
    }

    @Test
    fun importerMappedError_isPropagated() = runTest {
        val manager = ScriptImportManager(listOf(ThrowingImporter(ScriptImportException(ScriptImportError.Corrupt))))
        try {
            manager.import(
                ImportFileMetadata("a.txt", "text/plain", null),
                { ByteArrayInputStream("x".toByteArray(StandardCharsets.UTF_8)) },
            )
            fail("Expected corrupt exception")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.Corrupt, e.error)
        }
    }

    private class ThrowingImporter(
        private val thrown: ScriptImportException = ScriptImportException(ScriptImportError.Unreadable),
    ) : ScriptImporter {
        override fun supports(metadata: ImportFileMetadata): Boolean = true
        override suspend fun import(metadata: ImportFileMetadata, inputStreamProvider: suspend () -> java.io.InputStream): ImportedScript {
            throw thrown
        }
    }
}
