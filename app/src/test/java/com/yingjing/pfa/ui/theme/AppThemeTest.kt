package com.yingjing.pfa.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeTest {

    @Test
    fun fromIdResolvesKnownIds() {
        assertEquals(AppTheme.MORANDI, AppTheme.fromId("morandi"))
        assertEquals(AppTheme.CELADON, AppTheme.fromId("celadon"))
        assertEquals(AppTheme.INK, AppTheme.fromId("ink"))
    }

    @Test
    fun fromIdFallsBackToMorandiForNull() {
        assertEquals(AppTheme.MORANDI, AppTheme.fromId(null))
    }

    @Test
    fun fromIdFallsBackToMorandiForUnknownId() {
        // 旧版本主题名 / 拼写错误 / 任意字符串 → 回退默认，不抛异常
        assertEquals(AppTheme.MORANDI, AppTheme.fromId("vintage"))
        assertEquals(AppTheme.MORANDI, AppTheme.fromId(""))
        assertEquals(AppTheme.MORANDI, AppTheme.fromId("MORANDI"))
    }
}
