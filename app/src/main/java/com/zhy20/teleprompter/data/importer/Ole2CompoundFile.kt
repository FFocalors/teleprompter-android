package com.zhy20.teleprompter.data.importer

import java.io.InputStream

/**
 * Minimal read-only parser for the OLE2 Compound File (CFB) container used by legacy `.doc`.
 *
 * It implements exactly the parts needed to read whole streams out of a CFB: the header, the DIFAT
 * → FAT, the directory, and the mini-FAT / mini stream. Everything else (storages, property sets,
 * unicode directory names) is ignored. This is deliberately not a general-purpose OLE library; it
 * is scoped so a damaged or hostile file fails fast instead of exhausting memory.
 *
 * The class is JVM-only (no Android imports) so it is unit-testable on the host.
 */
internal class Ole2CompoundFile(
    private val fat: LongArray,
    private val miniFat: LongArray,
    private val sectorSize: Int,
    private val miniSectorSize: Int,
    private val sectorData: ByteArray,
) {
    /** Directory entry: UTF-8 name, type, start sector, stream size. */
    internal class DirectoryEntry(val name: String, val type: Int, val startSector: Long, val size: Long)

    val directory: List<DirectoryEntry>

    init {
        directory = readDirectory()
    }

    private fun readDirectory(): List<DirectoryEntry> {
        val firstDir = readUInt32(sectorData, HEADER_DIRECTORY_SECTOR_OFFSET).toInt()
        val chain = followFatChain(firstDir)
        val entries = mutableListOf<DirectoryEntry>()
        for (sector in chain) {
            val offset = sectorToByteOffset(sector)
            for (i in 0 until 4) {
                val entryOffset = offset + i * 128
                if (entryOffset + 128 > sectorData.size) break
                val nameLength = readUInt16(sectorData, entryOffset + 64)
                val type = sectorData[entryOffset + 66].toInt()
                val startSector = readUInt32(sectorData, entryOffset + 116).toLong()
                val size = readUInt64(sectorData, entryOffset + 120)
                if (nameLength == 0 || type == 0) continue
                val name = decodeUtf16Le(sectorData, entryOffset, nameLength)
                if (name.isEmpty()) continue
                entries.add(DirectoryEntry(name, type, startSector, size))
            }
        }
        return entries
    }

    /** Reads a stream's bytes by name. Returns null when the stream does not exist. */
    fun readStream(name: String): ByteArray? {
        val entry = directory.firstOrNull { it.name == name } ?: return null
        val size = entry.size
        if (size == 0L) return ByteArray(0)
        if (size >= MINI_STREAM_CUTOFF) {
            val chain = followFatChain(entry.startSector.toInt())
            return readChain(chain, size)
        }
        // Mini stream: stored inside the root entry's stream, chained by the mini-FAT.
        val root = directory.firstOrNull { it.type == TYPE_ROOT } ?: return null
        val rootChain = followFatChain(root.startSector.toInt())
        val miniStream = readChain(rootChain, root.size)
        return readMiniChain(miniStream, entry.startSector.toInt(), size)
    }

    /** True when the stream exists in the container (used for encrypted .docx detection). */
    fun hasStream(name: String): Boolean = directory.any { it.name == name }

    private fun followFatChain(start: Int): List<Int> {
        if (start < 0 || start >= fat.size) return emptyList()
        val chain = mutableListOf<Int>()
        var sector = start
        val seen = java.util.HashSet<Int>()
        while (sector >= 0 && sector < fat.size) {
            if (!seen.add(sector)) throw ScriptImportException(ScriptImportError.Corrupt) // cycle
            if (chain.size > MAX_FAT_CHAIN_LENGTH) throw ScriptImportException(ScriptImportError.TooComplex)
            chain.add(sector)
            val next = fat[sector]
            if (next == END_OF_CHAIN || next < 0) break
            sector = next.toInt()
        }
        return chain
    }

    private fun readChain(chain: List<Int>, size: Long): ByteArray {
        if (size > MAX_STREAM_BYTES) throw ScriptImportException(ScriptImportError.TooComplex)
        val out = java.io.ByteArrayOutputStream()
        var remaining = size
        for (sector in chain) {
            if (remaining <= 0) break
            val offset = sectorToByteOffset(sector)
            if (offset < 0 || offset >= sectorData.size) break
            val count = minOf(remaining, sectorSize.toLong()).toInt()
            out.write(sectorData, offset, count)
            remaining -= count
        }
        return out.toByteArray()
    }

    private fun readMiniChain(miniStream: ByteArray, start: Int, size: Long): ByteArray {
        if (size > MAX_STREAM_BYTES) throw ScriptImportException(ScriptImportError.TooComplex)
        val out = java.io.ByteArrayOutputStream()
        var remaining = size
        var sector = start
        val seen = java.util.HashSet<Int>()
        while (sector >= 0 && sector < miniFat.size && remaining > 0) {
            if (!seen.add(sector)) break
            val offset = sector.toLong() * miniSectorSize
            if (offset + miniSectorSize > miniStream.size) break
            val count = minOf(remaining.toInt(), miniSectorSize)
            out.write(miniStream, offset.toInt(), count)
            remaining -= count
            val next = miniFat[sector]
            if (next == END_OF_CHAIN || next < 0) break
            sector = next.toInt()
        }
        return out.toByteArray()
    }

    private fun sectorToByteOffset(sector: Int): Int = HEADER_SIZE + sector * sectorSize

    companion object {
        const val HEADER_SIZE = 512
        const val MINI_STREAM_CUTOFF = 4096L
        const val MAX_FAT_CHAIN_LENGTH = 200_000
        const val MAX_STREAM_BYTES = WordImportLimits.MAX_UNCOMPRESSED_BYTES
        private const val TYPE_ROOT = 5
        private const val END_OF_CHAIN = 0xFFFFFFFEL
        private const val HEADER_DIRECTORY_SECTOR_OFFSET = 48
        private const val HEADER_SECTOR_SHIFT_OFFSET = 30
        private const val HEADER_MINI_SECTOR_SHIFT_OFFSET = 32
        private const val HEADER_MINIFAT_FIRST_OFFSET = 60
        private const val HEADER_MINIFAT_COUNT_OFFSET = 64
        private const val HEADER_FAT_SECTOR_COUNT_OFFSET = 44
        private const val HEADER_FIRST_DIFAT_OFFSET = 76
        private const val HEADER_DIFAT_COUNT_OFFSET = 72

        /** Reads and parses a CFB from [input], capping total bytes read at [maxSourceBytes]. */
        fun read(input: InputStream, maxSourceBytes: Long = WordImportLimits.MAX_SOURCE_FILE_BYTES): Ole2CompoundFile {
            val data = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val count = input.read(chunk)
                if (count == -1) break
                total += count
                if (total > maxSourceBytes) throw ScriptImportException(ScriptImportError.TooLarge)
                data.write(chunk, 0, count)
            }
            return fromBytes(data.toByteArray())
        }

        fun fromBytes(bytes: ByteArray): Ole2CompoundFile {
            if (bytes.size < HEADER_SIZE) throw ScriptImportException(ScriptImportError.Corrupt)
            val sectorSize = 1 shl readUInt16(bytes, HEADER_SECTOR_SHIFT_OFFSET)
            val miniSectorSize = 1 shl readUInt16(bytes, HEADER_MINI_SECTOR_SHIFT_OFFSET)
            // The OLE2 spec requires a 512-byte sector and 64-byte mini sector minimum; anything
            // smaller is malformed and would break our arithmetic (e.g. division by zero below).
            if (sectorSize < 512 || sectorSize > 0x10000 || miniSectorSize < 64 || miniSectorSize > 0x1000) {
                throw ScriptImportException(ScriptImportError.Corrupt)
            }
            val fatSectorCount = readUInt32(bytes, HEADER_FAT_SECTOR_COUNT_OFFSET).toInt()
            if (fatSectorCount < 0 || fatSectorCount > MAX_FAT_SECTORS) {
                throw ScriptImportException(ScriptImportError.TooComplex)
            }

            // Build the FAT from the DIFAT (first 109 entries in the header plus chained DIFAT sectors).
            val maxSectors = ((bytes.size - HEADER_SIZE) + sectorSize - 1) / sectorSize
            val fat = LongArray((maxSectors + (sectorSize / 4) - 1) / (sectorSize / 4) * (sectorSize / 4))
            val fatSectorIndices = mutableListOf<Int>()
            var difatOffset = HEADER_FIRST_DIFAT_OFFSET
            var sectorsToRead = fatSectorCount
            // The header carries 109 DIFAT entries.
            val headerEntries = minOf(sectorsToRead, 109)
            for (i in 0 until headerEntries) {
                val s = readUInt32(bytes, difatOffset + i * 4).toInt()
                if (s >= 0 && s < maxSectors) fatSectorIndices.add(s)
            }
            sectorsToRead -= headerEntries
            // Chained DIFAT sectors: header[68..72] has the first one.
            var difatSector = readUInt32(bytes, 68).toInt()
            while (sectorsToRead > 0 && difatSector != END_OF_CHAIN.toInt() && difatSector >= 0 && difatSector < maxSectors) {
                val base = HEADER_SIZE + difatSector * sectorSize
                val entriesHere = (sectorSize / 4) - 1
                val n = minOf(sectorsToRead, entriesHere)
                for (i in 0 until n) {
                    val s = readUInt32(bytes, base + i * 4).toInt()
                    if (s >= 0 && s < maxSectors) fatSectorIndices.add(s)
                }
                sectorsToRead -= n
                difatSector = readUInt32(bytes, base + (sectorSize / 4 - 1) * 4).toInt()
            }

            var fatIndex = 0
            for (sector in fatSectorIndices) {
                if (fatIndex >= fat.size) break
                val base = HEADER_SIZE + sector * sectorSize
                if (base + sectorSize > bytes.size) continue
                for (j in 0 until sectorSize / 4) {
                    if (fatIndex >= fat.size) break
                    fat[fatIndex++] = readUInt32(bytes, base + j * 4)
                }
            }

            // Mini-FAT: a stream chained by the regular FAT.
            val firstMiniFat = readUInt32(bytes, HEADER_MINIFAT_FIRST_OFFSET).toInt()
            val miniFatCount = readUInt32(bytes, HEADER_MINIFAT_COUNT_OFFSET).toInt()
            val miniFatChain = followChainFromFAT(fat, firstMiniFat, miniFatCount, sectorSize, bytes)
            val miniFat = LongArray(miniFatChain.size * (sectorSize / 4))
            var mOff = 0
            for (sector in miniFatChain) {
                val base = HEADER_SIZE + sector * sectorSize
                if (base + sectorSize > bytes.size) continue
                for (j in 0 until sectorSize / 4) {
                    miniFat[mOff + j] = readUInt32(bytes, base + j * 4)
                }
                mOff += sectorSize / 4
            }

            return Ole2CompoundFile(
                fat = fat,
                miniFat = miniFat,
                sectorSize = sectorSize,
                miniSectorSize = miniSectorSize,
                sectorData = bytes,
            )
        }

        private fun followChainFromFAT(
            fat: LongArray,
            start: Int,
            maxSectors: Int,
            sectorSize: Int,
            bytes: ByteArray,
        ): List<Int> {
            if (start < 0 || start >= fat.size) return emptyList()
            val chain = mutableListOf<Int>()
            var sector = start
            val seen = java.util.HashSet<Int>()
            while (sector >= 0 && sector < fat.size) {
                if (!seen.add(sector)) break
                chain.add(sector)
                if (chain.size > maxSectors.coerceAtLeast(1)) break
                val next = fat[sector]
                if (next == END_OF_CHAIN || next < 0) break
                sector = next.toInt()
            }
            return chain
        }

        private fun decodeUtf16Le(data: ByteArray, offset: Int, byteLength: Int): String {
            val sb = StringBuilder()
            for (i in 0 until byteLength - 1 step 2) {
                val code = ((data[offset + i + 1].toInt() and 0xFF) shl 8) or (data[offset + i].toInt() and 0xFF)
                if (code == 0) break
                sb.append(code.toChar())
            }
            return sb.toString()
        }

        private fun readUInt16(data: ByteArray, offset: Int): Int {
            if (offset + 2 > data.size) throw ScriptImportException(ScriptImportError.Corrupt)
            return (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
        }

        private fun readUInt32(data: ByteArray, offset: Int): Long {
            if (offset + 4 > data.size) throw ScriptImportException(ScriptImportError.Corrupt)
            return (data[offset].toLong() and 0xFF) or
                ((data[offset + 1].toLong() and 0xFF) shl 8) or
                ((data[offset + 2].toLong() and 0xFF) shl 16) or
                ((data[offset + 3].toLong() and 0xFF) shl 24)
        }

        private fun readUInt64(data: ByteArray, offset: Int): Long {
            if (offset + 8 > data.size) throw ScriptImportException(ScriptImportError.Corrupt)
            var result = 0L
            for (i in 0 until 8) {
                result = result or ((data[offset + i].toLong() and 0xFF) shl (8 * i))
            }
            return result
        }

        const val MAX_FAT_SECTORS = 1 shl 18
    }
}
