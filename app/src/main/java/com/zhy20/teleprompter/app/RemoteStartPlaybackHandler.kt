package com.zhy20.teleprompter.app

import kotlinx.coroutines.channels.Channel

/**
 * Bridges a controller-issued start-playback request from the coordinator to the currently
 * visible Setup page.
 *
 * The coordinator sends a [Request]; the Setup composable receives it, flushes its settings
 * via [com.zhy20.teleprompter.feature.setup.SetupViewModel.flushNow], and only on success
 * navigates to the prompter route and completes the request with true. On save failure it
 * completes false and stays on the setup page. A `SynchronousSend` channel means
 * [requestStart] blocks until the setup page replies, so the coordinator can report a
 * truthful CommandResult.
 */
class RemoteStartPlaybackHandler {

    data class Request(
        val scriptId: String,
    )

    private val channel = Channel<Request>(capacity = Channel.UNLIMITED)

    private val openRequests = mutableMapOf<Request, (Boolean) -> Unit>()

    /**
     * Suspends until the visible Setup page flushes and answers. Returns false when no setup
     * page is collecting or when saving failed.
     */
    suspend fun requestStart(scriptId: String): Boolean {
        val request = Request(scriptId)
        val result = Channel<Boolean>(capacity = 1)
        synchronized(openRequests) { openRequests[request] = { value -> result.trySend(value) } }
        channel.send(request)
        return result.receive()
    }

    /** Called by the coordinator to observe incoming requests (collect in one place). */
    suspend fun awaitRequest(): Request = channel.receive()

    /** The Setup page completes a request after flush + navigation (or failure). */
    fun complete(request: Request, success: Boolean) {
        val callback = synchronized(openRequests) { openRequests.remove(request) }
        callback?.invoke(success)
    }

    /** Number of requests still awaiting a completion (used by tests). */
    fun pendingCount(): Int = synchronized(openRequests) { openRequests.size }
}
