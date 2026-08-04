package com.zhy20.teleprompter.data.importer

import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptDocument
import com.zhy20.teleprompter.core.model.ScriptSpan
import java.util.concurrent.atomic.AtomicInteger

/**
 * Parses the supported Markdown subset into a [ScriptDocument].
 *
 * This phase deliberately targets plain-text scripts, not full CommonMark. Supported constructs:
 *   - a leading level-1 heading (ATX `# 标题` or Setext `标题` over `===`) becomes the script title;
 *   - level-2..6 ATX headings and Setext headings become plain paragraphs in the body;
 *   - blank lines separate paragraphs; a single line break inside a paragraph is preserved.
 *
 * Any Markdown feature outside this subset (bold, italic, lists, code, links, tables, HTML, math,
 * YAML front matter, footnotes, horizontal rules, ...) is rejected with
 * [ScriptImportError.UnsupportedMarkdownSyntax] instead of being partially parsed, so a script is
 * never created from mangled Markdown.
 *
 * The parser is pure JVM: it does not touch the ContentResolver, the repository or the UI. Errors
 * carry only a safe line number and the error category — never the document text.
 */
internal class MarkdownSubsetParser {
    private val paragraphCounter = AtomicInteger(0)

    /** Parses [text] into a document plus the title derived from the first level-1 heading. */
    fun parse(text: String): ParseResult {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        val firstContent = lines.indexOfFirst { it.isNotBlank() }

        // --- Title: only the first non-blank content block may be the level-1 heading. ---
        var title: String? = null
        var startIndex: Int
        if (firstContent < 0) {
            startIndex = 0 // all lines blank; the scan below simply yields nothing
        } else {
            val atx = parseAtxHeading(lines[firstContent])
            if (atx != null && atx.level == 1) {
                detectInlineUnsupported(lines[firstContent], firstContent)
                title = atx.text
                startIndex = firstContent + 1
            } else if (isSetextH1(lines, firstContent)) {
                detectInlineUnsupported(lines[firstContent], firstContent)
                title = lines[firstContent].trim()
                startIndex = firstContent + 2
            } else {
                startIndex = firstContent
            }
        }

        // --- Body: headings become plain paragraphs; unsupported syntax is rejected. ---
        val body = mutableListOf<String>()
        val buffer = mutableListOf<String>()
        fun flush() {
            if (buffer.isNotEmpty()) {
                body.add(buffer.joinToString("\n"))
                buffer.clear()
            }
        }
        var i = startIndex
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank() -> {
                    flush()
                    i += 1
                }
                // A Setext underline directly under preceding text is a heading, not a rule:
                // the buffered text becomes the heading and the underline is consumed.
                buffer.isNotEmpty() && isSetextUnderline(line) -> {
                    body.add(buffer.joinToString("\n").trim())
                    buffer.clear()
                    i += 1
                }
                else -> {
                    val heading = parseAtxHeading(line)
                    if (heading != null) {
                        // Heading lines are checked for inline formatting only (bold inside a
                        // heading is still bold); block-level markers are consumed by the heading.
                        detectInlineUnsupported(line, i)
                        flush()
                        // A heading with empty text ("## " or "## #") adds nothing to the body.
                        if (heading.text.isNotEmpty()) body.add(heading.text)
                    } else {
                        detectUnsupported(line, i)
                        buffer += line.trimEnd()
                    }
                    i += 1
                }
            }
        }
        flush()

        val blocks = body.map { raw ->
            ScriptBlock.Paragraph(
                id = "md-${paragraphCounter.getAndIncrement()}",
                spans = listOf(ScriptSpan(raw)),
            )
        }
        return ParseResult(title = title, document = ScriptContent(blocks))
    }

    /** Returns the ATX heading text and level when [line] is an ATX heading, else null. */
    private fun parseAtxHeading(line: String): AtxHeading? {
        val trimmed = line.trimStart()
        var hashes = 0
        for (ch in trimmed) {
            if (ch == '#') hashes += 1 else break
        }
        if (hashes < 1 || hashes > 6) return null
        if (trimmed.length == hashes) return null // "###" alone is not a heading
        if (trimmed[hashes] != ' ') return null // "##" must be followed by a space
        return AtxHeading(hashes, stripClosingHashes(trimmed.substring(hashes)))
    }

    /** True when [lines][index] holds a Setext level-1 heading (text over a `===` underline). */
    private fun isSetextH1(lines: List<String>, index: Int): Boolean {
        if (index + 1 >= lines.size) return false
        val underline = lines[index + 1].trim()
        if (underline.length < 3) return false
        if (underline.any { it != '=' }) return false
        return lines[index].isNotBlank()
    }

    /** True when [line] is a Setext underline (`===` or `---`, at least three characters). */
    private fun isSetextUnderline(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.length < 3) return false
        val first = trimmed[0]
        if (first != '=' && first != '-') return false
        return trimmed.all { it == first }
    }

    /** Removes a trailing ATX closing sequence (`# 标题 #`) only when preceded by whitespace. */
    private fun stripClosingHashes(text: String): String =
        text.trim().replace(Regex("""\s+#+$"""), "").trim()

    /** Throws [ScriptImportException] when [line] contains a construct outside the subset. */
    private fun detectUnsupported(line: String, lineNumber: Int) {
        val trimmed = line.trimStart()

        // Fenced code blocks (``` and ~~~).
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            throw unsupported(lineNumber)
        }
        // Indented code blocks (four spaces or a tab).
        if (line.startsWith("    ") || line.startsWith("\t")) {
            throw unsupported(lineNumber)
        }
        // Unordered lists (- , * , +).
        if (isUnorderedList(trimmed)) {
            throw unsupported(lineNumber)
        }
        // Ordered lists (1. / 1)).
        if (Regex("""^\d+[.)]\s""").containsMatchIn(trimmed)) {
            throw unsupported(lineNumber)
        }
        // Task lists.
        if (isTaskList(trimmed)) {
            throw unsupported(lineNumber)
        }
        // Blockquote.
        if (trimmed.startsWith(">")) {
            throw unsupported(lineNumber)
        }
        // YAML front matter and horizontal rules (--- +++ *** ___).
        if (trimmed == "---" || trimmed == "+++" || isHorizontalRule(trimmed)) {
            throw unsupported(lineNumber)
        }
        // HTML block.
        if (trimmed.startsWith("<") && looksLikeHtml(trimmed)) {
            throw unsupported(lineNumber)
        }
        // Math display block.
        if (trimmed.startsWith("$$")) {
            throw unsupported(lineNumber)
        }
        // Footnote definition: "[^n]: text".
        if (trimmed.startsWith("[^") && trimmed.contains("]:")) {
            throw unsupported(lineNumber)
        }
        // Table rows: two or more pipes clearly indicate a table layout. A single `|` is prose.
        if (trimmed.count { it == '|' } >= 2) {
            throw unsupported(lineNumber)
        }

        // Inline constructs inside an ordinary paragraph line.
        detectInlineUnsupported(line, lineNumber)
    }

    private fun detectInlineUnsupported(line: String, lineNumber: Int) {
        // Bold ** and __.
        if (hasEmphasisPair(line, "**") || hasEmphasisPair(line, "__")) {
            throw unsupported(lineNumber)
        }
        // Italic * and _ (single markers, not part of a bold pair).
        if (!line.contains("**") && hasEmphasisPair(line, "*") ||
            !line.contains("__") && hasEmphasisPair(line, "_")
        ) {
            throw unsupported(lineNumber)
        }
        // Strikethrough ~~.
        if (hasBalancedPair(line, "~~")) {
            throw unsupported(lineNumber)
        }
        // Inline code `code`.
        if (hasBalancedPair(line, "`")) {
            throw unsupported(lineNumber)
        }
        // Images ![alt](url) and links [text](url).
        if (Regex("""!?\[[^\]\n]*\]\([^)\n]*\)""").containsMatchIn(line)) {
            throw unsupported(lineNumber)
        }
        // Autolinks <http://...> / <https://...> / <mailto:...>.
        if (Regex("""<(https?|mailto):[^>\s]+>""").containsMatchIn(line)) {
            throw unsupported(lineNumber)
        }
        // Footnote references [^n].
        if (Regex("""\[\^[^\]\s]+\]""").containsMatchIn(line)) {
            throw unsupported(lineNumber)
        }
        // Inline math $x$ — only ASCII math content counts; CJK amounts like "$5和$6" stay prose.
        if (hasInlineMath(line)) {
            throw unsupported(lineNumber)
        }
        // Inline HTML tags (including self-closing <br/>, <img/>).
        if (Regex("""<[a-zA-Z][a-zA-Z0-9]*(\s[^>]*)?\s*/?>""").containsMatchIn(line)) {
            throw unsupported(lineNumber)
        }
    }

    private fun isUnorderedList(trimmed: String): Boolean =
        trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")

    private fun isTaskList(trimmed: String): Boolean {
        if (!(trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ "))) return false
        val rest = trimmed.drop(2)
        return rest.startsWith("[ ]") || rest.startsWith("[x]") || rest.startsWith("[X]")
    }

    private fun isHorizontalRule(trimmed: String): Boolean {
        if (trimmed.length < 3) return false
        val first = trimmed[0]
        if (first != '-' && first != '*' && first != '_') return false
        return trimmed.all { it == first || it.isWhitespace() }
    }

    private fun looksLikeHtml(line: String): Boolean =
        line.startsWith("<!--") || line.length >= 2 && line[1].isLetter()

    /**
     * True when [marker] appears twice surrounding a well-formed emphasis body.
     *
     * Two distinct emphases are recognized:
     *   - CJK emphasis: both markers are directly adjacent to CJK characters (`_斜体_`,
     *     `*重要*`) — unambiguous in a Chinese script.
     *   - ASCII emphasis: markers next to whitespace/punctuation (`_hello_`, `A _B_ C`) — but a
     *     marker pair embedded in word characters is ordinary prose (`user_id 和 user_name`,
     *     `a_b_c`, `2*3*4` as multiplication, `100_000`).
     * Whitespace-padded content (`A * B * C`) is never emphasis.
     */
    private fun hasEmphasisPair(line: String, marker: String): Boolean {
        val first = line.indexOf(marker)
        if (first < 0) return false
        val second = line.indexOf(marker, first + marker.length)
        if (second < 0) return false
        val contentStart = first + marker.length
        val contentEnd = second
        if (contentStart >= contentEnd) return false
        // Emphasis body may not start or end with whitespace.
        if (line[contentStart].isWhitespace() || line[contentEnd - 1].isWhitespace()) return false
        // CJK emphasis: markers touch CJK on both the outer and inner side.
        val openTouchesCjk = (first > 0 && isCjk(line[first - 1])) || isCjk(line[contentStart])
        val closeTouchesCjk = isCjk(line[contentEnd - 1]) ||
            (second + marker.length < line.length && isCjk(line[second + marker.length]))
        if (openTouchesCjk && closeTouchesCjk) return true
        // ASCII emphasis: a marker flanked by a word character on either side is not a delimiter.
        if (first > 0 && line[first - 1].isLetterOrDigit()) return false
        if (second + marker.length < line.length && line[second + marker.length].isLetterOrDigit()) return false
        return true
    }

    /** True for CJK/CJK-extended ideographs and other non-Latin script characters. */
    private fun isCjk(ch: Char): Boolean = ch.code > 0x2FFF

    /** True when [marker] appears twice surrounding non-blank text (e.g. `` `x` ``, `~~x~~`). */
    private fun hasBalancedPair(line: String, marker: String): Boolean {
        val first = line.indexOf(marker)
        if (first < 0) return false
        val second = line.indexOf(marker, first + marker.length)
        if (second < 0) return false
        return line.substring(first + marker.length, second).isNotBlank()
    }

    /**
     * True when the line contains a `$...$` inline-math span whose content is ASCII-only.
     *
     * CJK-adjacent dollar amounts such as `价格 $5和$6` are ordinary prose, not math, so the
     * content between the dollars must consist entirely of ASCII characters (letters, digits,
     * operators, no whitespace) before the pair counts as math.
     */
    private fun hasInlineMath(line: String): Boolean {
        var from = 0
        while (true) {
            val open = line.indexOf('$', from)
            if (open < 0) return false
            if (open + 1 >= line.length) return false
            val close = line.indexOf('$', open + 1)
            if (close < 0) return false
            val body = line.substring(open + 1, close)
            if (body.isNotEmpty() && body.all { it.code < 128 && !it.isWhitespace() }) return true
            from = close + 1
        }
    }

    private fun unsupported(lineNumber: Int) = ScriptImportException(
        ScriptImportError.UnsupportedMarkdownSyntax,
        lineNumber,
    )

    private data class AtxHeading(val level: Int, val text: String)

    /** Result of a Markdown parse: the suggested title (may be null) plus the document. */
    data class ParseResult(val title: String?, val document: ScriptDocument)
}
