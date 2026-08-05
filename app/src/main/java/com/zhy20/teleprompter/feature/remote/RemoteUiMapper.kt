package com.zhy20.teleprompter.feature.remote

import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus
import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.model.RemotePrompterSurface

/**
 * Which top-level panel the remote screen shows. Kept as a pure, Compose-free mapping so the
 * UI-state rules are unit-testable on the JVM.
 */
enum class RemoteUiSection {
    /** No session active — show the "start waiting" panel. */
    Disconnected,

    /** Waiting/connecting — show the waiting panel. */
    Waiting,

    /** Connection failed with a structured reason. */
    ConnectionFailed,

    /** Link dropped — show the reconnect panel. */
    ConnectionLost,

    /** Connected but no ready prompter surface — show "waiting for setup". */
    ConnectedWaiting,

    /** Prompter is on the setup page — show the current script and start button. */
    Ready,

    Countdown,
    Playing,
    Paused,
    Finished,
}

/** Pure mapping from session state to the rendered section. */
object RemoteUiMapper {
    fun sectionOf(
        status: RemoteConnectionStatus,
        snapshot: RemotePrompterSnapshot?,
    ): RemoteUiSection = when (status) {
        RemoteConnectionStatus.Disabled,
        RemoteConnectionStatus.Ready,
        -> RemoteUiSection.Disconnected

        RemoteConnectionStatus.WaitingForController,
        RemoteConnectionStatus.Connecting,
        -> RemoteUiSection.Waiting

        is RemoteConnectionStatus.Failed -> RemoteUiSection.ConnectionFailed
        is RemoteConnectionStatus.Reconnecting -> RemoteUiSection.ConnectionLost

        is RemoteConnectionStatus.Connected -> {
            val snap = snapshot ?: return RemoteUiSection.ConnectedWaiting
            when (snap.surface) {
                RemotePrompterSurface.Library,
                RemotePrompterSurface.Editor,
                -> RemoteUiSection.ConnectedWaiting

                RemotePrompterSurface.Setup -> RemoteUiSection.Ready
                RemotePrompterSurface.Countdown -> RemoteUiSection.Countdown
                RemotePrompterSurface.Playing -> RemoteUiSection.Playing
                RemotePrompterSurface.Paused -> RemoteUiSection.Paused
                RemotePrompterSurface.Finished -> RemoteUiSection.Finished
            }
        }
    }
}
