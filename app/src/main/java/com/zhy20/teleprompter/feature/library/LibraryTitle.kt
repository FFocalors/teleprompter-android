package com.zhy20.teleprompter.feature.library

import androidx.annotation.StringRes
import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.core.model.ScriptFolder

/**
 * Derived title for the library header, based on the currently selected folder.
 *
 * The rule is shared by the phone top app bar and the wide-screen content header so both
 * layouts always show the same title. The title is derived directly from [selectedFolder]
 * and [folders] — no separate title state is kept, and a stale/deleted folder falls back to
 * "all scripts".
 *
 * @return the resolved title as either a string resource id (built-in views) or the folder
 *   name (a real custom folder), mirroring how the phone and wide layouts each render it.
 */
internal sealed interface LibraryTitle {
    /** Non-folder title — resolved via a string resource (all scripts / uncategorized / fallback). */
    data class Resource(@StringRes val id: Int) : LibraryTitle

    /** Custom folder name. */
    data class Folder(val name: String) : LibraryTitle
}

internal const val UNCATEGORIZED_FOLDER_ID: String = "uncategorized"

/**
 * Derives the current library title from [selectedFolder] and [folders]:
 * - `null` → all scripts
 * - "uncategorized" → uncategorized
 * - a folder id → that folder's name (falls back to all scripts if it no longer exists)
 */
internal fun libraryTitle(selectedFolder: String?, folders: List<ScriptFolder>): LibraryTitle =
    when (selectedFolder) {
        null -> LibraryTitle.Resource(R.string.all_scripts)
        UNCATEGORIZED_FOLDER_ID -> LibraryTitle.Resource(R.string.uncategorized)
        else -> folders.firstOrNull { it.id == selectedFolder }?.let { LibraryTitle.Folder(it.name) }
            ?: LibraryTitle.Resource(R.string.all_scripts)
    }
