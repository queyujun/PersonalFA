package com.yingjing.pfa.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BrandColorsTest {

    @Test
    fun brandColorsForVioletDiffersByDarkTheme() {
        val light = brandColorsFor(AppTheme.VIOLET, darkTheme = false)
        val dark = brandColorsFor(AppTheme.VIOLET, darkTheme = true)
        // 深浅模式应给出不同的品牌色与页面底色
        assertNotEquals(light, dark)
        assertNotEquals(light.pageBackground, dark.pageBackground)
    }

    @Test
    fun brandColorsForDistinguishesThemes() {
        // 四主题浅色各不相同
        val violet = brandColorsFor(AppTheme.VIOLET, darkTheme = false)
        val blue = brandColorsFor(AppTheme.BLUE, darkTheme = false)
        val green = brandColorsFor(AppTheme.GREEN, darkTheme = false)
        val orange = brandColorsFor(AppTheme.ORANGE, darkTheme = false)
        assertNotEquals(violet, blue)
        assertNotEquals(violet, green)
        assertNotEquals(violet, orange)
        assertNotEquals(blue, green)
        assertNotEquals(green, orange)
        assertNotEquals(blue, orange)
    }

    @Test
    fun brandColorsForMatchesExpectedVioletLightPrimary() {
        // 紫晶浅色品牌主色为深紫罗兰 #5B4B8A（设计稿确认色板）
        val violet = brandColorsFor(AppTheme.VIOLET, darkTheme = false)
        assertEquals(Color(0xFF5B4B8A), violet.primary)
    }
}
