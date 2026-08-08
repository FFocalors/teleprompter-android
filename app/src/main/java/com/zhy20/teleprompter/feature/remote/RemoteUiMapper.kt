package com.zhy20.teleprompter.feature.remote

import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus
import com.zhy20.teleprompter.remote.model.RemotePrompterSnapshot
import com.zhy20.teleprompter.remote.model.RemotePrompterSurface
import com.zhy20.teleprompter.remote.model.RemoteRole

/**
 * Which top-level panel the remote screen shows. Kept as a pure, Compose-free mapping so the
 * UI-state rules are unit-testable on the JVM.
 */
enum class RemoteUiSection {
    /** No role chosen yet — show the prompter/controller role chooser. */
    RoleSelection,

    /** Prompter role ready — show the "start waiting" panel. */
    PrompterReady,

    /** Prompter waiting for a controller — show the pairing QR. */
    PrompterWaiting,

    /** Controller role ready — show the scan/manual-connect panel. */
    ControllerReady,

    /** Handshake in progress. */
    Connecting,

    /** Connection failed with a structured reason. */
    ConnectionFailed,

    /** Link dropped — show the reconnect panel. */
    ConnectionLost,

    /** Connected but no ready prompter surface — show "waiting for setup". */
    ConnectedWaiting,

    /** Prompter role is connected — show the controller info and disconnect/stop actions. */
    PrompterConnected,

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
        role: RemoteRole?,
        reconnecting: Boolean,
    ): RemoteUiSection = when {
        reconnecting && status is RemoteConnectionStatus.Reconnecting -> RemoteUiSection.ConnectionLost
        role == null -> RemoteUiSection.RoleSelection
        else -> when (status) {
            RemoteConnectionStatus.Ready -> when (role) {
                RemoteRole.Prompter -> RemoteUiSection.PrompterReady
                RemoteRole.Controller -> RemoteUiSection.ControllerReady
            }
            RemoteConnectionStatus.WaitingForController -> RemoteUiSection.PrompterWaiting
            RemoteConnectionStatus.Connecting -> RemoteUiSection.Connecting
            RemoteConnectionStatus.Disabled -> RemoteUiSection.RoleSelection
            is RemoteConnectionStatus.Failed -> RemoteUiSection.ConnectionFailed
            is RemoteConnectionStatus.Reconnecting -> RemoteUiSection.ConnectionLost
            is RemoteConnectionStatus.Connected -> {
                if (role == RemoteRole.Prompter) {
                    // The prompter side shows connection management (controller name + the
                    // disconnect/stop actions), never the controller's playback panels.
                    RemoteUiSection.PrompterConnected
                } else {
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
    }
}
