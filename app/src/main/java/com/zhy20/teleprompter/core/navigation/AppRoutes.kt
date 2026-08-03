package com.zhy20.teleprompter.core.navigation

object AppRoutes {
    const val Library = "library"
    const val Editor = "editor/{scriptId}"
    const val Setup = "setup/{scriptId}"
    const val Prompter = "prompter/{scriptId}"
    const val Remote = "remote"
    const val Settings = "settings"
    const val Language = "settings/language"

    fun editor(scriptId: String) = "editor/$scriptId"
    fun setup(scriptId: String) = "setup/$scriptId"
    fun prompter(scriptId: String) = "prompter/$scriptId"
}
