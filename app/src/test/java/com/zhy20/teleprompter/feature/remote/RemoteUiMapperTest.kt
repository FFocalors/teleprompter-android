package com.zhy20.teleprompter.feature.remote

import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus
import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemoteFailureReason
import com.zhy20.teleprompter.remote.model.RemotePrompterSurface
import com.zhy20.teleprompter.remote.model.RemoteRole
import com.zhy20.teleprompter.remote.model.remotePrompterSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteUiMapperTest {

    private val device = RemoteDeviceInfo("c", "phone", RemoteRole.Controller)

    private fun snapshot(surface: RemotePrompterSurface) = remotePrompterSnapshot(
        revision = 1,
        surface = surface,
        scriptId = "1",
        scriptTitle = "台本",
    )

    @Test
    fun disabledShowsDisconnectedSection() {
        assertEquals(
            RemoteUiSection.Disconnected,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.Disabled, null),
        )
    }

    @Test
    fun waitingShowsWaitingSection() {
        assertEquals(
            RemoteUiSection.Waiting,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.WaitingForController, null),
        )
        assertEquals(
            RemoteUiSection.Waiting,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.Connecting, null),
        )
    }

    @Test
    fun failedShowsConnectionFailedSection() {
        assertEquals(
            RemoteUiSection.ConnectionFailed,
            RemoteUiMapper.sectionOf(
                RemoteConnectionStatus.Failed(RemoteFailureReason.ProtocolMismatch),
                null,
            ),
        )
    }

    @Test
    fun reconnectingShowsConnectionLostSection() {
        assertEquals(
            RemoteUiSection.ConnectionLost,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.Reconnecting(device), null),
        )
    }

    @Test
    fun librarySurfaceDoesNotShowPlaybackControls() {
        assertEquals(
            RemoteUiSection.ConnectedWaiting,
            RemoteUiMapper.sectionOf(
                RemoteConnectionStatus.Connected(device),
                snapshot(RemotePrompterSurface.Library),
            ),
        )
        assertEquals(
            RemoteUiSection.ConnectedWaiting,
            RemoteUiMapper.sectionOf(
                RemoteConnectionStatus.Connected(device),
                snapshot(RemotePrompterSurface.Editor),
            ),
        )
    }

    @Test
    fun setupSurfaceShowsReady() {
        assertEquals(
            RemoteUiSection.Ready,
            RemoteUiMapper.sectionOf(
                RemoteConnectionStatus.Connected(device),
                snapshot(RemotePrompterSurface.Setup),
            ),
        )
    }

    @Test
    fun countdownSurfaceShowsCountdown() {
        assertEquals(
            RemoteUiSection.Countdown,
            RemoteUiMapper.sectionOf(
                RemoteConnectionStatus.Connected(device),
                snapshot(RemotePrompterSurface.Countdown),
            ),
        )
    }

    @Test
    fun playingSurfaceShowsPlaying() {
        assertEquals(
            RemoteUiSection.Playing,
            RemoteUiMapper.sectionOf(
                RemoteConnectionStatus.Connected(device),
                snapshot(RemotePrompterSurface.Playing),
            ),
        )
    }

    @Test
    fun pausedSurfaceShowsPaused() {
        assertEquals(
            RemoteUiSection.Paused,
            RemoteUiMapper.sectionOf(
                RemoteConnectionStatus.Connected(device),
                snapshot(RemotePrompterSurface.Paused),
            ),
        )
    }

    @Test
    fun finishedSurfaceShowsFinished() {
        assertEquals(
            RemoteUiSection.Finished,
            RemoteUiMapper.sectionOf(
                RemoteConnectionStatus.Connected(device),
                snapshot(RemotePrompterSurface.Finished),
            ),
        )
    }

    @Test
    fun connectedWithoutSnapshotShowsWaiting() {
        assertEquals(
            RemoteUiSection.ConnectedWaiting,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.Connected(device), null),
        )
    }
}
