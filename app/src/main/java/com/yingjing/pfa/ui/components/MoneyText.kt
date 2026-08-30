package com.yingjing.pfa.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * 金额 / 数字文本：用等宽字体（Monospace）渲染，使数字、千分位逗号、小数点、
 * 货币符号在纵向列表中列对齐（避免比例字体下 "1" 与 "8" 宽度不同导致的错位）。
 *
 * 其余排版（字号、字重、颜色）沿用传入参数，仅把字体族固定为 Monospace。
 * 用法与 [Text] 一致，把显示金额/数字的 [Text] 替换为 [MoneyText] 即可。
 *
 * 多行或需右对齐（如低位对齐）时传 [textAlign]：等宽字体 + 右对齐即可让
 * 多个金额的小数点、个位纵向对齐。
 */
@Composable
fun MoneyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    style: TextStyle = TextStyle.Default,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        fontFamily = FontFamily.Monospace,
        style = style,
        textAlign = textAlign,
    )
}
