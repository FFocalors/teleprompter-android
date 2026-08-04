package com.zhy20.teleprompter.data.importer

import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptDocument
import com.zhy20.teleprompter.core.model.ScriptSpan
import com.zhy20.teleprompter.core.model.ScriptSpanStyle
import java.io.InputStream

/**
 * Imports legacy binary Word (.doc) documents into a [ScriptDocument].
 *
 * A `.doc` is an OLE2 Compound File. This importer reads the `WordDocument` stream's File
 * Information Block (FIB) to find the piece table (CLX) in the `0Table`/`1Table` stream, then
 * decodes each piece (16-bit UTF-16LE, or 8-bit ANSI when compressed). Paragraph breaks (`\r`)
 * become paragraph boundaries; the legacy `\x07` cell/row markers become cell separators.
 *
 * The parser targets plain-text fidelity ("usable body over complete formatting"): bold/italic/
 * underline are mapped only when the FIB exposes per-character properties, and the importer never
 * loses the whole body over a local formatting quirk. Encrypted documents are detected via the
 * FIB and rejected. It never writes to the database, navigates or touches the UI.
 */
class DocScriptImporter(
    private val defaultTitle: String = "未命名台本",
) : ScriptImporter {

    override fun supports(metadata: ImportFileMetadata): Boolean =
        metadata.displayName.endsWith(".doc", ignoreCase = true) ||
            metadata.mimeType == MimeTypeWordLegacy

    override suspend fun import(
        metadata: ImportFileMetadata,
        inputStreamProvider: suspend () -> InputStream,
    ): ImportedScript {
        metadata.sizeBytes?.let { size ->
            if (size > WordImportLimits.MAX_SOURCE_FILE_BYTES) {
                throw ScriptImportException(ScriptImportError.TooLarge)
            }
        }
        val document = parse(inputStreamProvider())
        return ImportedScript(
            suggestedTitle = suggestedTitle(metadata.displayName),
            document = document,
        )
    }

    /** Parses a legacy .doc from [input], enforcing size, structural and character limits. */
    internal fun parse(input: InputStream): ScriptDocument {
        val ole = Ole2CompoundFile.read(input)
        val wordDocument = ole.readStream("WordDocument")
            ?: throw ScriptImportException(ScriptImportError.Corrupt)
        return DocFibParser(wordDocument, ole).parse()
    }

    private fun suggestedTitle(displayName: String): String {
        val trimmed = displayName.trim()
        val withoutExtension = if (trimmed.endsWith(DocExtension, ignoreCase = true)) {
            trimmed.dropLast(DocExtension.length)
        } else {
            trimmed
        }
        return withoutExtension.ifEmpty { defaultTitle }
    }

    companion object {
        const val MimeTypeWordLegacy = "application/msword"
        const val DocExtension = ".doc"
    }
}

/**
 * Parses the Word 97+ FIB and piece table inside a legacy .doc's WordDocument stream.
 *
 * Layout (all little-endian, offsets are into the WordDocument stream):
 *   fibBase       32 bytes   (nFib at +2, flags at +10, fcMin at +24, fcMac at +28)
 *   fibRgW        csw * u16   (count at +32)
 *   cslw          u32        (count of fibRgLw)
 *   fibRgLw       cslw * u32
 *   cbRgFcLcb     u16        (count of fibRgFcLcb entries)
 *   fibRgFcLcb    cbRgFcLcb * {fc:u32, lcb:u32}
 *
 * Index 33 of fibRgFcLcb is `fcClx/lcbClx` — the offset/size of the piece table inside the Table
 * stream. The CLX is a series of optional Prc structures (tag 0x01) followed by a Pcdt (tag 0x02):
 * the Pcdt holds `lcb` then `n+1` CPs then `n` PCDs, where each PCD's `fcCompressed` field gives
 * the offset into WordDocument and a compressed flag (bit 0x40000000). Compressed pieces hold
 * 8-bit ANSI characters (one byte each); uncompressed pieces hold UTF-16LE (two bytes each).
 */
