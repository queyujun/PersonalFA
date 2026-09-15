package com.yingjing.pfa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 应用主题入口。
 *
 * - 深浅模式默认跟随系统（[isSystemInDarkTheme]），可由调用方覆盖。
 * - [themeId] 选择四套配色之一（紫晶 / 霁蓝 / 松石 / 琥珀），见 [AppTheme]；
 *   未知值回退默认紫晶，旧 id（morandi/celadon/ink）经别名映射，已存偏好不丢回默认。
 * - 暴露 [LocalBrandColors]：自绘场景取用品牌主色 / 渐变 / 页面底色，替代硬编码的旧品牌蓝。
 * - 暴露 [LocalSemanticColors]：涨跌 / 分类 / 自定义组合语义色按深浅模式取值，
 *   深色为提亮变体（图表在暗背景上的对比度），浅色与 [Color.kt] 常量一致。
 * - 暴露 [LocalDarkTheme]：最终生效的深浅模式（含 App 内强制档），供需要按深浅
 *   切换资源的场景读取（如顶栏 logo 的白字变体）——不能用 `-night` 资源限定符，
 *   因为强制档只改 Compose 主题、不改变 Activity Configuration 的 uiMode。
 */
@Composable
fun PersonalFaTheme(
    themeId: String?,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val theme = AppTheme.fromId(themeId)
    val colorScheme = colorSchemeFor(theme, darkTheme)
    val brand = brandColorsFor(theme, darkTheme)
    val semantic = semanticColorsFor(darkTheme)

    CompositionLocalProvider(
        LocalBrandColors provides brand,
        LocalSemanticColors provides semantic,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

/** 兼容旧调用（无主题参数，按默认紫晶渲染）；新代码应改用带 themeId 的重载。 */
@Composable
fun PersonalFaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) = PersonalFaTheme(themeId = AppTheme.VIOLET.id, darkTheme = darkTheme, content = content)

/** 当前是否深色模式（含 App 内强制档）；由 [PersonalFaTheme] 提供，默认浅色。 */
val LocalDarkTheme = staticCompositionLocalOf { false }
