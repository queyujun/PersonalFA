package com.yingjing.pfa.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SemanticColorsTest {

    @Test
    fun semanticColorsForDistinguishesDarkAndLight() {
        val light = semanticColorsFor(darkTheme = false)
        val dark = semanticColorsFor(darkTheme = true)
        // 深浅模式应给出两套不同语义色
        assertNotEquals(light, dark)
        assertNotEquals(light.gain, dark.gain)
        assertNotEquals(light.loss, dark.loss)
        assertNotEquals(light.cat1, dark.cat1)
        assertNotEquals(light.cat9, dark.cat9)
        assertNotEquals(light.customCombo, dark.customCombo)
    }

    @Test
    fun semanticColorsLightMatchesLegacyConstants() {
        // 浅色套 = Color.kt 既有常量（零视觉回归）
        val light = semanticColorsFor(darkTheme = false)
        assertEquals(GainRed, light.gain)
        assertEquals(LossGreen, light.loss)
        assertEquals(Cat1, light.cat1)
        assertEquals(Cat2, light.cat2)
        assertEquals(Cat3, light.cat3)
        assertEquals(Cat4, light.cat4)
        assertEquals(Cat5, light.cat5)
        assertEquals(Cat6, light.cat6)
        assertEquals(Cat7, light.cat7)
        assertEquals(Cat8, light.cat8)
        assertEquals(Cat9, light.cat9)
        assertEquals(CustomCombo, light.customCombo)
    }

    @Test
    fun semanticColorsDarkIsBrighterThanLight() {
        // 深色套为提亮变体：每个深色值的亮度（HSL l 分量）应高于对应浅色值
        val light = semanticColorsFor(darkTheme = false)
        val dark = semanticColorsFor(darkTheme = true)
        val pairs = listOf(
            light.gain to dark.gain,
            light.loss to dark.loss,
            light.cat1 to dark.cat1,
            light.cat2 to dark.cat2,
            light.cat3 to dark.cat3,
            light.cat4 to dark.cat4,
            light.cat5 to dark.cat5,
            light.cat6 to dark.cat6,
            light.cat7 to dark.cat7,
            light.cat8 to dark.cat8,
            light.cat9 to dark.cat9,
            light.customCombo to dark.customCombo,
        )
        pairs.forEach { (l, d) -> assert(luminance(d) > luminance(l)) { "dark $d should be brighter than light $l" } }
    }

    @Test
    fun semanticColorsDarkKeepsHueOrderDistinct() {
        // 深色套九分类色两两不同（饼图/走势图依赖色序区分）
        val dark = semanticColorsFor(darkTheme = true)
        val cats = listOf(dark.cat1, dark.cat2, dark.cat3, dark.cat4, dark.cat5, dark.cat6, dark.cat7, dark.cat8, dark.cat9)
        assertEquals(cats.size, cats.toSet().size)
    }

    /** 近似 HSL 亮度 l = (max(r,g,b) + min(r,g,b)) / 2，足够比较明暗。 */
    private fun luminance(c: Color): Float {
        val r = c.red
        val g = c.green
        val b = c.blue
        return (maxOf(r, g, b) + minOf(r, g, b)) / 2f
    }
}
