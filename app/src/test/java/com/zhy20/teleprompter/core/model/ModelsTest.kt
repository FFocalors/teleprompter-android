package com.zhy20.teleprompter.core.model

import com.zhy20.teleprompter.data.fake.FakeData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun scriptContent_preservesParagraphsAndBoldSpans() {
        val content = ScriptContent(
            listOf(
                ScriptBlock.Paragraph("one", listOf(ScriptSpan("普通"), ScriptSpan("加粗", bold = true))),
                ScriptBlock.Paragraph("two", listOf(ScriptSpan("下一段"))),
            ),
        )

        assertEquals("普通加粗\n\n下一段", content.plainText())
        assertTrue((content.blocks.first() as ScriptBlock.Paragraph).spans.last().bold)
    }

    @Test
    fun playbackDefaults_areSafeForPrompterUi() {
        val settings = PlaybackSettings()

        assertEquals(PlaybackOrientation.Landscape, settings.orientation)
        assertEquals(CountdownOption.ThreeSeconds, settings.countdown)
        assertEquals(GuideLineStyle.Highlight, settings.guideLineStyle)
        assertTrue(settings.guideLinePosition in 0.15f..0.75f)
    }

    @Test
    fun fakeFolders_areSingleLevelAndCountsMatchScripts() {
        FakeData.folders.forEach { folder ->
            assertEquals(folder.scriptCount, FakeData.scripts.count { it.folderId == folder.id })
        }
    }
}
