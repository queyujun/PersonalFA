package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.model.CategoryPoint
import com.yingjing.pfa.domain.model.NetWorthPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class TrendSeriesBuilderTest {

    // epochDay 20000 = 2024-10-04；用几天跨月数据
    private val d1 = 20000L // 2024-10-04
    private val d2 = 20001L // 2024-10-05
    private val d3 = 20035L // 2024-11-08

    private val totals = listOf(
        NetWorthPoint(d1, 1000.0),
        NetWorthPoint(d2, 1100.0),
        NetWorthPoint(d3, 1200.0),
    )
    private val categories = listOf(
        CategoryPoint(d1, "STOCK", 400.0),
        CategoryPoint(d2, "STOCK", 450.0),
        CategoryPoint(d3, "STOCK", 500.0),
    )

    private val label: (String) -> String = { it }

    @Test
    fun daily_keepsEveryDay() {
        val data = TrendSeriesBuilder.build(totals, categories, listOf(TREND_TOTAL_ID), TimeGranularity.DAY, label, totalLabel = "总净值", customLabel = TREND_CUSTOM_LABEL)
        assertEquals(3, data.bucketLabels.size)
        assertEquals(listOf(1000.0, 1100.0, 1200.0), data.series[0].values)
    }

    @Test
    fun monthly_takesLastValuePerMonth() {
        val data = TrendSeriesBuilder.build(totals, categories, listOf(TREND_TOTAL_ID), TimeGranularity.MONTH, label, totalLabel = "总净值", customLabel = TREND_CUSTOM_LABEL)
        // 10 月两天取最后一天(1100)，11 月一天(1200)
        assertEquals(2, data.bucketLabels.size)
        assertEquals(listOf(1100.0, 1200.0), data.series[0].values)
    }

    @Test
    fun multiSeries_totalAndCategory() {
        val data = TrendSeriesBuilder.build(
            totals, categories, listOf(TREND_TOTAL_ID, "STOCK"), TimeGranularity.DAY, label,
            totalLabel = "总净值", customLabel = TREND_CUSTOM_LABEL,
        )
        assertEquals(2, data.series.size)
        assertEquals("总净值", data.series[0].name)
        assertEquals(listOf(400.0, 450.0, 500.0), data.series[1].values)
    }

    @Test
    fun yearly_takesLastOfYear() {
        val data = TrendSeriesBuilder.build(totals, categories, listOf(TREND_TOTAL_ID), TimeGranularity.YEAR, label, totalLabel = "总净值", customLabel = TREND_CUSTOM_LABEL)
        assertEquals(1, data.bucketLabels.size)
        assertEquals(listOf(1200.0), data.series[0].values)
    }

    @Test
    fun empty_returnsEmpty() {
        val data = TrendSeriesBuilder.build(emptyList(), emptyList(), listOf(TREND_TOTAL_ID), TimeGranularity.DAY, label, totalLabel = "总净值", customLabel = TREND_CUSTOM_LABEL)
        assertTrue(data.bucketLabels.isEmpty())
    }

    @Test
    fun customCombination_sumsSelectedCategoriesByDay() {
        val cats = listOf(
            CategoryPoint(d1, "STOCK", 400.0),
            CategoryPoint(d2, "STOCK", 450.0),
            CategoryPoint(d3, "STOCK", 500.0),
            CategoryPoint(d1, "DEPOSIT", 100.0),
            CategoryPoint(d2, "DEPOSIT", 150.0),
            CategoryPoint(d3, "DEPOSIT", 200.0),
        )
        val data = TrendSeriesBuilder.build(
            totals, cats, listOf(TREND_CUSTOM_ID), TimeGranularity.DAY, label,
            totalLabel = "总净值", customLabel = TREND_CUSTOM_LABEL,
            customCategories = setOf("STOCK", "DEPOSIT"),
        )
        assertEquals(1, data.series.size)
        assertEquals(TREND_CUSTOM_LABEL, data.series[0].name)
        assertEquals(listOf(500.0, 600.0, 700.0), data.series[0].values)
    }

    @Test
    fun customCombination_treatsMissingCategoryDayAsZero() {
        // STOCK 有 d1/d2/d3；BOND 仅 d1/d3（d2 缺失→按 0 求和）
        val cats = listOf(
            CategoryPoint(d1, "STOCK", 400.0),
            CategoryPoint(d2, "STOCK", 450.0),
            CategoryPoint(d3, "STOCK", 500.0),
            CategoryPoint(d1, "BOND", 50.0),
            CategoryPoint(d3, "BOND", 80.0),
        )
        val data = TrendSeriesBuilder.build(
            emptyList(), cats, listOf(TREND_CUSTOM_ID), TimeGranularity.DAY, label,
            totalLabel = "总净值", customLabel = TREND_CUSTOM_LABEL,
            customCategories = setOf("STOCK", "BOND"),
        )
        // d1=450, d2=450(仅 STOCK), d3=580
        assertEquals(listOf(450.0, 450.0, 580.0), data.series[0].values)
    }

    @Test
    fun customCombination_alongsideTotalAndMonthlyResampling() {
        val cats = listOf(
            CategoryPoint(d1, "STOCK", 400.0),
            CategoryPoint(d2, "STOCK", 450.0), // 10 月最后一天
            CategoryPoint(d3, "STOCK", 500.0),
            CategoryPoint(d1, "DEPOSIT", 100.0),
            CategoryPoint(d2, "DEPOSIT", 150.0),
            CategoryPoint(d3, "DEPOSIT", 200.0),
        )
        val data = TrendSeriesBuilder.build(
            totals, cats, listOf(TREND_TOTAL_ID, TREND_CUSTOM_ID), TimeGranularity.MONTH, label,
            totalLabel = "总净值", customLabel = TREND_CUSTOM_LABEL,
            customCategories = setOf("STOCK", "DEPOSIT"),
        )
        assertEquals(2, data.series.size)
        // 10 月取最后一天(d2)：总净值 1100；自定义 = 450+150=600
        assertEquals(listOf(1100.0, 1200.0), data.series[0].values)
        assertEquals(listOf(600.0, 700.0), data.series[1].values)
    }

    @Test
    fun customCombination_emptyCategoriesYieldsNullSeries() {
        // 选中 CUSTOM 但未选任何类别：靠 totals 提供时间轴，CUSTOM 序列全 null（无类别可加）
        val data = TrendSeriesBuilder.build(
            totals, categories, listOf(TREND_TOTAL_ID, TREND_CUSTOM_ID), TimeGranularity.DAY, label,
            totalLabel = "总净值", customLabel = TREND_CUSTOM_LABEL,
            customCategories = emptySet(),
        )
        assertEquals(2, data.series.size)
        assertEquals(TREND_CUSTOM_LABEL, data.series[1].name)
        assertTrue(data.series[1].values.all { it == null })
    }
}
