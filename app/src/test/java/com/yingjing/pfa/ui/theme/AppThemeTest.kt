package com.yingjing.pfa.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeTest {

    @Test
    fun fromIdResolvesKnownIds() {
        assertEquals(AppTheme.VIOLET, AppTheme.fromId("violet"))
        assertEquals(AppTheme.BLUE, AppTheme.fromId("blue"))
        assertEquals(AppTheme.GREEN, AppTheme.fromId("green"))
        assertEquals(AppTheme.ORANGE, AppTheme.fromId("orange"))
    }

    @Test
    fun fromIdFallsBackToVioletForNull() {
        assertEquals(AppTheme.VIOLET, AppTheme.fromId(null))
    }

    @Test
    fun fromIdMapsLegacyAliases() {
        // 旧版 id（morandi/celadon/ink）经别名映射到最接近的新主题，已存偏好不丢回默认。
        assertEquals(AppTheme.BLUE, AppTheme.fromId("morandi"))
        assertEquals(AppTheme.GREEN, AppTheme.fromId("celadon"))
        assertEquals(AppTheme.VIOLET, AppTheme.fromId("ink"))
    }

    @Test
    fun fromIdFallsBackToVioletForUnknownId() {
        // 拼写错误 / 任意字符串 → 回退默认紫晶，不抛异常
        assertEquals(AppTheme.VIOLET, AppTheme.fromId("vintage"))
        assertEquals(AppTheme.VIOLET, AppTheme.fromId(""))
        assertEquals(AppTheme.VIOLET, AppTheme.fromId("VIOLET"))
    }
}
