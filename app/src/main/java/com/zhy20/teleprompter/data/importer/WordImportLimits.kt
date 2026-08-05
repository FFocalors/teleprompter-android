package com.zhy20.teleprompter.data.importer

/**
 * Central resource limits for Word (.doc/.docx) import. Every parser checks these while reading so
 * a malicious, damaged or pathological document can never exhaust memory or run forever.
 *
 * The values are chosen conservatively for a teleprompter script: they comfortably fit any real
 * speech script while keeping worst-case memory bounded well below what a mid-range phone can hold.
 */
object WordImportLimits {
    /** Hard cap on the source file itself (compressed on disk). Same order as TXT's 5 MiB. */
    const val MAX_SOURCE_FILE_BYTES = 20L * 1024 * 1024

    /** Total uncompressed bytes allowed inside a DOCX ZIP (guards against ZIP bombs). */
    const val MAX_UNCOMPRESSED_BYTES = 64L * 1024 * 1024

    /** Maximum number of entries a DOCX ZIP may contain. */
    const val MAX_ZIP_ENTRIES = 4096

    /** Maximum bytes for a single ZIP entry we actually read (document.xml etc.). */
    const val MAX_SINGLE_ENTRY_BYTES = 16L * 1024 * 1024

    /** Maximum number of body paragraphs extracted across the whole document. */
    const val MAX_PARAGRAPHS = 50_000

    /** Maximum number of characters in the final extracted text (word/char budget). */
    const val MAX_TEXT_CHARACTERS = 2_000_000

    /** Maximum characters accepted from one OLE text piece. */
    const val MAX_PIECE_CHARACTERS = 500_000

    /** Total XmlPullParser events the DOCX walker may consume (guards runaway subtrees). */
    const val MAX_XML_EVENTS = 5_000_000

    /** Maximum nested field depth accepted by the DOC field skip state machine. */
    const val MAX_FIELD_NESTING = 16
}
