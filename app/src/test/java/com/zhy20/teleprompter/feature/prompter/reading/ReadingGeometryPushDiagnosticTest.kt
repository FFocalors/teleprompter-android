package com.zhy20.teleprompter.feature.prompter.reading

import com.zhy20.teleprompter.app.AppState
import com.zhy20.teleprompter.app.RemoteAppCoordinator
import com.zhy20.teleprompter.app.RemoteStartPlaybackHandler
import com.zhy20.teleprompter.core.util.PlaybackLayoutCalculator
import com.zhy20.teleprompter.core.util.PlaybackLayoutMode
import com.zhy20.teleprompter.core.util.PlaybackReadingAnchor
import com.zhy20.teleprompter.data.fake.FakeData
import com.zhy20.teleprompter.remote.model.RemoteConnectionStatus
import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemoteRole
import com.zhy20.teleprompter.remote.pairing.RemotePairingPayload
import com.zhy20.teleprompter.remote.protocol.RemoteProtocol
import com.zhy20.teleprompter.remote.session.DefaultRemoteSessionRepository
import com.zhy20.teleprompter.remote.transport.WebSocketRemoteTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DIAGNOSTIC (round: "controller current-reading window still frozen at the first window"):
 * couples the prompter-side reading geometry loop — real engine scroll offsets (from
 * [PlaybackLayoutCalculator]) driving [PlaybackReadingTracker] and [ReadingWindowManager] — through
 * [RemoteAppCoordinator], a real prompter [DefaultRemoteSessionRepository], a real localhost
 * WebSocket, to a controller repository.
 *
 * Purpose: prove or disprove that an advancing playback cursor actually produces window revisions
 * 1, 2, 3 … that reach a controller repository. Every layer here is JVM-testable; if this passes,
 * any remaining break is narrowed to the Compose rendering layer ([PrompterViewport] emission /
 * [com.zhy20.teleprompter.feature.remote.ControllerReadingViewport] observation), which needs
 * on-device RemoteReadingDiag logs to pinpoint.
 */
class ReadingGeometryPushDiagnosticTest {

    private val prompterDevice = RemoteDeviceInfo("prompter-diag", "tablet", RemoteRole.Prompter)
    private val controllerDevice = RemoteDeviceInfo("ctrl-diag", "phone", RemoteRole.Controller)

