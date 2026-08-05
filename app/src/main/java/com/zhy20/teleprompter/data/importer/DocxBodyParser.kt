package com.zhy20.teleprompter.data.importer

import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptDocument
import com.zhy20.teleprompter.core.model.ScriptSpan
import org.xmlpull.v1.XmlPullParser

/**
 * Walks the OOXML `word/document.xml` element stream and produces a [ScriptDocument] containing
 * only ordinary body paragraphs.
 *
 * Every `w:p` that is a direct body paragraph (not inside a table, field, drawing, object, text box
 * or content control) is collected as a single unstyled [ScriptSpan]; run properties (bold/italic/
 * underline), tables, images, drawings, hyperlinks, fields, TOC entries, headers/footers, footnotes
 * and page layout are all skipped. `w:br` becomes a line break and `w:tab` a single space.
 *
 * Skip states are simple depth counters — no Word DOM is built. All structural counters are capped
 * by [WordImportLimits]; every violation aborts immediately so no half-built document is produced.
 */
internal class DocxBodyParser {
    private val paragraphs = mutableListOf<ScriptBlock.Paragraph>()
    private var paragraphCounter = 0
    private var eventCounter = 0
    private var totalCharacters = 0

    // Block-level skip depths: while a counter is > 0 its nested w:p content is not collected.
    private var tableDepth = 0
    private var drawingDepth = 0
    private var objectDepth = 0
    private var sdtDepth = 0

    // Field skip depth: while > 0, run text is not collected. Wired through run parsing so fields
    // nested inside a paragraph (fldChar / fldSimple / hyperlink) are suppressed correctly.
    private var fieldDepth = 0

