package com.zhy20.teleprompter.core.model

enum class SaveIconTone { Neutral, Success, Error }

data class SaveIconPresentation(
    val tone: SaveIconTone,
    val retryEnabled: Boolean,
)

/** Keeps save feedback stable across continuous edits instead of flashing text or colors. */
fun SaveState.toSaveIconPresentation(savedAfterEdit: Boolean): SaveIconPresentation = when (this) {
    SaveState.Initial -> SaveIconPresentation(SaveIconTone.Neutral, retryEnabled = false)
    SaveState.Saving -> SaveIconPresentation(if (savedAfterEdit) SaveIconTone.Success else SaveIconTone.Neutral, retryEnabled = false)
    SaveState.Saved -> SaveIconPresentation(SaveIconTone.Success, retryEnabled = false)
    SaveState.Error -> SaveIconPresentation(SaveIconTone.Error, retryEnabled = true)
}
