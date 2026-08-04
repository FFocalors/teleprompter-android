package com.zhy20.teleprompter.data.serialization

import com.zhy20.teleprompter.core.model.CountdownOption
import com.zhy20.teleprompter.core.model.GuideMode
import com.zhy20.teleprompter.core.model.PlaybackOrientation
import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.PlaybackTextAlignment
import com.zhy20.teleprompter.core.model.RhythmMode
import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptSpan
import com.zhy20.teleprompter.core.model.ScriptSpanStyle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializersTest {
    @Test
    fun document_roundTripsParagraphsAndCombinedStyles() {
        val document = ScriptContent(
            listOf(
                ScriptBlock.Paragraph("first", listOf(ScriptSpan("普通"))),
                ScriptBlock.Paragraph(
                    "second",
                    listOf(
                        ScriptSpan("粗体", setOf(ScriptSpanStyle.Bold)),
                        ScriptSpan("组合", setOf(ScriptSpanStyle.Italic, ScriptSpanStyle.Underline)),
                    ),
                ),
            ),
        )

        val json = ScriptDocumentSerializer.encode(document)
        val restored = ScriptDocumentSerializer.decode(json)

        assertEquals(ScriptDocumentSerializer.SchemaVersion, JSONObject(json).getInt("schemaVersion"))
        assertEquals(document, restored)
    }

    @Test
    fun document_emptyAndInvalidJsonSafelyBecomeAnEmptyDocument() {
        val empty = ScriptDocumentSerializer.emptyDocument()
        assertEquals(empty, ScriptDocumentSerializer.decode(ScriptDocumentSerializer.encode(empty)))
        assertEquals("", ScriptDocumentSerializer.decode("not-json").plainText())
        assertEquals("", ScriptDocumentSerializer.decode("{\"schemaVersion\":999}").plainText())
    }

    @Test
    fun playbackSettings_roundTripEveryPersistentField() {
        val settings = PlaybackSettings(
            backgroundColor = "#1D3550",
            textColor = "#F5F7FA",
            fontSize = 78,
            orientation = PlaybackOrientation.Portrait,
            textAlignment = PlaybackTextAlignment.End,
            mirrorEnabled = true,
            rhythmMode = RhythmMode.TargetDuration,
            speedMultiplier = 1.4f,
            targetDurationSeconds = 321,
            countdown = CountdownOption.TenSeconds,
            guideMode = GuideMode.Line,
            guideLinePosition = .42f,
            displayPresetId = "deep_blue_white",
        )

        val json = PlaybackSettingsSerializer.encode(settings)
        val restored = PlaybackSettingsSerializer.decode(json)

        assertEquals(PlaybackSettingsSerializer.SchemaVersion, JSONObject(json).getInt("schemaVersion"))
        assertEquals(settings, restored)
    }

    @Test
    fun playbackSettings_invalidJsonUsesProvidedFallback() {
        val fallback = PlaybackSettings(fontSize = 88, guideMode = GuideMode.Off)
        assertEquals(fallback, PlaybackSettingsSerializer.decode("broken", fallback))
        assertTrue(PlaybackSettingsSerializer.decode("{}").fontSize > 0)
    }
}
