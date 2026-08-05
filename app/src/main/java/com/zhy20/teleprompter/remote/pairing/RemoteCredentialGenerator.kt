package com.zhy20.teleprompter.remote.pairing

import java.security.SecureRandom

/**
 * Generates session ids and pairing tokens using a cryptographic PRNG. Tokens are at least
 * 128 bit of secure randomness (32 hex chars) and are never persisted.
 */
object RemoteCredentialGenerator {

    private val secureRandom = SecureRandom()

    /** A UUID-style session id. */
    fun newSessionId(): String = randomHex(16).let {
        "${it.substring(0, 8)}-${it.substring(8, 12)}-${it.substring(12, 16)}-${it.substring(16, 20)}-${it.substring(20)}"
    }

    /** At least 128 bit of secure randomness encoded as hex (32 chars). */
    fun newPairingToken(): String = randomHex(16)

    /** Unique connection id for a successful handshake. */
    fun newConnectionId(): String = randomHex(16)

    /** Opaque in-memory resume credential. */
    fun newResumeToken(): String = randomHex(32)

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
