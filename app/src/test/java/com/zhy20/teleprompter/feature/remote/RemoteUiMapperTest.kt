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
    fun noRoleShowsRoleSelection() {
        assertEquals(
            RemoteUiSection.RoleSelection,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.Disabled, null, null, false),
        )
    }

    @Test
    fun prompterReadyShowsPrompterReady() {
        assertEquals(
            RemoteUiSection.PrompterReady,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.Ready, null, RemoteRole.Prompter, false),
        )
    }

    @Test
    fun controllerReadyShowsControllerReady() {
        assertEquals(
            RemoteUiSection.ControllerReady,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.Ready, null, RemoteRole.Controller, false),
        )
    }

    @Test
    fun waitingShowsPrompterWaiting() {
        assertEquals(
            RemoteUiSection.PrompterWaiting,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.WaitingForController, null, RemoteRole.Prompter, false),
        )
    }

    @Test
    fun failedShowsConnectionFailed() {
        assertEquals(
            RemoteUiSection.ConnectionFailed,
            RemoteUiMapper.sectionOf(
                RemoteConnectionStatus.Failed(RemoteFailureReason.ProtocolMismatch),
                null,
                RemoteRole.Controller,
                false,
            ),
        )
    }

    @Test
    fun reconnectingShowsConnectionLost() {
        assertEquals(
            RemoteUiSection.ConnectionLost,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.Reconnecting(device), null, RemoteRole.Controller, true),
        )
    }

    @Test
    fun librarySurfaceDoesNotShowPlaybackControls() {
        assertEquals(
            RemoteUiSection.ConnectedWaiting,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.Connected(device), snapshot(RemotePrompterSurface.Library), RemoteRole.Controller, false),
        )
        assertEquals(
            RemoteUiSection.ConnectedWaiting,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.Connected(device), snapshot(RemotePrompterSurface.Editor), RemoteRole.Controller, false),
        )
    }

    @Test
    fun setupSurfaceShowsReady() {
        assertEquals(
            RemoteUiSection.Ready,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.Connected(device), snapshot(RemotePrompterSurface.Setup), RemoteRole.Controller, false),
        )
    }

    @Test
    fun countdownSurfaceShowsCountdown() {
        assertEquals(
            RemoteUiSection.Countdown,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.Connected(device), snapshot(RemotePrompterSurface.Countdown), RemoteRole.Controller, false),
        )
    }

    @Test
    fun playingSurfaceShowsPlaying() {
        assertEquals(
            RemoteUiSection.Playing,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.Connected(device), snapshot(RemotePrompterSurface.Playing), RemoteRole.Controller, false),
        )
    }

    @Test
    fun pausedSurfaceShowsPaused() {
        assertEquals(
            RemoteUiSection.Paused,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.Connected(device), snapshot(RemotePrompterSurface.Paused), RemoteRole.Controller, false),
        )
    }

    @Test
    fun finishedSurfaceShowsFinished() {
        assertEquals(
            RemoteUiSection.Finished,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.Connected(device), snapshot(RemotePrompterSurface.Finished), RemoteRole.Controller, false),
        )
    }

    @Test
    fun connectedWithoutSnapshotShowsWaiting() {
        assertEquals(
            RemoteUiSection.ConnectedWaiting,
            RemoteUiMapper.sectionOf(RemoteConnectionStatus.Connected(device), null, RemoteRole.Controller, false),
        )
    }
}
