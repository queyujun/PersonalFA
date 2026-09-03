package com.yingjing.pfa.ui.screens.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** parseMarkdown 解析规则测试：标题/列表/引用/段落合并/漏网语法降级。 */
class ParseMarkdownTest {

    @Test
    fun headings_allLevels() {
        val blocks = parseMarkdown("# H1\n## H2\n### H3\n#### H4")
        assertEquals(
            listOf(
                MdBlock.Heading(1, "H1"),
                MdBlock.Heading(2, "H2"),
                MdBlock.Heading(3, "H3"),
                MdBlock.Heading(3, "H4"), // 深于 3 按最浅约束降级
            ),
            blocks,
        )
    }

    @Test
    fun bullets_and_numbered() {
        val blocks = parseMarkdown("- first\n* star\n1. one\n2) two")
        assertEquals(
            listOf(
                MdBlock.Bullet("first"),
                MdBlock.Bullet("star"),
                MdBlock.Numbered("1", "one"),
                MdBlock.Numbered("2", "two"),
            ),
            blocks,
        )
    }

    @Test
    fun quote_and_paragraph() {
        val blocks = parseMarkdown("> quoted\nplain text")
        assertEquals(
            listOf(
                MdBlock.Quote("quoted"),
                MdBlock.Paragraph("plain text"),
            ),
            blocks,
        )
    }

    @Test
    fun consecutive_paragraph_lines_merge_with_space() {
        val blocks = parseMarkdown("line one\nline two\n\nline three")
        assertEquals(
            listOf(
                MdBlock.Paragraph("line one line two"),
                MdBlock.Paragraph("line three"),
            ),
            blocks,
        )
    }

    @Test
    fun table_row_falls_back_to_paragraph_not_lost() {
        val blocks = parseMarkdown("| a | b |\n|---|---|\n| 1 | 2 |")
        // 漏网表格行不丢内容：全部降级为段落（首两行合并）
        assertTrue(blocks.isNotEmpty())
        assertTrue(blocks.all { it is MdBlock.Paragraph })
        val joined = blocks.joinToString("\n") { (it as MdBlock.Paragraph).text }
        assertTrue(joined.contains("| a | b |"))
        assertTrue(joined.contains("| 1 | 2 |"))
    }

    @Test
    fun blank_and_whitespace_only_lines_split_paragraphs() {
        val blocks = parseMarkdown("a\n   \nb")
        assertEquals(
            listOf(MdBlock.Paragraph("a"), MdBlock.Paragraph("b")),
            blocks,
        )
    }

    @Test
    fun empty_input_returns_empty_list() {
        assertTrue(parseMarkdown("").isEmpty())
        assertTrue(parseMarkdown("\n\n").isEmpty())
    }

    @Test
    fun mixed_document_preserves_order() {
        val md = """
            ## Summary
            Net worth grew.

            - point one
            - point two

            1. action one
            > note
        """.trimIndent()
        val blocks = parseMarkdown(md)
        assertEquals(
            listOf(
                MdBlock.Heading(2, "Summary"),
                MdBlock.Paragraph("Net worth grew."),
                MdBlock.Bullet("point one"),
                MdBlock.Bullet("point two"),
                MdBlock.Numbered("1", "action one"),
                MdBlock.Quote("note"),
            ),
            blocks,
        )
    }
}
