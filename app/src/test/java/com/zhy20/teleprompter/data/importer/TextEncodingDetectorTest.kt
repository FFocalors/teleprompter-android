package com.zhy20.teleprompter.data.importer

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEncodingDetectorTest {
    @Test
    fun utf8WithoutBom_isDetected() {
        val bytes = "欢迎来到提词器。Hello world.".toByteArray(StandardCharsets.UTF_8)
        val result = TextEncodingDetector.detect(bytes)
        assertTrue(result is TextEncodingDetector.Result.Decoded)
        result as TextEncodingDetector.Result.Decoded
        assertEquals("UTF-8", result.encoding)
        assertEquals("欢迎来到提词器。Hello world.", result.text)
    }

    @Test
    fun utf8WithBom_isDetectedAndBomStripped() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + "中文内容".toByteArray(StandardCharsets.UTF_8)
        val result = TextEncodingDetector.detect(bytes)
        assertTrue(result is TextEncodingDetector.Result.Decoded)
        result as TextEncodingDetector.Result.Decoded
        assertEquals("UTF-8", result.encoding)
        assertEquals("中文内容", result.text)
    }

    @Test
    fun utf16LeWithBom_isDetected() {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val bytes = bom + "中文".toByteArray(StandardCharsets.UTF_16LE)
        val result = TextEncodingDetector.detect(bytes)
        assertTrue(result is TextEncodingDetector.Result.Decoded)
        result as TextEncodingDetector.Result.Decoded
        assertEquals("UTF-16LE", result.encoding)
        assertEquals("中文", result.text)
    }

    @Test
    fun utf16BeWithBom_isDetected() {
        val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val bytes = bom + "中文".toByteArray(StandardCharsets.UTF_16BE)
        val result = TextEncodingDetector.detect(bytes)
        assertTrue(result is TextEncodingDetector.Result.Decoded)
        result as TextEncodingDetector.Result.Decoded
        assertEquals("UTF-16BE", result.encoding)
        assertEquals("中文", result.text)
    }

    @Test
    fun emptyBytes_isValidUtf8WithNoText() {
        val result = TextEncodingDetector.detect(ByteArray(0))
        assertTrue(result is TextEncodingDetector.Result.Decoded)
        result as TextEncodingDetector.Result.Decoded
        assertEquals("", result.text)
    }

    @Test
    fun gb18030Fallback_decodesWhenUtf8StrictFails() {
        val charset = TextEncodingDetector.compatFallback()
        if (charset == null) return // JVM without GB18030 still behaves; Android always has it.
        val bytes = "简体中文标题".toByteArray(charset)
        val result = TextEncodingDetector.detect(bytes)
        assertTrue(result is TextEncodingDetector.Result.Decoded)
        result as TextEncodingDetector.Result.Decoded
        assertEquals(charset.name(), result.encoding)
        assertEquals("简体中文标题", result.text)
    }

    @Test
    fun gb18030Bytes_neverStrictlyDecodeAsUtf8() {
        val charset = TextEncodingDetector.compatFallback() ?: return
        val bytes = "测试".toByteArray(charset)
        // The GB18030 bytes of these characters must not be valid strict UTF-8,
        // otherwise the fallback would never trigger.
        assertNull(TextEncodingDetector.decodeStrict(bytes, 0, bytes.size, StandardCharsets.UTF_8))
    }

    @Test
    fun malformedUtf8_thatAlsoFailsGb18030_reportsFailed() {
        // 0x80 0x81 0x82 is invalid UTF-8 and is not a valid GB18030 lead byte either.
        val bytes = byteArrayOf(0x80.toByte(), 0x81.toByte(), 0x82.toByte())
        assertNull(TextEncodingDetector.decodeStrict(bytes, 0, bytes.size, StandardCharsets.UTF_8))
        val result = TextEncodingDetector.detect(bytes)
        assertEquals(TextEncodingDetector.Result.Failed, result)
    }

    @Test
    fun asciiIsSubsetOfUtf8_andIsDetected() {
        val bytes = "Plain ASCII text.".toByteArray(StandardCharsets.US_ASCII)
        val result = TextEncodingDetector.detect(bytes)
        assertTrue(result is TextEncodingDetector.Result.Decoded)
        result as TextEncodingDetector.Result.Decoded
        assertEquals("UTF-8", result.encoding)
        assertEquals("Plain ASCII text.", result.text)
    }

    @Test
    fun compatFallback_isNotNullOnAndroidLikeJvm() {
        assertNotNull(TextEncodingDetector.compatFallback())
    }
}
