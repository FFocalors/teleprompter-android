package com.zhy20.teleprompter.core.navigation

object AppRoutes {
    const val Library = "library"
    const val Editor = "editor/{scriptId}"
    const val Setup = "setup/{scriptId}"
    const val Prompter = "prompter/{scriptId}"
    const val Remote = "remote"
    const val Settings = "settings"
    const val PlaybackDefaults = "settings/playback-defaults"
    const val Language = "settings/language"
    const val About = "settings/about"

    fun editor(scriptId: String) = "editor/$scriptId"
    fun setup(scriptId: String) = "setup/$scriptId"
    fun prompter(scriptId: String) = "prompter/$scriptId"
}