    fun parse(parser: XmlPullParser): ScriptDocument {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            eventCounter += 1
            if (eventCounter > WordImportLimits.MAX_XML_EVENTS) {
                throw ScriptImportException(ScriptImportError.TooComplex)
            }
            when {
                event == XmlPullParser.START_TAG && parser.isW(TAG_PARAGRAPH) -> {
                    if (blockSkipActive()) {
                        skipElement(parser)
                    } else {
                        parseParagraph(parser)
                    }
                }
                event == XmlPullParser.START_TAG && parser.isW(TAG_TABLE) -> tableDepth += 1
                event == XmlPullParser.START_TAG && parser.isW(TAG_SDT) -> sdtDepth += 1
                event == XmlPullParser.START_TAG && parser.isW(TAG_DRAWING) -> drawingDepth += 1
                event == XmlPullParser.START_TAG && parser.isW(TAG_PICT) -> drawingDepth += 1
                event == XmlPullParser.START_TAG && parser.isW(TAG_OBJECT) -> objectDepth += 1
                event == XmlPullParser.END_TAG && parser.isW(TAG_TABLE) -> tableDepth = (tableDepth - 1).coerceAtLeast(0)
                event == XmlPullParser.END_TAG && parser.isW(TAG_SDT) -> sdtDepth = (sdtDepth - 1).coerceAtLeast(0)
                event == XmlPullParser.END_TAG && parser.isW(TAG_DRAWING) -> drawingDepth = (drawingDepth - 1).coerceAtLeast(0)
                event == XmlPullParser.END_TAG && parser.isW(TAG_PICT) -> drawingDepth = (drawingDepth - 1).coerceAtLeast(0)
                event == XmlPullParser.END_TAG && parser.isW(TAG_OBJECT) -> objectDepth = (objectDepth - 1).coerceAtLeast(0)
            }
            event = parser.next()
        }
        if (paragraphs.isEmpty()) throw ScriptImportException(ScriptImportError.Empty)
        return ScriptContent(paragraphs)
    }

    private fun blockSkipActive(): Boolean =
        tableDepth > 0 || drawingDepth > 0 || objectDepth > 0 || sdtDepth > 0

    /** Parses one body `w:p`, collecting its run text. Positions on the paragraph end tag. */
    private fun parseParagraph(parser: XmlPullParser) {
        val text = StringBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when {
                    parser.isW(TAG_RUN) -> parseRunText(parser, text)
                    parser.isW(TAG_INS) -> parseTrackedInsertion(parser, text)
                    parser.isW(TAG_FIELD_SIMPLE) -> {
                        fieldDepth += 1
                        skipElement(parser)
                        fieldDepth = (fieldDepth - 1).coerceAtLeast(0)
                    }
                    parser.isW(TAG_HYPERLINK) -> {
                        fieldDepth += 1
                        skipElement(parser)
                        fieldDepth = (fieldDepth - 1).coerceAtLeast(0)
                    }
                    else -> skipElement(parser) // pPr, bookmark, proofErr, rPr, drawing, ...
                }
                XmlPullParser.END_TAG -> {
                    emitParagraph(text)
                    return
                }
                XmlPullParser.END_DOCUMENT -> {
                    emitParagraph(text)
                    return
                }
            }
        }
    }

    /**
     * Collects the runs inside a `w:ins` tracked-insertion wrapper into [text]. `w:ins` carries
     * visible run text in the current revision, so its content is kept (unlike `w:del`, whose
     * deleted runs are skipped via the default branch). Positions on the `w:ins` end tag.
     */
    private fun parseTrackedInsertion(parser: XmlPullParser, text: StringBuilder) {
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when {
                    parser.isW(TAG_RUN) -> parseRunText(parser, text)
                    parser.isW(TAG_HYPERLINK) -> {
                        fieldDepth += 1
                        skipElement(parser)
                        fieldDepth = (fieldDepth - 1).coerceAtLeast(0)
                    }
                    else -> skipElement(parser) // insPr, proofErr, drawing, ...
                }
                XmlPullParser.END_TAG -> return
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /** Collects the text of one `w:r` into [text], skipping fields. Positions on the run end tag. */
    private fun parseRunText(parser: XmlPullParser, text: StringBuilder) {
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when {
                    parser.isW(TAG_TEXT) -> {
                        val t = parser.nextText()
                        if (fieldDepth == 0) {
                            totalCharacters += t.length
                            if (totalCharacters > WordImportLimits.MAX_TEXT_CHARACTERS) {
                                throw ScriptImportException(ScriptImportError.TooComplex)
                            }
                            text.append(t)
                        }
                        // nextText consumes the text node and the w:t end tag.
                    }
                    parser.isW(TAG_BREAK) -> {
                        if (fieldDepth == 0) text.append('\n')
                        skipElement(parser)
                    }
                    parser.isW(TAG_TAB) -> {
                        if (fieldDepth == 0) text.append(' ')
                        skipElement(parser)
                    }
                    parser.isW(TAG_FIELD_CHAR) -> {
                        handleFieldChar(parser)
                    }
                    else -> skipElement(parser) // rPr (styles ignored), instrText, drawing, ...
                }
                XmlPullParser.END_TAG -> return
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /** Reads one `w:fldChar` and updates the field depth by its `w:fldCharType`. */
    private fun handleFieldChar(parser: XmlPullParser) {
        val type = parser.getAttributeValue(W_NAMESPACE, "fldCharType")
        when (type) {
            "begin" -> {
                fieldDepth += 1
                if (fieldDepth > WordImportLimits.MAX_FIELD_NESTING) {
                    throw ScriptImportException(ScriptImportError.TooComplex)
                }
            }
            "end" -> fieldDepth = (fieldDepth - 1).coerceAtLeast(0)
            "separate" -> Unit // stays in the field
        }
        skipElement(parser) // consume the fldChar subtree itself
    }

    private fun emitParagraph(text: StringBuilder) {
        if (fieldDepth > 0) return // a paragraph that never closed its field is dropped
        val cleaned = text.toString().trim()
        if (cleaned.isEmpty()) return
        if (paragraphs.size >= WordImportLimits.MAX_PARAGRAPHS) {
            throw ScriptImportException(ScriptImportError.TooComplex)
        }
        paragraphs += ScriptBlock.Paragraph(
            id = nextParagraphId(),
            spans = listOf(ScriptSpan(cleaned)),
        )
    }

    private fun nextParagraphId(): String = "docx-${paragraphCounter++}"

    /** Consumes an element and its whole subtree. Positions on its end tag. */
    private fun skipElement(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth += 1
                XmlPullParser.END_TAG -> depth -= 1
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun XmlPullParser.isW(name: String): Boolean =
        (eventType == XmlPullParser.START_TAG || eventType == XmlPullParser.END_TAG) &&
            namespace == W_NAMESPACE && this.name == name

    companion object {
        private const val W_NAMESPACE = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        private const val TAG_PARAGRAPH = "p"
        private const val TAG_RUN = "r"
        private const val TAG_TEXT = "t"
        private const val TAG_TAB = "tab"
        private const val TAG_BREAK = "br"
        private const val TAG_TABLE = "tbl"
        private const val TAG_SDT = "sdt"
        private const val TAG_FIELD_SIMPLE = "fldSimple"
        private const val TAG_FIELD_CHAR = "fldChar"
        private const val TAG_DRAWING = "drawing"
        private const val TAG_PICT = "pict"
        private const val TAG_OBJECT = "object"
        private const val TAG_HYPERLINK = "hyperlink"
        private const val TAG_INS = "ins"
    }
}