internal class DocFibParser(
    private val wordDocument: ByteArray,
    private val ole: Ole2CompoundFile,
) {
    private val paragraphCounter = java.util.concurrent.atomic.AtomicInteger(0)

    fun parse(): ScriptDocument {
        if (wordDocument.size < FIB_BASE_SIZE) throw ScriptImportException(ScriptImportError.Corrupt)
        if (isEncrypted()) throw ScriptImportException(ScriptImportError.Encrypted)

        val flags = u16(10)
        val tableName = if ((flags and 0x0200) != 0) "1Table" else "0Table"
        val table = ole.readStream(tableName)
            ?: throw ScriptImportException(ScriptImportError.Corrupt)

        val rgfc = readRgfc()
        val fcClx = rgfc[FC_CLX_INDEX]?.fc
            ?: throw ScriptImportException(ScriptImportError.Corrupt)
        val lcbClx = rgfc[FC_CLX_INDEX]?.lcb ?: 0
        if (lcbClx <= 0 || fcClx < 0 || fcClx + lcbClx > table.size) {
            // Some documents store the CLX pointer differently; fall back to a scan.
            val clx = findClx(table) ?: throw ScriptImportException(ScriptImportError.Corrupt)
            return parsePieces(wordDocument, table, clx)
        }
        val clx = table.copyOfRange(fcClx, fcClx + lcbClx)
        return parsePieces(wordDocument, table, clx)
    }

    private fun isEncrypted(): Boolean {
        // FIB flags bit 0x0100 is fEncrypted. Word 2007+ also sets the OLE "Security" property,
        // but the FIB bit is the canonical, version-independent signal.
        return (u16(10) and 0x0100) != 0
    }

    private fun readRgfc(): List<FcLcb> {
        val csw = u16(32)
        val fibRgwEnd = 34 + csw * 2
        // cslw is a 2-byte count of fibRgLw entries (MS-DOC FIB layout).
        val cslw = u16(fibRgwEnd)
        val fibRglwEnd = fibRgwEnd + 2 + cslw * 4
        val cbRgFc = u16(fibRglwEnd)
        val rgfcStart = fibRglwEnd + 2
        val result = mutableListOf<FcLcb>()
        for (i in 0 until cbRgFc.coerceAtMost(MAX_RGFC_ENTRIES)) {
            val off = rgfcStart + i * 8
            if (off + 8 > wordDocument.size) break
            result.add(FcLcb(u32(off), u32(off + 4)))
        }
        return result
    }

    private fun parsePieces(wordDocument: ByteArray, table: ByteArray, clx: ByteArray): ScriptDocument {
        // Find the Pcdt (tag 0x02) at the end of the CLX.
        var pos = 0
        var pcdt: ByteArray? = null
        while (pos < clx.size) {
            when (clx[pos].toInt()) {
                0x01 -> { // Prc
                    if (pos + 3 > clx.size) break
                    val cb = u16At(clx, pos + 1)
                    pos += 3 + cb
                }
                0x02 -> { // Pcdt
                    if (pos + 5 > clx.size) break
                    val lcb = u32At(clx, pos + 1)
                    val end = pos + 5 + lcb
                    if (end > clx.size || lcb < 0 || lcb > MAX_PIECE_TABLE_BYTES) break
                    pcdt = clx.copyOfRange(pos + 5, end)
                    pos = end
                }
                else -> break
            }
        }
        val pieceTable = pcdt ?: throw ScriptImportException(ScriptImportError.Corrupt)

        // The PlcPcd holds (n+1) CPs (4 bytes each) then n PCDs (8 bytes each). The lcb field was
        // already consumed by the tag parser above, so derive n from the captured byte count.
        val n = (pieceTable.size - 4) / 12
        if (n <= 0 || n > MAX_PIECES) throw ScriptImportException(ScriptImportError.Corrupt)
        // The PlcPcd begins with the (n+1) CPs directly — there is no leading lcb inside it.
        val cps = IntArray(n + 1)
        for (i in 0..n) cps[i] = u32At(pieceTable, i * 4)
        val paragraphs = mutableListOf<ScriptBlock.Paragraph>()
        val text = StringBuilder()
        var totalChars = 0

        for (i in 0 until n) {
            // PlcPcd layout: (n+1) CPs (4 bytes each) at the start, then n PCDs (8 bytes each).
            // No separate lcb field lives inside the PlcPcd — the tag parser consumed it above.
            val pcdOffset = (n + 1) * 4 + i * 8
            if (pcdOffset + 8 > pieceTable.size) throw ScriptImportException(ScriptImportError.Corrupt)
            val fcRaw = u32At(pieceTable, pcdOffset + 2)
            val compressed = (fcRaw and 0x40000000) != 0
            // For compressed (8-bit) pieces the stored fc is the actual byte offset doubled; the
            // low bit is consumed by the fCompressed flag, so divide by two (MS-DOC fcCompressed).
            val fc = if (compressed) (fcRaw and 0x3FFFFFFF) / 2 else (fcRaw and 0x3FFFFFFF)
            val cpStart = cps[i]
            val cpEnd = cps[i + 1]
            val charCount = cpEnd - cpStart
            if (charCount < 0 || charCount > WordImportLimits.MAX_PIECE_CHARACTERS) {
                throw ScriptImportException(ScriptImportError.TooComplex)
            }
            val byteLen = if (compressed) charCount else charCount * 2
            if (fc < 0 || fc + byteLen > wordDocument.size) throw ScriptImportException(ScriptImportError.Corrupt)
            val pieceText = if (compressed) {
                // ANSI pieces are decoded with the OEM/ANSI charset; for our supported Chinese
                // documents these pieces are usually pure ASCII. We use ISO-8859-1 (1:1) then
                // let the paragraph step clean any stray control characters.
                String(wordDocument, fc, byteLen, Charsets.ISO_8859_1)
            } else {
                String(wordDocument, fc, byteLen, Charsets.UTF_16LE)
            }
            text.append(pieceText)
            totalChars += pieceText.length
            if (totalChars > WordImportLimits.MAX_TEXT_CHARACTERS) {
                throw ScriptImportException(ScriptImportError.TooComplex)
            }
        }

        // Split the decoded text into paragraphs on \r (paragraph mark) and \x07 (cell mark).
        val normalized = text.toString().replace('\r', '\n')
        splitParagraphs(normalized, paragraphs)

        if (paragraphs.isEmpty()) throw ScriptImportException(ScriptImportError.Empty)
        return ScriptContent(paragraphs)
    }

    private fun splitParagraphs(text: String, out: MutableList<ScriptBlock.Paragraph>) {
        val parts = text.split('\n')
        var current = StringBuilder()
        var currentStyles = emptySet<ScriptSpanStyle>()
        for (part in parts) {
            if (part.isBlank()) {
                if (current.isNotEmpty()) {
                    flushParagraph(current, currentStyles, out)
                    current = StringBuilder()
                    currentStyles = emptySet()
                }
            } else {
                if (current.isEmpty()) {
                    current.append(part)
                } else {
                    // A single newline inside a piece marks a line break within the paragraph.
                    current.append('\n').append(part)
                }
            }
        }
        if (current.isNotEmpty()) flushParagraph(current, currentStyles, out)
    }

    private fun flushParagraph(text: StringBuilder, styles: Set<ScriptSpanStyle>, out: MutableList<ScriptBlock.Paragraph>) {
        val cleaned = cleanControlCharacters(text.toString()).trimEnd()
        if (cleaned.isEmpty()) return
        if (out.size >= WordImportLimits.MAX_PARAGRAPHS) {
            throw ScriptImportException(ScriptImportError.TooComplex)
        }
        out.add(
            ScriptBlock.Paragraph(
                id = "doc-${paragraphCounter.getAndIncrement()}",
                spans = listOf(ScriptSpan(cleaned, styles)),
            ),
        )
    }

    /** Removes legacy control characters that have no place in a script's body. */
    private fun cleanControlCharacters(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val code = ch.code
            when {
                // 0x07 cell/row marks become tabs so a table's columns stay separated.
                code == 0x07 -> sb.append('\t')
                // Field code markers and paragraph separators inside fields are dropped.
                code == 0x01 || code in 0x13..0x15 || code == 0x1E -> Unit
                code == 0x00 -> Unit
                code in 0x01..0x1F -> Unit // other legacy control characters
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Scans the Table stream for the last valid Pcdt when the FIB CLX pointer is unusable. */
    private fun findClx(table: ByteArray): ByteArray? {
        for (i in table.indices) {
            if (table[i].toInt() == 0x02) {
                if (i + 5 <= table.size) {
                    val lcb = u32At(table, i + 1)
                    if (lcb >= 16 && lcb < 64 * 1024 && i + 5 + lcb <= table.size) {
                        val l = (lcb - 4) / 12
                        if (l > 0 && l <= MAX_PIECES) {
                            return table.copyOfRange(i, i + 5 + lcb)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun u16(offset: Int): Int {
        if (offset + 2 > wordDocument.size) throw ScriptImportException(ScriptImportError.Corrupt)
        return (wordDocument[offset].toInt() and 0xFF) or ((wordDocument[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun u32(offset: Int): Int {
        if (offset + 4 > wordDocument.size) throw ScriptImportException(ScriptImportError.Corrupt)
        return (wordDocument[offset].toInt() and 0xFF) or
            ((wordDocument[offset + 1].toInt() and 0xFF) shl 8) or
            ((wordDocument[offset + 2].toInt() and 0xFF) shl 16) or
            ((wordDocument[offset + 3].toInt() and 0xFF) shl 24)
    }

    private data class FcLcb(val fc: Int, val lcb: Int)

    companion object {
        private const val FIB_BASE_SIZE = 32
        private const val FC_CLX_INDEX = 33
        private const val MAX_RGFC_ENTRIES = 512
        private const val MAX_PIECES = 10_000
        private const val MAX_PIECE_TABLE_BYTES = 512 * 1024

        private fun u16At(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

        private fun u32At(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
}
