package com.yingjing.pfa.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 数据语义色（涨跌 / 分类 / 自定义组合）：按深浅模式提供两套取值，
 * 浅色 = [Color.kt] 既有常量（零视觉回归），深色 = 往亮调的变体（在暗背景上保证对比度）。
 *
 * 与 [BrandColors] 同构：由 [PersonalFaTheme] 解析后经 [LocalSemanticColors] 分发，
 * 自绘图表 / 纯函数取色统一从 Local 读取，不直接引用顶层常量（顶层常量保留作浅色值）。
 *
 * 中国市场惯例：红涨 / 绿跌，两套模式均保持该语义。
 */
data class SemanticColors(
    val gain: Color,
    val loss: Color,
    val cat1: Color, // 房产
    val cat2: Color, // 存款
    val cat3: Color, // 股票
    val cat4: Color, // 黄金
    val cat5: Color, // 国债
    val cat6: Color, // 区块链
    val cat7: Color, // 公司股权
    val cat8: Color, // 场外基金
    val cat9: Color, // 其他
    val customCombo: Color,
)

/** 当前语义色；由 [PersonalFaTheme] 按深浅模式解析后提供。默认浅色套 = 既有常量。 */
val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColorsLight
}

/** 浅色套：直接复用 [Color.kt] 顶层常量，视觉与既有版本完全一致。 */
val SemanticColorsLight = SemanticColors(
    gain = GainRed,
    loss = LossGreen,
    cat1 = Cat1,
    cat2 = Cat2,
    cat3 = Cat3,
    cat4 = Cat4,
    cat5 = Cat5,
    cat6 = Cat6,
    cat7 = Cat7,
    cat8 = Cat8,
    cat9 = Cat9,
    customCombo = CustomCombo,
)

/**
 * 深色套：同色相往亮提（对齐深色 error 0xFFE34948 与 BrandColors 深色粉彩的亮度带），
 * 九分类色保持色相错开，走势 / 饼图在暗背景上依旧可辨。
 */
val SemanticColorsDark = SemanticColors(
    gain = Color(0xFFEF6E6E),
    loss = Color(0xFF57D867),
    cat1 = Color(0xFF9CC8EC), // 房产 — 浅天蓝提亮
    cat2 = Color(0xFFF2BC93), // 存款 — 浅橙提亮
    cat3 = Color(0xFF9BD8B2), // 股票 — 浅草绿提亮
    cat4 = Color(0xFFF4D488), // 黄金 — 浅金黄提亮
    cat5 = Color(0xFFEAAEBF), // 国债 — 浅玫粉提亮
    cat6 = Color(0xFFCED98A), // 区块链 — 浅黄绿提亮
    cat7 = Color(0xFFB4A9E4), // 公司股权 — 浅紫提亮
    cat8 = Color(0xFF8CDCCB), // 场外基金 — 浅松青提亮
    cat9 = Color(0xFFB9C0CB), // 其他 — 浅灰蓝提亮
    customCombo = Color(0xFFD98FD3),
)

/** 按深浅模式解析语义色。 */
fun semanticColorsFor(darkTheme: Boolean): SemanticColors =
    if (darkTheme) SemanticColorsDark else SemanticColorsLight
