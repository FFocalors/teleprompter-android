package com.zhy20.teleprompter.data.importer

import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptDocument
import com.zhy20.teleprompter.core.model.ScriptSpan
import com.zhy20.teleprompter.core.model.ScriptSpanStyle
import org.xmlpull.v1.XmlPullParser

/**
 * Walks the OOXML `word/document.xml` element stream and produces a [ScriptDocument].
 *
 * Document order is preserved: paragraphs and tables appear where they appear in the body. Tables
 * are flattened row-by-row with a tab separator between cells and a line break between a cell's own
 * paragraphs, so cell text never merges together. Hyperlinks contribute their display text (never
 * the URL). Runs are accumulated into spans and adjacent spans with identical styles merge into one,
 * exactly as the editor model expects.
 *
 * Only bold, italic and underline are mapped. Fonts, sizes, colors, highlight, strikethrough,
 * superscript/subscript, character spacing and language are ignored, as are images, headers and
 * footers, comments, footnotes, endnotes, text boxes, math, SmartArt, charts and page layout.
 *
 * All structural counters are capped by [WordImportLimits] and every violation aborts immediately;
 * no half-built document is ever produced. Each handler consumes exactly its own element's subtree,
 * so parsing is robust against unexpected nested content.
 */
internal class DocxBodyParser {
    private val paragraphs = mutableListOf<ScriptBlock.Paragraph>()
    private var paragraphCounter = 0
    private var runCounter = 0

