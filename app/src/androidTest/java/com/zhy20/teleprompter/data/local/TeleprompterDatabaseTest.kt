package com.zhy20.teleprompter.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptSpan
import com.zhy20.teleprompter.core.model.ScriptSpanStyle
import com.zhy20.teleprompter.data.repository.FolderNameConflictException
import com.zhy20.teleprompter.data.repository.GlobalSettings
import com.zhy20.teleprompter.data.repository.RoomScriptFolderRepository
import com.zhy20.teleprompter.data.repository.RoomScriptRepository
import com.zhy20.teleprompter.data.repository.SettingsRepository
import java.io.Closeable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TeleprompterDatabaseTest {
    private lateinit var database: TeleprompterDatabase
    private lateinit var scripts: RoomScriptRepository
    private lateinit var folders: RoomScriptFolderRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TeleprompterDatabase::class.java).build()
        val settings = TestSettingsRepository(PlaybackSettings(fontSize = 72))
        scripts = RoomScriptRepository(
            database.scriptDao(),
            database.scriptFolderDao(),
            settings,
            defaultTitle = { "未命名台本" },
            clockMillis = object : () -> Long { var now = 1L; override fun invoke() = now++ },
            newId = object : () -> String { var next = 0; override fun invoke() = "id-${next++}" },
        )
        folders = RoomScriptFolderRepository(
            database,
            database.scriptFolderDao(),
            database.scriptDao(),
            clockMillis = object : () -> Long { var now = 100L; override fun invoke() = now++ },
            newId = object : () -> String { var next = 0; override fun invoke() = "folder-${next++}" },
        )
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun scriptAndFolderCrud_flowSortingAndDeleteFolderTransaction() = runBlocking {
        val folder = folders.create("拍摄")
        val first = scripts.create(folder.id)
        val second = scripts.create(null)
        assertEquals(72, first.playbackSettings.fontSize)
        scripts.updateTitle(first.id, "甲台本")
        scripts.updateDocument(
            first.id,
            ScriptContent(listOf(ScriptBlock.Paragraph("p", listOf(ScriptSpan("真实正文"))))),
        )
        scripts.move(second.id, folder.id)

        assertEquals(listOf(first.id, second.id).toSet(), scripts.observeInFolder(folder.id).first().map { it.id }.toSet())
        assertEquals("真实正文", scripts.getById(first.id)?.content?.plainText())
        assertEquals(4, scripts.getById(first.id)?.wordCount)
        val durationBeforeStyleChange = scripts.getById(first.id)!!.normalEstimatedDurationSeconds
        scripts.updateDocument(
            first.id,
            ScriptContent(
                listOf(
                    ScriptBlock.Paragraph(
                        "p",
                        listOf(ScriptSpan("真实正文", setOf(ScriptSpanStyle.Bold, ScriptSpanStyle.Underline))),
                    ),
                ),
            ),
        )
        assertEquals(durationBeforeStyleChange, scripts.getById(first.id)?.normalEstimatedDurationSeconds)

        folders.rename(folder.id, "已重命名")
        assertEquals("已重命名", folders.observeAll().first().single().name)
        assertThrows(FolderNameConflictException::class.java) { runBlocking { folders.create("已重命名") } }
        folders.deleteAndUncategorizeScripts(folder.id)

        assertEquals(2, scripts.observeUncategorized().first().size)
        assertNull(database.scriptFolderDao().getById(folder.id))
        scripts.delete(first.id)
        assertNull(scripts.getById(first.id))
    }
}

private class TestSettingsRepository(defaults: PlaybackSettings) : SettingsRepository {
    private val state = MutableStateFlow(GlobalSettings(playbackDefaults = defaults))
    override val settings: Flow<GlobalSettings> = state
    override suspend fun updatePlaybackDefaults(settings: PlaybackSettings) { state.value = state.value.copy(playbackDefaults = settings) }
    override suspend fun updateLanguage(languageTag: String) { state.value = state.value.copy(languageTag = languageTag) }
}
