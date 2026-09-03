package com.yingjing.pfa.ui.screens.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 轻量 Markdown 渲染（零第三方依赖）。
 *
 * AI 输出受提示词约束：只有 `##`/`###` 标题、`-` 要点、`1.` 有序列表、普通段落与 `>` 引用；
 * 本渲染器按行解析为块（[MdBlock]），行内仅支持 `**加粗**`。表格行等漏网语法降级为普通段落，
 * 不丢内容。由 [AiPromptBuilder] 的「禁止表格/代码围栏」规则兜底，这里只做防御性降级。
 */

/** 解析出的一个块级元素；[text] 已剥去标记符，行内标记在渲染时处理。 */
sealed interface MdBlock {
    /** `#`~`###` 标题；[level] 为 1~3。 */
    data class Heading(val level: Int, val text: String) : MdBlock

    /** `-` 无序列表项。 */
    data class Bullet(val text: String) : MdBlock

    /** `1.` 有序列表项；[number] 为原始序号文本。 */
    data class Numbered(val number: String, val text: String) : MdBlock

    /** `>` 引用。 */
    data class Quote(val text: String) : MdBlock

    /** 普通段落（含降级的表格行等）。 */
    data class Paragraph(val text: String) : MdBlock
}

private val NUMBERED_LINE = Regex("""^(\d{1,3})[.)]\s+(.*)$""")

/**
 * 把 Markdown 文本按行解析为块列表（纯函数）。
 *
 * 规则：`#`/`##`/`###` → Heading（更深级别 `####`+ 按 3 处理并剥净 `#` 前缀）；`-`/`*` 开头 → Bullet；
 * `数字.`/`数字)` 开头 → Numbered；`>` 开头 → Quote；其余非空行 → Paragraph。
 * 连续普通行合并为一段（AI 输出中多见换行硬折行，合并更接近自然排版）。
 */
fun parseMarkdown(md: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(paragraph.toString())
            paragraph.clear()
        }
    }

    md.lines().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isEmpty() -> flushParagraph()
            line.startsWith("####") -> {
                flushParagraph()
                // 深于约束的级别（####+）：降级为 3 级，剥掉全部 # 前缀避免残留
                blocks += MdBlock.Heading(3, line.trimStart('#').trim())
            }
            line.startsWith("###") -> {
                flushParagraph()
                blocks += MdBlock.Heading(3, line.removePrefix("###").trim())
            }
            line.startsWith("##") -> {
                flushParagraph()
                blocks += MdBlock.Heading(2, line.removePrefix("##").trim())
            }
            line.startsWith("#") -> {
                flushParagraph()
                blocks += MdBlock.Heading(1, line.removePrefix("#").trim())
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                flushParagraph()
                blocks += MdBlock.Bullet(line.substring(2).trim())
            }
            NUMBERED_LINE.containsMatchIn(line) -> {
                flushParagraph()
                val match = NUMBERED_LINE.find(line)!!
                blocks += MdBlock.Numbered(match.groupValues[1], match.groupValues[2].trim())
            }
            line.startsWith(">") -> {
                flushParagraph()
                blocks += MdBlock.Quote(line.removePrefix(">").trim())
            }
            else -> {
                // 段落内换行合并为空格；表格行（| 开头）等漏网语法一并落入段落，不丢内容。
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line)
            }
        }
    }
    flushParagraph()
    return blocks
}

/**
 * 把行内 `**加粗**` 渲染为 AnnotatedString（纯函数）。未闭合的 `**` 按原文保留。
 */
internal fun boldSpans(text: String) = buildAnnotatedString {
    append(text)
    var from = 0
    while (true) {
        val start = text.indexOf("**", from)
        if (start < 0) break
        val end = text.indexOf("**", start + 2)
        if (end < 0) break
        addStyle(
            SpanStyle(fontWeight = FontWeight.Bold),
            start,
            end + 2,
        )
        from = end + 2
    }
}

/** Markdown 块列表 → Compose 纵向排版。 */
@Composable
fun AiMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    Column(modifier = modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = boldSpans(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
                is MdBlock.Bullet -> Text(
                    text = boldSpans(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
                is MdBlock.Numbered -> Text(
                    text = boldSpans("${block.number}. ${block.text}"),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
                is MdBlock.Quote -> Text(
                    text = boldSpans(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
                is MdBlock.Paragraph -> Text(
                    text = boldSpans(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
