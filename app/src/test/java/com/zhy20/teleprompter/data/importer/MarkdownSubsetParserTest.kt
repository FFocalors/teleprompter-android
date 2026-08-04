package com.zhy20.teleprompter.data.importer

import com.zhy20.teleprompter.core.model.ScriptBlock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Verifies the Markdown subset parser: supported headings/paragraphs convert as specified, and every
 * construct outside the subset aborts with [ScriptImportError.UnsupportedMarkdownSyntax] instead of
 * being partially parsed. Ordinary punctuation must never be mistaken for Markdown.
 */
class MarkdownSubsetParserTest {

    private val parser = MarkdownSubsetParser()

    private fun parse(text: String): MarkdownSubsetParser.ParseResult = parser.parse(text)

    private fun assertUnsupported(text: String) {
        try {
            parse(text)
            fail("Expected UnsupportedMarkdownSyntax for:\n$text")
        } catch (e: ScriptImportException) {
            assertEquals(ScriptImportError.UnsupportedMarkdownSyntax, e.error)
        }
    }

    private fun bodyText(blocks: List<ScriptBlock>): String = blocks.joinToString("\n") { (it as ScriptBlock.Paragraph).spans.joinToString("") { s -> s.text } }

    // --- Supported: headings and body ---

    @Test
    fun atxH1_becomesTitle_andIsRemovedFromBody() {
        val result = parse("# 我的台本\n\n第一段。")
        assertEquals("我的台本", result.title)
        assertEquals("第一段。", bodyText(result.document.blocks))
    }

    @Test
    fun atxH1_withClosingHashes_stripsClosing() {
        val result = parse("# 我的台本 #\n\n正文")
        assertEquals("我的台本", result.title)
        assertEquals("正文", bodyText(result.document.blocks))
    }

    @Test
    fun setextH1_becomesTitle_andIsRemovedFromBody() {
        val result = parse("我的台本\n========\n\n第一段。")
        assertEquals("我的台本", result.title)
        assertEquals("第一段。", bodyText(result.document.blocks))
    }

    @Test
    fun multipleH1_firstIsTitle_restAreBodyParagraphs() {
        val result = parse("# 标题\n\n正文\n\n# 第二章节\n\n更多")
        assertEquals("标题", result.title)
        assertEquals("正文\n第二章节\n更多", bodyText(result.document.blocks))
    }

    @Test
    fun noH1_titleFallsBackToNull() {
        val result = parse("普通正文")
        assertEquals(null, result.title)
    }

    @Test
    fun atxH2toH6_becomePlainBodyParagraphs() {
        val result = parse("## 第二部分\n\n### 第三部分\n\n正文")
        assertEquals(null, result.title)
        assertEquals("第二部分\n第三部分\n正文", bodyText(result.document.blocks))
    }

    @Test
    fun setextH2_becomesPlainBodyParagraph() {
        val result = parse("第二部分\n--------\n\n正文")
        assertEquals(null, result.title)
        assertEquals("第二部分\n正文", bodyText(result.document.blocks))
    }

    @Test
    fun paragraphs_separatedByBlankLines() {
        val result = parse("第一段。\n\n第二段。\n\n\n第三段。")
        assertEquals(3, result.document.blocks.size)
        assertEquals("第一段。\n第二段。\n第三段。", bodyText(result.document.blocks))
    }

    @Test
    fun singleLineBreakInsideParagraph_isPreserved() {
        val result = parse("第一行\n第二行")
        assertEquals(1, result.document.blocks.size)
        assertEquals("第一行\n第二行", bodyText(result.document.blocks))
    }

    @Test
    fun mixedChineseEnglish_preserved() {
        val result = parse("欢迎来到提词器。Welcome to the teleprompter.\n第二段 English text.")
        assertEquals(1, result.document.blocks.size)
        assertEquals("欢迎来到提词器。Welcome to the teleprompter.\n第二段 English text.", bodyText(result.document.blocks))
    }

    @Test
    fun everyParagraphHasAtLeastOneSpan_noInlineStyles() {
        val result = parse("一段。\n\n## 标题\n\n二段。")
        result.document.blocks.forEach { block ->
            val paragraph = block as ScriptBlock.Paragraph
            assertTrue(paragraph.spans.isNotEmpty())
            paragraph.spans.forEach { assertTrue(it.styles.isEmpty()) }
        }
    }

