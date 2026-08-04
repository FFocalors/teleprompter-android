package com.zhy20.teleprompter.data.importer

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Detects the encoding of a plain-text byte array and decodes it to a String.
 *
 * Strategy, in order:
 * 1. BOM is authoritative when present (UTF-8 / UTF-16 LE / UTF-16 BE), and is stripped.
 * 2. Without a BOM, UTF-8 is tried with strict decoding (any malformed byte rejects it).
 * 3. Only when strict UTF-8 fails, a compatible Chinese fallback (GB18030) is tried.
 *
 * No charset is ever decoded with replacement characters: malformed UTF-8 falls through to the
 * fallback, and only if the fallback also cannot decode is the file reported as unreadable.
 * The detector is intentionally pure JVM so every rule is unit-testable.
 */
object TextEncodingDetector {
    private val BOM_UTF8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val BOM_UTF16_LE = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val BOM_UTF16_BE = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    sealed interface Result {
        data class Decoded(val text: String, val encoding: String) : Result
        data object Failed : Result
    }

    /** Returns the decoded text and the detected encoding name, or [Result.Failed]. */
    fun detect(bytes: ByteArray): Result {
        if (bytes.isEmpty()) return Result.Decoded("", "UTF-8")

        if (bytes.startsWith(BOM_UTF16_LE)) {
            return decodeStrict(bytes, 2, bytes.size, StandardCharsets.UTF_16LE)?.let { Result.Decoded(it, "UTF-16LE") } ?: Result.Failed
        }
        if (bytes.startsWith(BOM_UTF16_BE)) {
            return decodeStrict(bytes, 2, bytes.size, StandardCharsets.UTF_16BE)?.let { Result.Decoded(it, "UTF-16BE") } ?: Result.Failed
        }
        if (bytes.startsWith(BOM_UTF8)) {
            return decodeStrict(bytes, 3, bytes.size, StandardCharsets.UTF_8)?.let { Result.Decoded(it, "UTF-8") } ?: Result.Failed
        }

        val utf8 = decodeStrict(bytes, 0, bytes.size, StandardCharsets.UTF_8)
        if (utf8 != null) return Result.Decoded(utf8, "UTF-8")

        val fallback = compatFallback()
        return fallback?.let { charset ->
            decodeStrict(bytes, 0, bytes.size, charset)?.let { Result.Decoded(it, charset.name()) }
        } ?: Result.Failed
    }

    /**
     * The Chinese-compatible fallback. Resolved lazily so environments without the charset
     * (exotic JVMs or constrained test runners) still behave; Android always provides it.
     */
    fun compatFallback(): Charset? = runCatching { Charset.forName("GB18030") }.getOrNull()

    /**
     * Strictly decodes [bytes] in [from, to) with [charset]; malformed input yields null
     * rather than U+FFFD replacement characters.
     */
    fun decodeStrict(bytes: ByteArray, from: Int, to: Int, charset: Charset): String? = try {
        val decoder: CharsetDecoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(bytes, from, to - from)).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (index in prefix.indices) {
            if (this[index] != prefix[index]) return false
        }
        return true
    }
}
