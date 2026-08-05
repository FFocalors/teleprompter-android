package com.zhy20.teleprompter.remote.model

/** Stable, protocol-level identity of a remote device. No Android or Compose types. */
data class RemoteDeviceInfo(
    val deviceId: String,
    val displayName: String,
    val role: RemoteRole,
)
