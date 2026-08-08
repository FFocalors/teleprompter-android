package com.zhy20.teleprompter.core.model

/**
 * A character-range selection inside a document, kept Compose-independent so a future
 * editor engine can reuse the same state. [start] and [end] may be in either order;
 * use [min]/[max] for the normalized range and [coerceTo] to clamp it to a document.
 */
data class TextSelection(val start: Int, val end: Int) {
    val min: Int get() = kotlin.math.min(start, end)
    val max: Int get() = kotlin.math.max(start, end)
    val isCollapsed: Boolean get() = start == end

    fun coerceTo(textLength: Int): TextSelection = TextSelection(
        start = start.coerceIn(0, textLength),
        end = end.coerceIn(0, textLength),
    )
}

/**
 * An immutable point-in-time copy of a document and its selection, captured before a
 * text or style change so the editor can restore it when undoing.
 */
data class RichTextSnapshot(
    val document: ScriptDocument,
    val selection: TextSelection,
)

/**
 * The editor's working state: the current [document], the [selection], and a bounded
 * [undoHistory] of snapshots taken before each edit. Mutations are pure — they route
 * through [RichTextDocument] and return a new state — so edits are deterministic and
 * the undo history always reflects the edits that actually happened.
 */
data class RichTextEditorState(
    val document: ScriptDocument,
    val selection: TextSelection = TextSelection(document.plainText().length, document.plainText().length),
    val undoHistory: List<RichTextSnapshot> = emptyList(),
) {
    val text: String get() = document.plainText()
    val canUndo: Boolean get() = undoHistory.isNotEmpty()

    fun toggleStyle(style: ScriptSpanStyle): RichTextEditorState {
        if (selection.isCollapsed) return this
        return copy(
            document = RichTextDocument.toggleStyle(document, selection, style),
            undoHistory = (undoHistory + RichTextSnapshot(document, selection)).takeLast(MaxHistory),
        )
    }

    fun replaceText(newText: String, newSelection: TextSelection): RichTextEditorState {
        if (newText == text && newSelection == selection) return this
        return copy(
            document = RichTextDocument.applyTextChange(document, text, newText),
            selection = newSelection.coerceTo(newText.length),
            undoHistory = (undoHistory + RichTextSnapshot(document, selection)).takeLast(MaxHistory),
        )
    }

    fun withSelection(newSelection: TextSelection): RichTextEditorState = copy(selection = newSelection.coerceTo(text.length))

    fun undo(): RichTextEditorState {
        val previous = undoHistory.lastOrNull() ?: return this
        return copy(document = previous.document, selection = previous.selection, undoHistory = undoHistory.dropLast(1))
    }

    private companion object { const val MaxHistory = 80 }
}

/**
 * Small, deterministic document transformer. It works at character granularity internally,
 * then rebuilds merged spans, so text replacements cannot orphan or overlap style ranges.
 */
object RichTextDocument {
    private data class StyledCharacter(val value: Char, val styles: Set<ScriptSpanStyle>)

    fun toggleStyle(document: ScriptDocument, selection: TextSelection, style: ScriptSpanStyle): ScriptDocument {
        val characters = characters(document)
        val range = selection.coerceTo(characters.size)
        if (range.isCollapsed) return document
        val selected = characters.subList(range.min, range.max)
        val hasText = selected.any { it.value != '\n' }
        if (!hasText) return document
        val shouldAdd = selected.filter { it.value != '\n' }.any { style !in it.styles }
        return toDocument(characters.mapIndexed { index, character ->
            if (index in range.min until range.max && character.value != '\n') {
                character.copy(styles = if (shouldAdd) character.styles + style else character.styles - style)
            } else {
                character
            }
        })
    }

    fun applyTextChange(document: ScriptDocument, oldText: String, newText: String): ScriptDocument {
        if (oldText == newText) return document
        val prefix = commonPrefix(oldText, newText)
        val suffix = commonSuffix(oldText, newText, prefix)
        val source = characters(document)
        val from = prefix.coerceIn(0, source.size)
        val to = (oldText.length - suffix).coerceIn(from, source.size)
        val inserted = newText.substring(prefix, newText.length - suffix).map { StyledCharacter(it, emptySet()) }
        return toDocument(source.take(from) + inserted + source.drop(to))
    }

    fun isStyleFullyApplied(document: ScriptDocument, selection: TextSelection, style: ScriptSpanStyle): Boolean {
        val source = characters(document)
        val range = selection.coerceTo(source.size)
        if (range.isCollapsed) return false
        val selected = source.subList(range.min, range.max).filter { it.value != '\n' }
        return selected.isNotEmpty() && selected.all { style in it.styles }
    }

    fun toAnnotatedSegments(document: ScriptDocument): List<Pair<String, Set<ScriptSpanStyle>>> = document.blocks.flatMapIndexed { blockIndex, block ->
        val paragraph = block as ScriptBlock.Paragraph
        buildList {
            addAll(paragraph.spans.map { it.text to it.styles })
            if (blockIndex != document.blocks.lastIndex) add("\n\n" to emptySet())
        }
    }

    private fun characters(document: ScriptDocument): List<StyledCharacter> = toAnnotatedSegments(document).flatMap { (text, styles) ->
        text.map { StyledCharacter(it, styles) }
    }

    private fun toDocument(characters: List<StyledCharacter>): ScriptDocument {
        val paragraphs = mutableListOf<MutableList<StyledCharacter>>()
        var current = mutableListOf<StyledCharacter>()
        var index = 0
        while (index < characters.size) {
            if (characters[index].value == '\n' && characters.getOrNull(index + 1)?.value == '\n') {
                paragraphs += current
                current = mutableListOf()
                index += 2
            } else {
                current += characters[index]
                index += 1
            }
        }
        paragraphs += current
        return ScriptContent(paragraphs.mapIndexed { paragraphIndex, paragraph ->
            ScriptBlock.Paragraph(
                id = "paragraph-$paragraphIndex",
                spans = mergeCharacters(paragraph).ifEmpty { listOf(ScriptSpan("")) },
            )
        })
    }

    private fun mergeCharacters(characters: List<StyledCharacter>): List<ScriptSpan> = buildList {
        characters.forEach { character ->
            val previous = lastOrNull()
            if (previous != null && previous.styles == character.styles) {
                this[lastIndex] = previous.copy(text = previous.text + character.value)
            } else {
                add(ScriptSpan(character.value.toString(), character.styles))
            }
        }
    }

    private fun commonPrefix(first: String, second: String): Int {
        val limit = minOf(first.length, second.length)
        var index = 0
        while (index < limit && first[index] == second[index]) index += 1
        return index
    }

    private fun commonSuffix(first: String, second: String, prefixLength: Int): Int {
        val limit = minOf(first.length, second.length) - prefixLength
        var count = 0
        while (count < limit && first[first.lastIndex - count] == second[second.lastIndex - count]) count += 1
        return count
    }
}
