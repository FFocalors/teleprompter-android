package com.zhy20.teleprompter.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveIndicatorStateTest {
    @Test
    fun initialAndFirstSave_areNeutralWithoutVisibleStatusText() {
        assertEquals(SaveIconTone.Neutral, SaveState.Initial.toSaveIconPresentation(false).tone)
        assertEquals(SaveIconTone.Neutral, SaveState.Saving.toSaveIconPresentation(false).tone)
    }

    @Test
    fun continuousEdits_keepSuccessToneAfterFirstSuccessfulSave() {
        assertEquals(SaveIconTone.Success, SaveState.Saved.toSaveIconPresentation(true).tone)
        assertEquals(SaveIconTone.Success, SaveState.Saving.toSaveIconPresentation(true).tone)
    }

    @Test
    fun saveFailure_usesErrorToneAndEnablesRetryOnlyThen() {
        val error = SaveState.Error.toSaveIconPresentation(true)

        assertEquals(SaveIconTone.Error, error.tone)
        assertTrue(error.retryEnabled)
        assertFalse(SaveState.Saved.toSaveIconPresentation(true).retryEnabled)
    }
}
