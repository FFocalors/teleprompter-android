package com.zhy20.teleprompter.remote.model

/**
 * The role a remote device plays in a teleprompter session.
 *
 * The prompter device owns the script, page, playback settings and the playback engine.
 * A controller device only sends command requests and observes snapshots.
 */
enum class RemoteRole {
    Prompter,
    Controller,
}
