package com.zhy20.teleprompter.remote.pairing

import com.zhy20.teleprompter.remote.protocol.RemoteProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePairingPayloadCodecTest {

    private fun payload(
        host: String = "192.168.137.20",
        port: Int = 8765,
        session: String = "abc123",
        token: String = "t".repeat(32),
        expires: Long = Long.MAX_VALUE,
        version: Int = RemoteProtocol.VERSION,
    ) = RemotePairingPayload(version, host, port, session, token, expires)

    @Test
    fun normalUriRoundTrips() {
        val original = payload()
        val raw = RemotePairingPayloadCodec.encode(original)
        val parsed = RemotePairingPayloadCodec.parse(raw).getOrThrow()
        assertEquals(original, parsed)
    }

    @Test
    fun wrongSchemeIsRejected() {
        val result = RemotePairingPayloadCodec.parse("http://pair?v=1&host=192.168.137.20&port=8765&session=s&token=t&expires=1")
        assertTrue(result.exceptionOrNull() is RemotePairingError.NotPairingUri)
    }

    @Test
    fun missingHostIsRejected() {
        val result = RemotePairingPayloadCodec.parse("teleprompter://pair?v=1&port=8765&session=s&token=t&expires=1")
        assertTrue(result.exceptionOrNull() is RemotePairingError.MissingFields)
    }

    @Test
    fun invalidIpv4IsRejected() {
        val result = RemotePairingPayloadCodec.parse("teleprompter://pair?v=1&host=999.1.1.1&port=8765&session=s&token=t&expires=1")
        assertTrue(result.exceptionOrNull() is RemotePairingError.InvalidHost)
        val nonIp = RemotePairingPayloadCodec.parse("teleprompter://pair?v=1&host=evil.example.com&port=8765&session=s&token=t&expires=1")
        assertTrue(nonIp.exceptionOrNull() is RemotePairingError.InvalidHost)
    }

    @Test
    fun invalidPortIsRejected() {
        val zero = RemotePairingPayloadCodec.parse("teleprompter://pair?v=1&host=192.168.1.5&port=0&session=s&token=t&expires=1")
        assertTrue(zero.exceptionOrNull() is RemotePairingError.InvalidPort)
        val tooBig = RemotePairingPayloadCodec.parse("teleprompter://pair?v=1&host=192.168.1.5&port=70000&session=s&token=t&expires=1")
        assertTrue(tooBig.exceptionOrNull() is RemotePairingError.InvalidPort)
    }

    @Test
    fun missingTokenIsRejected() {
        val result = RemotePairingPayloadCodec.parse("teleprompter://pair?v=1&host=192.168.1.5&port=8765&session=s&expires=1")
        assertTrue(result.exceptionOrNull() is RemotePairingError.MissingFields)
    }

    @Test
    fun expiredPayloadIsRejected() {
        val original = payload(expires = 1_000L)
        val raw = RemotePairingPayloadCodec.encode(original)
        val parsed = RemotePairingPayloadCodec.parse(raw).getOrThrow()
        val result = RemotePairingPayloadCodec.validateExpiry(parsed, nowEpochMillis = 2_000L)
        assertTrue(result.exceptionOrNull() is RemotePairingError.Expired)
    }

    @Test
    fun futureExpiryIsValid() {
        val original = payload(expires = 5_000L)
        val raw = RemotePairingPayloadCodec.encode(original)
        val parsed = RemotePairingPayloadCodec.parse(raw).getOrThrow()
        assertTrue(RemotePairingPayloadCodec.validateExpiry(parsed, 1_000L).isSuccess)
    }

    @Test
    fun unsupportedVersionIsRejected() {
        val raw = RemotePairingPayloadCodec.encode(payload(version = RemoteProtocol.VERSION + 1))
        val result = RemotePairingPayloadCodec.parse(raw)
        assertTrue(result.exceptionOrNull() is RemotePairingError.UnsupportedVersion)
    }

    @Test
    fun urlEncodedSessionIsSupported() {
        val original = payload(session = "session with spaces+plus")
        val raw = RemotePairingPayloadCodec.encode(original)
        val parsed = RemotePairingPayloadCodec.parse(raw).getOrThrow()
        assertEquals(original, parsed)
    }

    @Test
    fun truncationIsRejectedAsMissing() {
        val result = RemotePairingPayloadCodec.parse("teleprompter://pair?v=1&host=192.168.1.5&port=8765")
        assertTrue(result.isFailure)
    }

    @Test
    fun credentialGeneratorProducesSecureLengths() {
        assertTrue(RemoteCredentialGenerator.newPairingToken().length >= 32)
        assertEquals(36, RemoteCredentialGenerator.newSessionId().length)
        assertTrue(RemoteCredentialGenerator.newResumeToken().length >= 64)
    }
}