    @Test
    fun advancingPlaybackGeometryPushesSlidingWindowsToAController() = runBlockingWithTimeout {
        // --- 1. canonical text + visual layout (mirrors PrompterViewport) ---
        val text = StringBuilder()
        val ranges = mutableListOf<IntRange>()
        for (line in 0 until 200) {
            val start = text.length
            repeat(25) { text.append('文') }
            ranges += start..text.length - 1
            if (line < 199) text.append('\n')
        }
        val canonical = text.toString()
        val lineHeightPx = 30f
        val layout = FakeReadingLayout.fromRanges(canonical, ranges, lineHeightPx = lineHeightPx)
        val textHeightPx = 200 * lineHeightPx // 6000px

        // --- 2. engine geometry (mirrors PrompterViewport: status band 64 + content 800) ---
        val contentHeightPx = 800f
        val anchorViewportY = contentHeightPx * 0.25f
        val engineViewport = 864f
        val metrics = PlaybackLayoutCalculator.calculate(
            viewportHeightPx = engineViewport,
            textHeightPx = textHeightPx,
            mode = PlaybackLayoutMode.LivePlayback,
            readingAnchor = PlaybackReadingAnchor(
                viewportFraction = 0.25f,
                initialTextOffsetLines = 0f,
                durationMillis = 200_000L,
                normalDurationSeconds = 200,
            ),
            lineHeightPx = lineHeightPx,
        )

        // --- 3. walk progress like the playback frame loop; collect cursor + window samples ---
        val manager = ReadingWindowManager()
        val samples = mutableListOf<Pair<ReadingCursorSample, ReadingWindow>>()
        var progress = 0f
        while (progress <= 0.8f) {
            val contentOffset = metrics.startOffsetPx + (metrics.endOffsetPx - metrics.startOffsetPx) * progress
            val anchorLocalY = anchorViewportY - contentOffset
            val cursor = PlaybackReadingTracker.computeCursor(layout, anchorLocalY, textRevision = 7L)
            val window = manager.update(canonical, 7L, cursor.absoluteOffset)
            samples += cursor to window
            progress += 0.005f
        }

        // --- 4. distinct windows the geometry loop produced (the diagnostic table) ---
        val distinct = samples.map { it.second }.distinctBy { it.revision }
        println(
            "DIAG windows produced by geometry: " +
                distinct.joinToString(" | ") { "rev${it.revision}@${it.startOffset}..${it.endOffset} len=${it.text.length}" },
        )
        println(
            "DIAG cursor walk: first=${samples.first().first.absoluteOffset} " +
                "mid=${samples[samples.size / 2].first.absoluteOffset} last=${samples.last().first.absoluteOffset}",
        )
        assertTrue("geometry must produce more than one window revision", distinct.size >= 2)

        // --- 5. push through coordinator + real prompter repo -> socket -> controller repo ---
        val prompterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prompterTransport = WebSocketRemoteTransport(bindPort = 0, scope = prompterScope)
        val prompter = DefaultRemoteSessionRepository(
            transport = prompterTransport,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            device = prompterDevice,
            lanAddressProvider = { "127.0.0.1" },
        )
        prompter.prepare(RemoteRole.Prompter)
        prompter.startWaiting()
        val port = withTimeout(5_000) {
            var candidate: Int? = null
            while (candidate == null) {
                candidate = prompterTransport.boundPort.value
                if (candidate == null) delay(50)
            }
            candidate
        }
        val pairing = prompter.sessionState.value.pairingPayload!!

        val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val clientTransport = WebSocketRemoteTransport(scope = clientScope)
        val controller = DefaultRemoteSessionRepository(
            transport = clientTransport,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            device = controllerDevice,
        )
        controller.prepare(RemoteRole.Controller)
        controller.connectToPrompter(
            RemotePairingPayload(
                protocolVersion = RemoteProtocol.VERSION,
                host = "127.0.0.1",
                port = port,
                sessionId = pairing.sessionId,
                pairingToken = pairing.pairingToken,
                expiresAtEpochMillis = Long.MAX_VALUE,
            ),
        )
        withTimeout(10_000) {
            while (prompter.sessionState.value.status !is RemoteConnectionStatus.Connected ||
                controller.sessionState.value.status !is RemoteConnectionStatus.Connected
            ) {
                delay(50)
            }
        }

        val state = AppState(
            initialScripts = FakeData.scripts,
            initialFolders = FakeData.folders,
            initialDefaults = FakeData.defaultPlaybackSettings,
        )
        val coordinator = RemoteAppCoordinator(
            state,
            prompter,
            CoroutineScope(UnconfinedTestDispatcher()),
            RemoteStartPlaybackHandler(),
        )

        // Push each distinct window through the coordinator; repo + socket forward it.
        var lastPushed = 0L
        for ((cursor, window) in samples) {
            if (window.revision <= lastPushed) continue
            lastPushed = window.revision
            state.updatePlaybackReadingWindow(window)
            state.updatePlaybackReadingCursor(cursor)
            coordinator.pushReadingState()
        }
        println("DIAG pushed ${distinct.size} windows, lastPushed=$lastPushed")

        // The controller must reach the latest window revision.
        var controllerSeen = mutableListOf<Long>()
        withTimeout(10_000) {
            var prev = controller.readingWindow.value?.windowRevision ?: 0L
            controllerSeen += prev
            while (prev < lastPushed) {
                delay(50)
                val now = controller.readingWindow.value?.windowRevision ?: 0L
                if (now != prev) {
                    controllerSeen += now
                    prev = now
                }
            }
        }
        println("DIAG controller window revision sequence: $controllerSeen")
        val received = controller.readingWindow.value
        println(
            "DIAG controller final window: rev=${received?.windowRevision} " +
                "start=${received?.startOffset} end=${received?.endOffset} len=${received?.text?.length}",
        )
        assertTrue("controller must receive window rev >= 2", (received?.windowRevision ?: 0L) >= 2L)
        assertTrue("controller window start must have advanced", (received?.startOffset ?: 0) > 1000)

        prompter.stopHosting()
    }
}

/** Runs the block with a generous global timeout so a hang fails loudly. */
private fun runBlockingWithTimeout(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
    kotlinx.coroutines.runBlocking {
        withTimeout(30_000) { block() }
    }
}
