package com.zhy20.teleprompter.remote.protocol

import com.zhy20.teleprompter.remote.model.RemoteDeviceInfo
import com.zhy20.teleprompter.remote.model.RemotePlaybackState
import com.zhy20.teleprompter.remote.model.RemotePrompterSurface
import com.zhy20.teleprompter.remote.model.RemoteRole
import com.zhy20.teleprompter.remote.model.remotePrompterSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteJsonCodecTest {

    private val device = RemoteDeviceInfo("dev-1", "手机", RemoteRole.Controller)

    private fun roundTrip(message: RemoteMessage): RemoteMessage {
        val json = RemoteJsonCodec.encode(message)
        val decoded = RemoteJsonCodec.decode(json).getOrThrow()
        assertEquals(message, decoded)
        return decoded
    }

    @Test
    fun clientHelloRoundTrips() {
        val message = RemoteMessage.ClientHello(2, "s1", "tok123", device)
        roundTrip(message)
    }

    @Test
    fun serverAcceptedRoundTripsWithUnicodeAndSnapshot() {
        val snapshot = remotePrompterSnapshot(
            revision = 3,
            surface = RemotePrompterSurface.Setup,
            scriptId = "7",
            scriptTitle = "开幕式主持稿·中文",
            estimatedDurationSeconds = 150,
            playbackState = RemotePlaybackState(isCountdown = true, countdownSecondsRemaining = 3),
            progress = 0.4f,
            elapsedTimeMillis = 60_000L,
            remainingTimeMillis = 90_000L,
            speedMultiplier = 1.5f,
            nearbyText = "尊敬的各位来宾，亲爱的老师、同学们，大家上午好。",
        )
        val message = RemoteMessage.ServerAccepted("c1", device, "resume", snapshot)
        roundTrip(message)
    }

    @Test
    fun commandRequestRoundTripsAllCommandTypes() {
        roundTrip(RemoteMessage.CommandRequest(RemoteCommand.StartPlayback("1", "script-1")))
        roundTrip(RemoteMessage.CommandRequest(RemoteCommand.PausePlayback("2")))
        roundTrip(RemoteMessage.CommandRequest(RemoteCommand.ResumeImmediately("3")))
        roundTrip(RemoteMessage.CommandRequest(RemoteCommand.ResumeWithCountdown("4")))
        roundTrip(RemoteMessage.CommandRequest(RemoteCommand.SeekBy("5", -0.03f)))
        roundTrip(RemoteMessage.CommandRequest(RemoteCommand.ChangeSpeed("6", 0.1f)))
        roundTrip(RemoteMessage.CommandRequest(RemoteCommand.EndPlayback("7")))
    }

    @Test
    fun commandResultRoundTripsWithRevision() {
        val message = RemoteMessage.CommandResult("c1", false, RemoteRejectReason.CommandNotAllowedInState, "not allowed", null)
        roundTrip(message)
    }

    @Test
    fun snapshotUpdateRoundTrips() {
        val snapshot = remotePrompterSnapshot(
            revision = 1,
            surface = RemotePrompterSurface.Playing,
            scriptId = "1",
            scriptTitle = "校长采访开场",
            progress = 0.5f,
            elapsedTimeMillis = 100_000L,
            remainingTimeMillis = 100_000L,
            speedMultiplier = 1f,
            nearbyText = "接下来，我们会介绍三个重点项目。",
        )
        roundTrip(RemoteMessage.SnapshotUpdate(snapshot))
    }

    @Test
    fun heartbeatPingAndPongRoundTrip() {
        roundTrip(RemoteMessage.HeartbeatPing(42))
        roundTrip(RemoteMessage.HeartbeatPong(42))
    }

    @Test
    fun disconnectNoticeAndProtocolErrorRoundTrip() {
        roundTrip(RemoteMessage.DisconnectNotice(RemoteRejectReason.TokenExpired, "bye"))
        roundTrip(RemoteMessage.ProtocolError(RemoteProtocolErrorCode.UnsupportedVersion, "v999"))
    }

    @Test
    fun invalidJsonReturnsMalformedError() {
        val result = RemoteJsonCodec.decode("{ not json")
        assertTrue(result.isFailure)
    }

    @Test
    fun missingTypeReturnsMalformedError() {
        val result = RemoteJsonCodec.decode("""{"foo":1}""")
        assertTrue(result.isFailure)
    }

    @Test
    fun unknownTypeReturnsUnknownTypeError() {
        val result = RemoteJsonCodec.decode("""{"type":"bogus"}""")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RemoteDecodeError.UnknownType)
    }

    @Test
    fun invalidProgressIsRejected() {
        val bad = """{"type":"snapshotUpdate","snapshot":{"revision":1,"surface":"Playing","progress":1.5,"speed":1.0,"elapsedTimeMillis":0,"remainingTimeMillis":0,"playbackState":{}}}"""
        val result = RemoteJsonCodec.decode(bad)
        assertTrue(result.isFailure)
    }

    @Test
    fun nanAndInfinityAreRejected() {
        val bad = """{"type":"snapshotUpdate","snapshot":{"revision":1,"surface":"Playing","progress":NaN,"speed":1.0,"elapsedTimeMillis":0,"remainingTimeMillis":0,"playbackState":{}}}"""
        val result = RemoteJsonCodec.decode(bad)
        assertTrue(result.isFailure)
    }

    @Test
    fun missingRequiredFieldIsRejected() {
        val bad = """{"type":"clientHello","sessionId":"s","pairingToken":"t","device":{"deviceId":"d","displayName":"n","role":"Controller"}}"""
        val result = RemoteJsonCodec.decode(bad)
        assertTrue(result.isFailure) // missing protocolVersion
    }

    @Test
    fun unknownExtraFieldsAreIgnored() {
        val json = """{"type":"heartbeatPing","sequence":1,"extra":"ignored"}"""
        val decoded = RemoteJsonCodec.decode(json).getOrThrow()
        assertEquals(RemoteMessage.HeartbeatPing(1), decoded)
    }

    @Test
    fun oversizedMessageIsRejected() {
        val huge = "a".repeat(100 * 1024)
        val result = RemoteJsonCodec.decode(huge)
        assertTrue(result.isFailure)
    }

    @Test
    fun nearbyTextIsTruncatedOnDecode() {
        val longText = "字".repeat(500)
        val snapshot = remotePrompterSnapshot(
            revision = 1,
            surface = RemotePrompterSurface.Playing,
            nearbyText = longText,
        )
        val json = RemoteJsonCodec.encode(RemoteMessage.SnapshotUpdate(snapshot))
        val decoded = (RemoteJsonCodec.decode(json).getOrThrow() as RemoteMessage.SnapshotUpdate).snapshot
        assertEquals(140, decoded.nearbyText?.length)
    }
}
