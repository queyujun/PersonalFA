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
        primary = Color(0xFF5B4B8A),
        gradientStart = Color(0xFF5B4B8A),
        gradientEnd = Color(0xFF9C8FB8),
        pageBackground = Color(0xFFEEEAF6),
    )
}

// ── 紫晶：深紫罗兰 / 薰衣草 ─────────────────────────────────────────────────
private val VioletLight = BrandColors(
    primary = Color(0xFF5B4B8A),
    gradientStart = Color(0xFF5B4B8A),
    gradientEnd = Color(0xFF9C8FB8),
    pageBackground = Color(0xFFEEEAF6),
)
private val VioletDark = BrandColors(
    primary = Color(0xFFCDBAFF),
    gradientStart = Color(0xFF4A3878),
    gradientEnd = Color(0xFF7A68A8),
    pageBackground = Color(0xFF0F0E18),
)

// ── 霁蓝：霁青蓝 / 天青 ─────────────────────────────────────────────────────
private val BlueLight = BrandColors(
    primary = Color(0xFF1F5C8B),
    gradientStart = Color(0xFF1F5C8B),
    gradientEnd = Color(0xFF86A6C2),
    pageBackground = Color(0xFFEAEFF5),
)
private val BlueDark = BrandColors(
    primary = Color(0xFF8FCBFB),
    gradientStart = Color(0xFF1E4E73),
    gradientEnd = Color(0xFF3A6E91),
    pageBackground = Color(0xFF0E1216),
)

// ── 松石：松石绿 / 浅艾 ─────────────────────────────────────────────────────
private val GreenLight = BrandColors(
    primary = Color(0xFF1F6E5D),
    gradientStart = Color(0xFF1F6E5D),
    gradientEnd = Color(0xFF7CA89A),
    pageBackground = Color(0xFFEAF2EE),
)
private val GreenDark = BrandColors(
    primary = Color(0xFF8FD8C4),
    gradientStart = Color(0xFF1E5B4D),
    gradientEnd = Color(0xFF3D7A6A),
    pageBackground = Color(0xFF0E1411),
)

// ── 琥珀：琥珀橙 / 浅驼 ─────────────────────────────────────────────────────
private val OrangeLight = BrandColors(
    primary = Color(0xFF9A5520),
    gradientStart = Color(0xFF9A5520),
    gradientEnd = Color(0xFFB98E6E),
    pageBackground = Color(0xFFF4EDE6),
)
private val OrangeDark = BrandColors(
    primary = Color(0xFFF2B97A),
    gradientStart = Color(0xFF7A4515),
    gradientEnd = Color(0xFFA06F3E),
    pageBackground = Color(0xFF14100C),
)

/** 按主题 + 系统深浅解析品牌色。 */
fun brandColorsFor(theme: AppTheme, darkTheme: Boolean): BrandColors = when (theme) {
    AppTheme.VIOLET -> if (darkTheme) VioletDark else VioletLight
    AppTheme.BLUE -> if (darkTheme) BlueDark else BlueLight
    AppTheme.GREEN -> if (darkTheme) GreenDark else GreenLight
    AppTheme.ORANGE -> if (darkTheme) OrangeDark else OrangeLight
}
