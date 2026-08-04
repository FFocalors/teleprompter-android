package com.zhy20.teleprompter.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhy20.teleprompter.core.model.ChineseSpeechDurationEstimator
import com.zhy20.teleprompter.core.model.CountdownOption
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptDocument
import com.zhy20.teleprompter.core.model.ScriptSpan
import com.zhy20.teleprompter.data.repository.FolderNotFoundException
import com.zhy20.teleprompter.data.repository.GlobalSettings
import com.zhy20.teleprompter.data.repository.RoomScriptRepository
import com.zhy20.teleprompter.data.repository.SettingsRepository
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
class ScriptImportRepositoryTest {
    private lateinit var database: TeleprompterDatabase
    private lateinit var scripts: RoomScriptRepository

    private val folderDefaults = PlaybackSettings(
        fontSize = 78,
        countdown = CountdownOption.FiveSeconds,
        guideLinePosition = 0.4f,
    )

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TeleprompterDatabase::class.java).build()
        val settings = ImportTestSettingsRepository(folderDefaults)
        scripts = RoomScriptRepository(
            database.scriptDao(),
            database.scriptFolderDao(),
            settings,
            defaultTitle = { "未命名台本" },
            clockMillis = object : () -> Long { var now = 1_000L; override fun invoke() = now++ },
            newId = object : () -> String { var next = 0; override fun invoke() = "imported-${next++}" },
        )
    }

    @After
    fun closeDatabase() = database.close()

    private fun document(vararg paragraphs: List<ScriptSpan>): ScriptDocument = ScriptContent(
        paragraphs.mapIndexed { index, spans -> ScriptBlock.Paragraph("p$index", spans) },
    )

    @Test
    fun createFromDocument_savesTitleAndDocument() = runBlocking {
        val doc = document(listOf(ScriptSpan("大家好，"), ScriptSpan("欢迎。")))
        val script = scripts.createFromDocument("欢迎词", doc, folderId = null)

        val stored = scripts.getById(script.id)!!
        assertEquals("欢迎词", stored.title)
        assertEquals(doc, stored.content)
    }

    @Test
    fun createFromDocument_derivesPlainText() = runBlocking {
        val doc = document(listOf(ScriptSpan("第一段")), listOf(ScriptSpan("第二段")))
        val script = scripts.createFromDocument("标题", doc, null)

        assertEquals("第一段\n\n第二段", scripts.getById(script.id)!!.plainTextPreview)
        // The entity stores the full plain text; the model exposes a truncated preview.
        assertEquals("第一段\n\n第二段", scripts.getById(script.id)!!.content.plainText())
    }

    @Test
    fun createFromDocument_computesWordCount() = runBlocking {
        val doc = document(listOf(ScriptSpan("12 中文 34")))
        val script = scripts.createFromDocument("字数", doc, null)

        // Non-whitespace characters: "12", "中文", "34" => 2 + 2 + 2 = 6
        assertEquals(6, scripts.getById(script.id)!!.wordCount)
    }

    @Test
    fun createFromDocument_computesDurationEstimate() = runBlocking {
        val doc = document(listOf(ScriptSpan("字".repeat(850))))
        val script = scripts.createFromDocument("时长", doc, null)

        val expected = ChineseSpeechDurationEstimator.estimate(doc)
        assertEquals(expected, scripts.getById(script.id)!!.normalEstimatedDurationSeconds)
    }

    @Test
    fun createFromDocument_copiesGlobalPlaybackDefaults() = runBlocking {
        val doc = document(listOf(ScriptSpan("正文")))
        val script = scripts.createFromDocument("设置", doc, null)

        assertEquals(folderDefaults, scripts.getById(script.id)!!.playbackSettings)
    }

    @Test
    fun createFromDocument_savesFolderId() = runBlocking {
        val folderId = database.scriptFolderDao().insertFolder("拍摄")
        val doc = document(listOf(ScriptSpan("正文")))
        val script = scripts.createFromDocument("入夹", doc, folderId)

        assertEquals(folderId, scripts.getById(script.id)!!.folderId)
    }

    @Test
    fun createFromDocument_invalidFolder_failsWithoutCreatingScript() = runBlocking {
        assertThrows(FolderNotFoundException::class.java) {
            runBlocking { scripts.createFromDocument("标题", document(listOf(ScriptSpan("正文"))), "missing") }
        }
        assertNull(scripts.getById("imported-0"))
    }

    @Test
    fun createFromDocument_blankTitle_usesDefaultTitle() = runBlocking {
        val script = scripts.createFromDocument("   ", document(listOf(ScriptSpan("正文"))), null)
        assertEquals("未命名台本", scripts.getById(script.id)!!.title)
    }

    @Test
    fun createFromDocument_trimsTitle() = runBlocking {
        val script = scripts.createFromDocument("  标题  ", document(listOf(ScriptSpan("正文"))), null)
        assertEquals("标题", scripts.getById(script.id)!!.title)
    }

    @Test
    fun createFromDocument_createdAndUpdatedAtMatch() = runBlocking {
        val script = scripts.createFromDocument("时间", document(listOf(ScriptSpan("正文"))), null)
        val entity = database.scriptDao().getById(script.id)!!
        assertEquals(entity.createdAt, entity.updatedAt)
    }
}

private suspend fun com.zhy20.teleprompter.data.local.dao.ScriptFolderDao.insertFolder(name: String): String {
    val entity = com.zhy20.teleprompter.data.local.entity.ScriptFolderEntity(
        id = "folder-$name",
        name = name,
        createdAt = 1L,
        updatedAt = 1L,
        sortOrder = 0,
    )
    insert(entity)
    return entity.id
}

private class ImportTestSettingsRepository(defaults: PlaybackSettings) : SettingsRepository {
    private val state = MutableStateFlow(GlobalSettings(playbackDefaults = defaults))
    override val settings: Flow<GlobalSettings> = state
    override suspend fun updatePlaybackDefaults(settings: PlaybackSettings) { state.value = state.value.copy(playbackDefaults = settings) }
    override suspend fun updateLanguage(languageTag: String) { state.value = state.value.copy(languageTag = languageTag) }
}