    fun parse(parser: XmlPullParser): ScriptDocument {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when {
                event == XmlPullParser.START_TAG && parser.isW(TAG_PARAGRAPH) -> parseParagraph(parser)
                event == XmlPullParser.START_TAG && parser.isW(TAG_TABLE) -> parseTable(parser)
            }
            event = parser.next()
        }
        if (paragraphs.isEmpty()) throw ScriptImportException(ScriptImportError.Empty)
        return ScriptContent(paragraphs)
    }

    /** Parses a w:p. Positions the parser on its end tag. */
    private fun parseParagraph(parser: XmlPullParser) {
        val builder = SpanBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when {
                    parser.isW(TAG_RUN) -> parseRun(parser, builder)
                    parser.isW(TAG_HYPERLINK) -> parseHyperlink(parser, builder)
                    parser.isW(TAG_SMARTCONTENT) -> skipElement(parser)
                    else -> skipElement(parser) // pPr, bookmark, proofErr, commentRangeStart, ...
                }
                XmlPullParser.END_TAG -> {
                    appendParagraph(builder.build())
                    return
                }
                XmlPullParser.END_DOCUMENT -> {
                    appendParagraph(builder.build())
                    return
                }
            }
        }
    }

    /** Parses a w:hyperlink; keeps display text of its runs. Positions on its end tag. */
    private fun parseHyperlink(parser: XmlPullParser, builder: SpanBuilder) {
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when {
                    parser.isW(TAG_RUN) -> parseRun(parser, builder)
                    parser.isW(TAG_HYPERLINK) -> parseHyperlink(parser, builder)
                    else -> skipElement(parser)
                }
                XmlPullParser.END_TAG -> return
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /** Parses a w:r; appends its text (with styles) to [builder]. Positions on its end tag. */
    private fun parseRun(parser: XmlPullParser, builder: SpanBuilder) {
        runCounter += 1
        if (runCounter > WordImportLimits.MAX_RUNS) throw ScriptImportException(ScriptImportError.TooComplex)
        var styles = emptySet<ScriptSpanStyle>()
        val text = StringBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when {
                    parser.isW(TAG_RUN_PROPS) -> styles = parseRunProps(parser)
                    parser.isW(TAG_TEXT) -> {
                        text.append(parser.nextText())
                        // nextText consumes the text node and the w:t end tag.
                    }
                    parser.isW(TAG_TAB) -> {
                        text.append(TAB)
                        skipElement(parser) // consume the <w:tab/> element itself
                    }
                    parser.isW(TAG_BREAK) -> {
                        text.append('\n')
                        skipElement(parser) // consume the <w:br/> element itself
                    }
                    else -> skipElement(parser) // drawing, noBreakHyphen, softHyphen, ...
                }
                XmlPullParser.END_TAG -> {
                    builder.appendRun(text.toString(), styles)
                    return
                }
                XmlPullParser.END_DOCUMENT -> {
                    builder.appendRun(text.toString(), styles)
                    return
                }
            }
        }
    }

    /** Parses w:rPr; returns the mapped style set. Positions on the rPr end tag. */
    private fun parseRunProps(parser: XmlPullParser): Set<ScriptSpanStyle> {
        val styles = mutableSetOf<ScriptSpanStyle>()
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    // Capture the style, then consume the element (b/i/u are leaves). skipElement
                    // leaves the parser on that element's end tag, so the only END_TAG this loop
                    // sees is </rPr> — styles never leak into the sibling run elements.
                    when {
                        parser.isW(TAG_BOLD) -> {
                            styles += ScriptSpanStyle.Bold
                            skipElement(parser)
                        }
                        parser.isW(TAG_ITALIC) -> {
                            styles += ScriptSpanStyle.Italic
                            skipElement(parser)
                        }
                        parser.isW(TAG_UNDERLINE) -> {
                            styles += ScriptSpanStyle.Underline
                            skipElement(parser)
                        }
                        else -> skipElement(parser)
                    }
                }
                XmlPullParser.END_TAG -> return styles
                XmlPullParser.END_DOCUMENT -> return styles
            }
        }
    }

    /** Parses a w:tbl; emits one paragraph per row. Positions on the table end tag. */
    private fun parseTable(parser: XmlPullParser) {
        var cellCounter = 0
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when {
                    parser.isW(TAG_TABLE_ROW) -> {
                        val rowParagraphs = parseTableRow(parser, cellCounter)
                        cellCounter += rowParagraphs.second
                        rowParagraphs.first.forEach { paragraph ->
                            appendParagraph(paragraph.spans)
                        }
                    }
                    else -> skipElement(parser) // tblPr, tblGrid
                }
                XmlPullParser.END_TAG -> return
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /**
     * Parses one w:tr into (paragraphs, cellsConsumed). Each cell's text is collected with a tab
     * between cells, so a row is emitted as a single paragraph of tab-separated cell text; an empty
     * cell stays empty to keep the column positions. Positions on the row end tag.
     */
    private fun parseTableRow(parser: XmlPullParser, cellCounterStart: Int): Pair<List<ScriptBlock.Paragraph>, Int> {
        val cellTexts = mutableListOf<String>()
        var cells = 0
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when {
                    parser.isW(TAG_TABLE_CELL) -> {
                        cells += 1
                        if (cellCounterStart + cells > WordImportLimits.MAX_TABLE_CELLS) {
                            throw ScriptImportException(ScriptImportError.TooComplex)
                        }
                        cellTexts += parseTableCell(parser)
                    }
                    else -> skipElement(parser) // tblPrEx
                }
                XmlPullParser.END_TAG -> {
                    val joined = cellTexts.joinToString(TAB).trimEnd()
                    val paragraph = if (joined.isNotEmpty()) {
                        listOf(
                            ScriptBlock.Paragraph(
                                id = nextParagraphId(),
                                spans = listOf(ScriptSpan(joined)),
                            ),
                        )
                    } else {
                        emptyList()
                    }
                    return Pair(paragraph, cells)
                }
                XmlPullParser.END_DOCUMENT -> return Pair(emptyList(), cells)
            }
        }
    }

    /** Parses one w:tc; returns its text with cell-internal paragraphs joined by newlines. */
    private fun parseTableCell(parser: XmlPullParser): String {
        val cellParts = mutableListOf<String>()
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when {
                    parser.isW(TAG_PARAGRAPH) -> cellParts += parseParagraphText(parser)
                    else -> skipElement(parser) // tcPr, bookmarks...
                }
                XmlPullParser.END_TAG -> return cellParts.joinToString("\n").trim()
                XmlPullParser.END_DOCUMENT -> return cellParts.joinToString("\n").trim()
            }
        }
    }

    /** Parses a w:p and returns its joined span text, without emitting a top-level paragraph. */
    private fun parseParagraphText(parser: XmlPullParser): String {
        val builder = SpanBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when {
                    parser.isW(TAG_RUN) -> parseRun(parser, builder)
                    parser.isW(TAG_HYPERLINK) -> parseHyperlink(parser, builder)
                    else -> skipElement(parser)
                }
                XmlPullParser.END_TAG -> return builder.build().joinToString("") { it.text }
                XmlPullParser.END_DOCUMENT -> return builder.build().joinToString("") { it.text }
            }
        }
    }

    private fun appendParagraph(spans: List<ScriptSpan>) {
        if (spans.isEmpty()) return
        if (paragraphs.size >= WordImportLimits.MAX_PARAGRAPHS) {
            throw ScriptImportException(ScriptImportError.TooComplex)
        }
        paragraphs += ScriptBlock.Paragraph(id = nextParagraphId(), spans = spans)
    }

    private fun nextParagraphId(): String = "docx-${paragraphCounter++}"

    /** Consumes an unknown element and its whole subtree. Positions on its end tag. */
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
        eventType == XmlPullParser.START_TAG && namespace == W_NAMESPACE && this.name == name

    companion object {
        private const val W_NAMESPACE = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        private const val TAG_PARAGRAPH = "p"
        private const val TAG_RUN = "r"
        private const val TAG_TEXT = "t"
        private const val TAG_TAB = "tab"
        private const val TAG_BREAK = "br"
        private const val TAG_RUN_PROPS = "rPr"
        private const val TAG_BOLD = "b"
        private const val TAG_ITALIC = "i"
        private const val TAG_UNDERLINE = "u"
        private const val TAG_TABLE = "tbl"
        private const val TAG_TABLE_ROW = "tr"
        private const val TAG_TABLE_CELL = "tc"
        private const val TAG_HYPERLINK = "hyperlink"
        private const val TAG_SMARTCONTENT = "smartContent"
        private const val TAB = "\t"
    }
}

/** Accumulates runs into spans, merging adjacent spans with identical style sets. */
internal class SpanBuilder {
    private val spans = mutableListOf<ScriptSpan>()
    private var pendingText = StringBuilder()
    private var pendingStyles: Set<ScriptSpanStyle> = emptySet()

    fun appendRun(text: String, styles: Set<ScriptSpanStyle>) {
        if (text.isEmpty()) return
        if (pendingText.isEmpty()) {
            pendingText.append(text)
            pendingStyles = styles
        } else if (pendingStyles == styles) {
            pendingText.append(text)
        } else {
            flush()
            pendingText.append(text)
            pendingStyles = styles
        }
    }

    fun build(): List<ScriptSpan> {
        flush()
        return spans.toList()
    }

    private fun flush() {
        if (pendingText.isEmpty()) return
        spans += ScriptSpan(pendingText.toString(), pendingStyles)
        pendingText = StringBuilder()
        pendingStyles = emptySet()
    }
}
