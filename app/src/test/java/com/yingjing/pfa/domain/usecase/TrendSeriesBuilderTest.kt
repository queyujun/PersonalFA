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
        val data = TrendSeriesBuilder.build(totals, categories, listOf(TREND_TOTAL_ID), TimeGranularity.DAY, label)
        assertEquals(3, data.bucketLabels.size)
        assertEquals(listOf(1000.0, 1100.0, 1200.0), data.series[0].values)
    }

    @Test
    fun monthly_takesLastValuePerMonth() {
        val data = TrendSeriesBuilder.build(totals, categories, listOf(TREND_TOTAL_ID), TimeGranularity.MONTH, label)
        // 10 月两天取最后一天(1100)，11 月一天(1200)
        assertEquals(2, data.bucketLabels.size)
        assertEquals(listOf(1100.0, 1200.0), data.series[0].values)
    }

    @Test
    fun multiSeries_totalAndCategory() {
        val data = TrendSeriesBuilder.build(
            totals, categories, listOf(TREND_TOTAL_ID, "STOCK"), TimeGranularity.DAY, label,
        )
        assertEquals(2, data.series.size)
        assertEquals("总净值", data.series[0].name)
        assertEquals(listOf(400.0, 450.0, 500.0), data.series[1].values)
    }

    @Test
    fun yearly_takesLastOfYear() {
        val data = TrendSeriesBuilder.build(totals, categories, listOf(TREND_TOTAL_ID), TimeGranularity.YEAR, label)
        assertEquals(1, data.bucketLabels.size)
        assertEquals(listOf(1200.0), data.series[0].values)
    }

    @Test
    fun empty_returnsEmpty() {
        val data = TrendSeriesBuilder.build(emptyList(), emptyList(), listOf(TREND_TOTAL_ID), TimeGranularity.DAY, label)
        assertTrue(data.bucketLabels.isEmpty())
    }
}
