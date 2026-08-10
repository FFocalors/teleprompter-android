package com.zhy20.teleprompter.feature.remote

import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus
import com.zhy20.teleprompter.remote.model.RemoteFailureReason
import com.zhy20.teleprompter.remote.model.RemoteRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePanelRenderStateTest {
    @Test
    fun `failed outgoing panel retains failed status after retry starts connecting`() {
        val failedPanel = remotePanelRenderState(
            state = RemoteUiState(
                status = RemoteConnectionStatus.Failed(RemoteFailureReason.HandshakeFailed),
                role = RemoteRole.Controller,
            ),
            scanError = null,
        )
        val connectingPanel = remotePanelRenderState(
            state = RemoteUiState(
                status = RemoteConnectionStatus.Connecting,
                role = RemoteRole.Controller,
            ),
            scanError = null,
        )

        assertEquals(RemoteUiSection.ConnectionFailed, failedPanel.section)
        assertTrue(failedPanel.uiState.status is RemoteConnectionStatus.Failed)
        assertEquals(RemoteUiSection.Connecting, connectingPanel.section)
        assertTrue(connectingPanel.uiState.status is RemoteConnectionStatus.Connecting)
    }

    @Test
    fun `rapid reconnect transitions each keep their own status snapshot`() {
        val lostPanel = remotePanelRenderState(
            state = RemoteUiState(
                status = RemoteConnectionStatus.Reconnecting(device = null),
                role = RemoteRole.Controller,
                reconnecting = true,
            ),
            scanError = null,
        )
        val connectingPanel = remotePanelRenderState(
            state = RemoteUiState(
                status = RemoteConnectionStatus.Connecting,
                role = RemoteRole.Controller,
            ),
            scanError = null,
        )

        assertEquals(RemoteUiSection.ConnectionLost, lostPanel.section)
        assertTrue(lostPanel.uiState.status is RemoteConnectionStatus.Reconnecting)
        assertEquals(RemoteUiSection.Connecting, connectingPanel.section)
    }
}
