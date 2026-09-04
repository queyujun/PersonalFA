package com.yingjing.pfa.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 轻量 Markdown 渲染（零第三方依赖）。
 *
 * AI 输出受提示词约束：`##`/`###` 标题、`-` 要点、`1.` 有序列表、`---` 分隔线，
 * 关键数字与结论用 `**加粗**` 强调；行内另支持 `*斜体*`、`~~删除线~~`、`` `代码` `` 的防御性渲染。
 * 表格行等漏网语法降级为普通段落，不丢内容。由 [AiPromptBuilder] 的「禁止表格/代码围栏」
 * 规则兜底，这里只做防御性降级。
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

    /** `---`/`***`/`___` 水平分隔线。 */
    data object Rule : MdBlock

    /** 普通段落（含降级的表格行等）。 */
    data class Paragraph(val text: String) : MdBlock
}

private val NUMBERED_LINE = Regex("""^(\d{1,3})[.)]\s+(.*)$""")
private val RULE_LINE = Regex("""^[-*_]{3,}$""")

/**
 * 把 Markdown 文本按行解析为块列表（纯函数）。
 *
 * 规则：`#`/`##`/`###` → Heading（更深级别 `####`+ 按 3 处理并剥净 `#` 前缀）；`-`/`*` 开头 → Bullet；
 * `数字.`/`数字)` 开头 → Numbered；`>` 开头 → Quote；整行 `---`/`***`/`___` → Rule；
 * 其余非空行 → Paragraph。连续普通行合并为一段（AI 输出中多见换行硬折行，合并更接近自然排版）。
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
            RULE_LINE.matches(line) -> {
                flushParagraph()
                blocks += MdBlock.Rule
            }
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

/** 行内标记：[open]/[close] 定界符；[styleFor] 生成内容样式（code 可带浅底色）；[literal] 内容不再嵌套解析。 */
private data class InlineMarker(
    val open: String,
    val close: String,
    val styleFor: (Color?) -> SpanStyle,
    val literal: Boolean = false,
)

private val INLINE_MARKERS = listOf(
    // `**` 必须先于 `*` 匹配，否则加粗会被斜体规则截胡
    InlineMarker(
        open = "**",
        close = "**",
        styleFor = { SpanStyle(fontWeight = FontWeight.Bold) },
    ),
    InlineMarker(
        open = "~~",
        close = "~~",
        styleFor = { SpanStyle(textDecoration = TextDecoration.LineThrough) },
    ),
    InlineMarker(
        open = "`",
        close = "`",
        literal = true,
        styleFor = { bg ->
            SpanStyle(fontFamily = FontFamily.Monospace, background = bg ?: Color.Unspecified)
        },
    ),
    InlineMarker(
        open = "*",
        close = "*",
        styleFor = { SpanStyle(fontStyle = FontStyle.Italic) },
    ),
)

/**
 * 行内标记 → [AnnotatedString]：剥去定界符并把样式应用到内容（星号/波浪线/反引号不显示）。
 * 逐位置扫描、先长定界符优先；斜体/加粗等内容要求首尾非空格（`a * b * c` 不误判）；
 * 未闭合或不满足条件的标记按原文保留。非 literal 内容递归解析以支持嵌套（如粗中带斜）。
 */
internal fun inlineStyled(text: String, codeBackground: Color? = null): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val marker = INLINE_MARKERS.firstOrNull { text.startsWith(it.open, i) }
        val contentStart = i + (marker?.open?.length ?: 0)
        val closeIdx = marker?.let { m -> text.indexOf(m.close, contentStart) }
        val content = if (marker != null && closeIdx != null && closeIdx > contentStart) {
            text.substring(contentStart, closeIdx)
        } else {
            null
        }
        if (marker != null && closeIdx != null && content != null &&
            (marker.literal || (content.first() != ' ' && content.last() != ' '))
        ) {
            withStyle(marker.styleFor(codeBackground)) {
                append(if (marker.literal) content else inlineStyled(content, codeBackground))
            }
            i = closeIdx + marker.close.length
        } else {
            append(text[i])
            i++
        }
    }
}

/** Markdown 块列表 → Compose 纵向排版（标题/列表/引用/分隔线/段落分层呈现）。 */
@Composable
fun AiMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> HeadingBlock(block, codeBackground)
                is MdBlock.Bullet -> MarkerRow(
                    marker = "•",
                    text = block.text,
                    codeBackground = codeBackground,
                )
                is MdBlock.Numbered -> MarkerRow(
                    marker = "${block.number}.",
                    text = block.text,
                    codeBackground = codeBackground,
                )
                is MdBlock.Quote -> QuoteBlock(block, codeBackground)
                is MdBlock.Rule -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                is MdBlock.Paragraph -> Text(
                    text = inlineStyled(block.text, codeBackground),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** 标题：H1 大字、H2 主色竖条、H3 小字，均加粗并留出与上文的间隔。 */
@Composable
private fun HeadingBlock(block: MdBlock.Heading, codeBackground: Color) {
    when (block.level) {
        1 -> Text(
            text = inlineStyled(block.text, codeBackground),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        2 -> Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(top = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(vertical = 3.dp)
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = inlineStyled(block.text, codeBackground),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        else -> Text(
            text = inlineStyled(block.text, codeBackground),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 列表行：符号列（• / 序号）着主色，内容悬挂缩进（折行后与内容对齐）。 */
@Composable
private fun MarkerRow(marker: String, text: String, codeBackground: Color) {
    val bodyStyle = MaterialTheme.typography.bodyMedium
    Row(modifier = Modifier.padding(start = 4.dp)) {
        Text(
            text = marker,
            style = bodyStyle,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = inlineStyled(text, codeBackground),
            style = bodyStyle,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 引用块：左侧灰竖线 + 变淡文字。 */
@Composable
private fun QuoteBlock(block: MdBlock.Quote, codeBackground: Color) {
    Row(
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .padding(start = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 2.dp)
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = inlineStyled(block.text, codeBackground),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
