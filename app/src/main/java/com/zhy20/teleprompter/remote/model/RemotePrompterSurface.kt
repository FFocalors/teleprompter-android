package com.zhy20.teleprompter.remote.model

/**
 * Stable domain surface of the prompter device, decoupled from Compose routes and the
 * NavController so a controller never depends on app navigation details.
 */
enum class RemotePrompterSurface {
    Library,
    Editor,
    Setup,
    Countdown,
    Playing,
    Paused,
    Finished,
}