    @Test
    fun paragraphIds_areUniqueAndPrefixed() {
        val result = parse("一\n\n二\n\n三")
        val ids = result.document.blocks.map { (it as ScriptBlock.Paragraph).id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("md-") })
    }

    // --- Unsupported: block-level constructs ---

    @Test
    fun bold_isRejected() = assertUnsupported("这是**粗体**文字")
    @Test
    fun italic_isRejected() = assertUnsupported("这是*斜体*文字")
    @Test
    fun italicUnderscore_isRejected() = assertUnsupported("这是_斜体_文字")
    @Test
    fun strikethrough_isRejected() = assertUnsupported("这是~~删除线~~")
    @Test
    fun unorderedListDash_isRejected() = assertUnsupported("- 项目")
    @Test
    fun unorderedListStar_isRejected() = assertUnsupported("* 项目")
    @Test
    fun unorderedListPlus_isRejected() = assertUnsupported("+ 项目")
    @Test
    fun orderedListDot_isRejected() = assertUnsupported("1. 项目")
    @Test
    fun orderedListParen_isRejected() = assertUnsupported("1) 项目")
    @Test
    fun taskList_isRejected() = assertUnsupported("- [ ] 任务")
    @Test
    fun taskListChecked_isRejected() = assertUnsupported("- [x] 任务")
    @Test
    fun blockquote_isRejected() = assertUnsupported("> 引用")
    @Test
    fun fencedCodeBacktick_isRejected() = assertUnsupported("```kotlin\n内容\n```")
    @Test
    fun fencedCodeTilde_isRejected() = assertUnsupported("~~~\n内容\n~~~")
    @Test
    fun indentedCode_isRejected() = assertUnsupported("    缩进代码")
    @Test
    fun inlineCode_isRejected() = assertUnsupported("使用 `code` 示例")
    @Test
    fun link_isRejected() = assertUnsupported("[链接](https://example.com)")
    @Test
    fun image_isRejected() = assertUnsupported("![图片](image.png)")
    @Test
    fun autolink_isRejected() = assertUnsupported("<https://example.com>")
    @Test
    fun table_isRejected() = assertUnsupported("| 列一 | 列二 |")
    @Test
    fun tableSeparator_isRejected() = assertUnsupported("| --- | --- |")
    @Test
    fun htmlTag_isRejected() = assertUnsupported("<div>内容</div>")
    @Test
    fun htmlComment_isRejected() = assertUnsupported("<!-- 注释 -->")
    @Test
    fun yamlFrontMatter_isRejected() = assertUnsupported("---\ntitle: 示例\n---")
    @Test
    fun footnoteDefinition_isRejected() = assertUnsupported("[^1]: 脚注内容")
    @Test
    fun footnoteReference_isRejected() = assertUnsupported("正文[^1]内容")
    @Test
    fun horizontalRuleDash_isRejected() = assertUnsupported("---")
    @Test
    fun horizontalRuleStar_isRejected() = assertUnsupported("***")
    @Test
    fun horizontalRuleUnderscore_isRejected() = assertUnsupported("___")
    @Test
    fun mathDisplay_isRejected() = assertUnsupported("\$\$ x + y \$\$")
    @Test
    fun mathInline_isRejected() = assertUnsupported("公式 \$x^2\$ 内容")

    // --- Ordinary punctuation must pass ---

    @Test
    fun csharpPound_isAllowed() {
        val result = parse("C# 是一种语言")
        assertEquals("C# 是一种语言", bodyText(result.document.blocks))
    }

    @Test
    fun versionNumber_isAllowed() {
        val result = parse("版本号是 1.0-beta")
        assertEquals("版本号是 1.0-beta", bodyText(result.document.blocks))
    }

    @Test
    fun underscoreInNumber_isAllowed() {
        val result = parse("金额为 100_000 元")
        assertEquals("金额为 100_000 元", bodyText(result.document.blocks))
    }

    @Test
    fun asteriskOperators_isAllowed() {
        val result = parse("结果是 A * B")
        assertEquals("结果是 A * B", bodyText(result.document.blocks))
    }

    @Test
    fun bracketContent_isAllowed() {
        val result = parse("数组内容为 [1, 2, 3]")
        assertEquals("数组内容为 [1, 2, 3]", bodyText(result.document.blocks))
    }

    @Test
    fun pipeInProse_isAllowed() {
        val result = parse("a | b")
        assertEquals("a | b", bodyText(result.document.blocks))
    }

    @Test
    fun hashInProse_isAllowed() {
        val result = parse("标签 #笔记")
        assertEquals("标签 #笔记", bodyText(result.document.blocks))
    }

    @Test
    fun headingWithBold_isRejected() = assertUnsupported("# **粗体标题**")

    @Test
    fun dollarAmountsWithSpaces_isAllowed() {
        val result = parse("价格 $5 和 $6")
        assertEquals("价格 $5 和 $6", bodyText(result.document.blocks))
    }

    @Test
    fun setextUnderlineInsideParagraph_notAHeadingAndRejectedAsRule() = assertUnsupported("---")

    @Test
    fun wordBoundaryUnderscore_isNotItalic() {
        val result = parse("TCP_IP_设置 和 user_id_验证 以及 a_b_c")
        assertEquals("TCP_IP_设置 和 user_id_验证 以及 a_b_c", bodyText(result.document.blocks))
    }

    @Test
    fun wordBoundaryAsterisk_isNotItalic() {
        val result = parse("2*3*4 等于 24")
        assertEquals("2*3*4 等于 24", bodyText(result.document.blocks))
    }

    @Test
    fun spacePaddedAsterisk_isNotItalic() {
        val result = parse("结果是 A * B 或者 C * D")
        assertEquals("结果是 A * B 或者 C * D", bodyText(result.document.blocks))
    }

    @Test
    fun emptyHeadingText_addsNothingToBody() {
        val result = parse("第一段。\n\n## \n\n第二段。")
        assertEquals("第一段。\n第二段。", bodyText(result.document.blocks))
    }

    @Test
    fun cjkAdjacentDollarAmount_isNotMath() {
        val result = parse("价格 $5和$6")
        assertEquals("价格 $5和$6", bodyText(result.document.blocks))
    }

    @Test
    fun separatedIdentifiersWithUnderscores_isNotItalic() {
        val result = parse("user_id 和 user_name")
        assertEquals("user_id 和 user_name", bodyText(result.document.blocks))
    }

    @Test
    fun multipleUnderscoreIdentifiers_isNotItalic() {
        val result = parse("100_000_200 元")
        assertEquals("100_000_200 元", bodyText(result.document.blocks))
    }

    @Test
    fun selfClosingHtmlTag_isRejected() = assertUnsupported("换行 <br/> 标签")
}
