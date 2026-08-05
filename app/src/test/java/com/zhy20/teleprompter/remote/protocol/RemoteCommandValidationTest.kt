package com.zhy20.teleprompter.remote.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteCommandValidationTest {

    @Test
    fun protocolVersionIsOne() {
        assertEquals(1, RemoteProtocol.VERSION)
    }

    @Test
    fun startPlaybackRequiresScriptId() {
        assertNotNull(RemoteCommand.StartPlayback("c1", " ").validationError())
        assertNotNull(RemoteCommand.StartPlayback("c1", "").validationError())
        assertNull(RemoteCommand.StartPlayback("c1", "abc").validationError())
    }

    @Test
    fun seekDeltaIsRangeChecked() {
        assertNull(RemoteCommand.SeekBy("c1", 0.03f).validationError())
        assertNull(RemoteCommand.SeekBy("c1", -1f).validationError())
        assertNull(RemoteCommand.SeekBy("c1", 1f).validationError())
        assertNotNull(RemoteCommand.SeekBy("c1", 1.5f).validationError())
        assertNotNull(RemoteCommand.SeekBy("c1", -2f).validationError())
        assertNotNull(RemoteCommand.SeekBy("c1", Float.NaN).validationError())
        assertNotNull(RemoteCommand.SeekBy("c1", Float.POSITIVE_INFINITY).validationError())
    }

    @Test
    fun speedDeltaIsRangeChecked() {
        assertNull(RemoteCommand.ChangeSpeed("c1", 0.1f).validationError())
        assertNull(RemoteCommand.ChangeSpeed("c1", 5f).validationError())
        assertNull(RemoteCommand.ChangeSpeed("c1", -5f).validationError())
        assertNotNull(RemoteCommand.ChangeSpeed("c1", 5.1f).validationError())
        assertNotNull(RemoteCommand.ChangeSpeed("c1", Float.NaN).validationError())
    }

    @Test
    fun simpleCommandsAlwaysValidate() {
        assertNull(RemoteCommand.PausePlayback("c1").validationError())
        assertNull(RemoteCommand.ResumeImmediately("c1").validationError())
        assertNull(RemoteCommand.ResumeWithCountdown("c1").validationError())
        assertNull(RemoteCommand.EndPlayback("c1").validationError())
    }
}
