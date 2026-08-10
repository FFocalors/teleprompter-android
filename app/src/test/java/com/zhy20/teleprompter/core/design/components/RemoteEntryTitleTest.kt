package com.zhy20.teleprompter.core.design.components

import com.zhy20.teleprompter.R
import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus
import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemoteRole
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteEntryTitleTest {
    private val connected = RemoteConnectionStatus.Connected(
        RemoteDeviceInfo(
            deviceId = "peer",
            displayName = "Peer",
            role = RemoteRole.Controller,
        ),
    )

    @Test
    fun `disconnected entry is role neutral`() {
        assertEquals(
            R.string.device_remote,
            remoteEntryTitleRes(RemoteConnectionStatus.Disabled, RemoteRole.Controller),
        )
        assertEquals(
            R.string.device_remote,
            remoteEntryTitleRes(RemoteConnectionStatus.WaitingForController, RemoteRole.Prompter),
        )
    }

    @Test
    fun `connected entry follows local role`() {
        assertEquals(R.string.role_prompter_label, remoteEntryTitleRes(connected, RemoteRole.Prompter))
        assertEquals(R.string.role_controller_label, remoteEntryTitleRes(connected, RemoteRole.Controller))
    }

    @Test
    fun `connected entry without local role stays neutral`() {
        assertEquals(R.string.device_remote, remoteEntryTitleRes(connected, null))
    }
}
