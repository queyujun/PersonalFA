package com.yingjing.pfa.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 主题品牌色：随 [AppTheme] 变化的强调色与渐变（深浅已按系统模式解析，调用方无需再判 isDark）。
 * 数据语义色（涨跌 / 分类 / 自定义组合）见 [Color.kt]，不随主题变化。
 *
 * @property primary 品牌主色（与 MaterialTheme.colorScheme.primary 同源；自绘场景取用）。
 * @property gradientStart 页头渐变起始色。
 * @property gradientEnd 页头渐变终止色。
 * @property pageBackground 页面底色（略深于 colorScheme.background，使白色卡片凸显）。
 */
data class BrandColors(
    val primary: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val pageBackground: Color,
)

/** 当前主题品牌色；由 [PersonalFaTheme] 按 [AppTheme] + 系统深浅解析后提供。 */
val LocalBrandColors = staticCompositionLocalOf {
    BrandColors(
        primary = Color(0xFF6E8B9B),
        gradientStart = Color(0xFF6E8B9B),
        gradientEnd = Color(0xFFA3B5A0),
        pageBackground = Color(0xFFE9E9E3),
    )
}

// ── 莫兰迪：灰蓝 / 藕粉 / 灰绿 ──────────────────────────────────────────────
private val MorandiLight = BrandColors(
    primary = Color(0xFF6E8B9B),
    gradientStart = Color(0xFF6E8B9B),
    gradientEnd = Color(0xFFA3B5A0),
    pageBackground = Color(0xFFE9E9E3),
)
private val MorandiDark = BrandColors(
    primary = Color(0xFF8FA8B6),
    gradientStart = Color(0xFF5E7B8B),
    gradientEnd = Color(0xFF93A592),
    pageBackground = Color(0xFF080807),
)

// ── 青瓷：青绿 / 墨青 / 米白 ────────────────────────────────────────────────
private val CeladonLight = BrandColors(
    primary = Color(0xFF4A6B66),
    gradientStart = Color(0xFF4A6B66),
    gradientEnd = Color(0xFF7FA9A3),
    pageBackground = Color(0xFFE9ECE8),
)
private val CeladonDark = BrandColors(
    primary = Color(0xFF7FA9A3),
    gradientStart = Color(0xFF3C5A55),
    gradientEnd = Color(0xFF6E948E),
    pageBackground = Color(0xFF070908),
)

// ── 墨韵：黛 / 烟灰 / 赭 ────────────────────────────────────────────────────
private val InkLight = BrandColors(
    primary = Color(0xFF3A4A4A),
    gradientStart = Color(0xFF3A4A4A),
    gradientEnd = Color(0xFF6B6F70),
    pageBackground = Color(0xFFE8E9EA),
)
private val InkDark = BrandColors(
    primary = Color(0xFF7C8081),
    gradientStart = Color(0xFF445353),
    gradientEnd = Color(0xFF7B7F80),
    pageBackground = Color(0xFF070808),
)

/** 按主题 + 系统深浅解析品牌色。 */
fun brandColorsFor(theme: AppTheme, darkTheme: Boolean): BrandColors = when (theme) {
    AppTheme.MORANDI -> if (darkTheme) MorandiDark else MorandiLight
    AppTheme.CELADON -> if (darkTheme) CeladonDark else CeladonLight
    AppTheme.INK -> if (darkTheme) InkDark else InkLight
}
