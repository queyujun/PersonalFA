package com.yingjing.pfa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * 应用主题入口。
 *
 * - 深浅模式默认跟随系统（[isSystemInDarkTheme]），可由调用方覆盖。
 * - [themeId] 选择四套配色之一（紫晶 / 霁蓝 / 松石 / 琥珀），见 [AppTheme]；
 *   未知值回退默认紫晶，旧 id（morandi/celadon/ink）经别名映射，已存偏好不丢回默认。
 * - 暴露 [LocalBrandColors]：自绘场景取用品牌主色 / 渐变 / 页面底色，替代硬编码的旧品牌蓝。
 *
 * 数据语义色（涨跌 / 分类 / 自定义组合）不随主题变化，直接复用 [Color.kt] 顶层常量。
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

    CompositionLocalProvider(LocalBrandColors provides brand) {
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
