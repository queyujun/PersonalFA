package com.yingjing.pfa.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BrandColorsTest {

    @Test
    fun brandColorsForMorandiDiffersByDarkTheme() {
        val light = brandColorsFor(AppTheme.MORANDI, darkTheme = false)
        val dark = brandColorsFor(AppTheme.MORANDI, darkTheme = true)
        // 深浅模式应给出不同的品牌色与页面底色
        assertNotEquals(light, dark)
        assertNotEquals(light.pageBackground, dark.pageBackground)
    }

    @Test
    fun brandColorsForDistinguishesThemes() {
        // 三主题浅色各不相同
        val morandi = brandColorsFor(AppTheme.MORANDI, darkTheme = false)
        val celadon = brandColorsFor(AppTheme.CELADON, darkTheme = false)
        val ink = brandColorsFor(AppTheme.INK, darkTheme = false)
        assertNotEquals(morandi, celadon)
        assertNotEquals(morandi, ink)
        assertNotEquals(celadon, ink)
    }

    @Test
    fun brandColorsForMatchesExpectedMorandiLightPrimary() {
        // 莫兰迪浅色品牌主色为灰蓝 #6E8B9B（设计稿确认色板）
        val morandi = brandColorsFor(AppTheme.MORANDI, darkTheme = false)
        assertEquals(Color(0xFF6E8B9B), morandi.primary)
    }
}
