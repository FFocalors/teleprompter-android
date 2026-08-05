package com.zhy20.teleprompter.feature.library

import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.core.model.ScriptFolder
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The library header title must follow the currently selected folder:
 * - null → all scripts
 * - "uncategorized" → uncategorized
 * - a real folder id → that folder's name
 * - a deleted/missing folder → falls back to all scripts
 *
 * Both the phone top app bar and the wide-screen content header derive their title from this same
 * rule, so these tests cover the shared logic.
 */
class LibraryTitleTest {

    private val folders = listOf(
        ScriptFolder("f1", "工作", 1, 2),
        ScriptFolder("f2", "演讲", 2, 1),
    )

    @Test
    fun noSelection_showsAllScripts() {
        assertEquals(LibraryTitle.Resource(R.string.all_scripts), libraryTitle(null, folders))
    }

    @Test
    fun uncategorized_showsUncategorized() {
        assertEquals(
            LibraryTitle.Resource(R.string.uncategorized),
            libraryTitle(UNCATEGORIZED_FOLDER_ID, folders),
        )
    }

    @Test
    fun customFolder_showsFolderName() {
        assertEquals(LibraryTitle.Folder("工作"), libraryTitle("f1", folders))
        assertEquals(LibraryTitle.Folder("演讲"), libraryTitle("f2", folders))
    }

    @Test
    fun renamedFolder_showsNewName() {
        val renamed = listOf(
            ScriptFolder("f1", "新名称", 1, 2),
            ScriptFolder("f2", "演讲", 2, 1),
        )
        assertEquals(LibraryTitle.Folder("新名称"), libraryTitle("f1", renamed))
    }

    @Test
    fun deletedFolder_fallsBackToAllScripts() {
        assertEquals(LibraryTitle.Resource(R.string.all_scripts), libraryTitle("deleted", folders))
    }

    @Test
    fun emptyFolders_fallsBackToAllScripts() {
        assertEquals(LibraryTitle.Resource(R.string.all_scripts), libraryTitle("f1", emptyList()))
    }

    @Test
    fun uncategorizedStillWorks_whenFoldersEmpty() {
        assertEquals(
            LibraryTitle.Resource(R.string.uncategorized),
            libraryTitle(UNCATEGORIZED_FOLDER_ID, emptyList()),
        )
    }

    @Test
    fun allScriptsFallback_matchesPhoneAndWideRule() {
        // The same rule is shared by the phone TopAppBar and the wide LibraryContent header;
        // the fallback for a missing folder must never produce a folder name or crash.
        assertEquals(
            LibraryTitle.Resource(R.string.all_scripts),
            libraryTitle(null, emptyList()),
        )
    }
}
