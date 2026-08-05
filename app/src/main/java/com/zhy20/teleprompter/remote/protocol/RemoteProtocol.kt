package com.zhy20.teleprompter.remote.protocol

/** Version constants for the remote-control protocol. */
object RemoteProtocol {
    /**
     * The protocol revision this app speaks. v2 introduced the explicit pairing handshake
     * (ClientHello/ServerAccepted/ServerRejected), heartbeats, disconnects, and structured
     * command results. Bump on incompatible message changes.
     */
    const val VERSION = 2
}
