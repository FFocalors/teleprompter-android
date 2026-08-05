package com.zhy20.teleprompter.remote.pairing

import java.net.URI
import java.net.URLDecoder

/**
 * Encodes a [RemotePairingPayload] to the QR URI and parses a scanned QR string back into a
 * validated payload. Pure JVM (java.net only) so it is unit-testable without Android.
 *
 * The URI format is:
 * `teleprompter://pair?v=1&host=192.168.137.20&port=8765&session=...&token=...&expires=...`
 *
 * Only valid IPv4 hosts and ports in `1..65535` are accepted, so an arbitrary URI can never
 * be turned into a connection target.
 */
object RemotePairingPayloadCodec {

    fun encode(payload: RemotePairingPayload): String {
        val params = listOf(
            "v" to payload.protocolVersion.toString(),
            "host" to payload.host,
            "port" to payload.port.toString(),
            "session" to urlEncode(payload.sessionId),
            "token" to urlEncode(payload.pairingToken),
            "expires" to payload.expiresAtEpochMillis.toString(),
        ).joinToString("&") { (key, value) -> "$key=$value" }
        return "$PAIRING_SCHEME://$PAIRING_HOST?$params"
    }

    /**
     * Parses and validates a scanned QR string. Returns the payload or a structured error.
     * Call [validateExpiry] separately so tests can distinguish "malformed" from "expired".
     */
    fun parse(raw: String): Result<RemotePairingPayload> {
        val uri = runCatching { URI(raw) }.getOrElse { return Result.failure(RemotePairingError.Malformed) }
        if (uri.scheme != PAIRING_SCHEME || uri.host != PAIRING_HOST) {
            return Result.failure(RemotePairingError.NotPairingUri)
        }
        val rawQuery = uri.rawQuery
            ?: return Result.failure(RemotePairingError.MissingFields)
        val params = parseQuery(rawQuery)

        val version = params["v"]?.toIntOrNull()
            ?: return Result.failure(RemotePairingError.MissingFields)
        val host = params["host"]
            ?: return Result.failure(RemotePairingError.MissingFields)
        val port = params["port"]?.toIntOrNull()
            ?: return Result.failure(RemotePairingError.MissingFields)
        val session = params["session"]
            ?: return Result.failure(RemotePairingError.MissingFields)
        val token = params["token"]
            ?: return Result.failure(RemotePairingError.MissingFields)
        val expires = params["expires"]?.toLongOrNull()
            ?: return Result.failure(RemotePairingError.MissingFields)

        if (!isValidIpv4(host)) return Result.failure(RemotePairingError.InvalidHost)
        if (port !in 1..65535) return Result.failure(RemotePairingError.InvalidPort)
        if (token.isEmpty() || token.length > MAX_TOKEN_LENGTH) return Result.failure(RemotePairingError.InvalidToken)
        if (session.isEmpty() || session.length > MAX_SESSION_LENGTH) return Result.failure(RemotePairingError.InvalidSession)
        if (expires < 0L) return Result.failure(RemotePairingError.InvalidExpiry)
        if (version != com.zhy20.teleprompter.remote.protocol.RemoteProtocol.VERSION) {
            return Result.failure(RemotePairingError.UnsupportedVersion)
        }

        return Result.success(
            RemotePairingPayload(
                protocolVersion = version,
                host = host,
                port = port,
                sessionId = session,
                pairingToken = token,
                expiresAtEpochMillis = expires,
            ),
        )
    }

    /** Time-based validation; call with the payload produced by [parse]. */
    fun validateExpiry(payload: RemotePairingPayload, nowEpochMillis: Long): Result<Unit> {
        if (payload.expiresAtEpochMillis < nowEpochMillis) {
            return Result.failure(RemotePairingError.Expired)
        }
        return Result.success(Unit)
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun parseQuery(rawQuery: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        rawQuery.split('&').forEach { pair ->
            val index = pair.indexOf('=')
            if (index > 0) {
                val key = pair.substring(0, index)
                val value = URLDecoder.decode(pair.substring(index + 1), "UTF-8")
                if (result.put(key, value) == null) {
                    // keep the first occurrence of a duplicate key
                }
            }
        }
        return result
    }

    private fun isValidIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            if (part.isEmpty() || part.length > 3) return false
            if (part.length > 1 && part.startsWith('0')) return false
            val value = part.toIntOrNull() ?: return false
            value in 0..255
        }
    }
}
