package com.zhy20.teleprompter.core.design

import androidx.compose.ui.text.style.TextAlign
import com.zhy20.teleprompter.core.model.PlaybackTextAlignment
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTextAlignmentTest {
    @Test
    fun everyDomainAlignment_mapsToTheMatchingComposeAlignment() {
        assertEquals(TextAlign.Start, PlaybackTextAlignment.Start.toComposeTextAlign())
        assertEquals(TextAlign.Center, PlaybackTextAlignment.Center.toComposeTextAlign())
        assertEquals(TextAlign.End, PlaybackTextAlignment.End.toComposeTextAlign())
    }
}
