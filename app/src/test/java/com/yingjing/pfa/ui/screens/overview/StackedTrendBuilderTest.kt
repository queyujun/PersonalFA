package com.yingjing.pfa.ui.screens.overview

import com.yingjing.pfa.domain.model.AssetCategory
import com.yingjing.pfa.domain.model.CategoryPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StackedTrendBuilderTest {

    @Test
    fun `fewer than 2 days returns empty series`() {
        val result = buildStackedTrend(
            netWorthDays = listOf(100L),
            categoryPoints = listOf(CategoryPoint(100L, AssetCategory.STOCK.name, 5000.0)),
        )
        assertTrue(result.series.isEmpty())
        assertTrue(result.epochDays.isEmpty())
    }

    @Test
    fun `series aligned to net worth days with missing category filled zero`() {
        // 时间轴 day1/day2/day3；STOCK 只在 day1/day3 有，DEPOSIT 仅 day2 有。
        val days = listOf(10L, 11L, 12L)
        val points = listOf(
            CategoryPoint(10L, AssetCategory.STOCK.name, 100.0),
            CategoryPoint(12L, AssetCategory.STOCK.name, 300.0),
            CategoryPoint(11L, AssetCategory.DEPOSIT.name, 50.0),
        )

        val result = buildStackedTrend(days, points)

        assertEquals(days, result.epochDays)
        // 9 个资产类（排除负债），各序列与时间轴等长 3。
        assertEquals(9, result.series.size)
        result.series.forEach { assertEquals(3, it.values.size) }

        val stock = result.series.first { it.category == AssetCategory.STOCK }
        assertEquals(listOf(100.0, 0.0, 300.0), stock.values)

        val deposit = result.series.first { it.category == AssetCategory.DEPOSIT }
        assertEquals(listOf(0.0, 50.0, 0.0), deposit.values)

        // 负债类不参与堆叠。
        assertTrue(result.series.none { it.category == AssetCategory.LIABILITY })
    }

    @Test
    fun `stacked totals per day equal sum of each series values`() {
        val days = listOf(1L, 2L)
        val points = listOf(
            CategoryPoint(1L, AssetCategory.REAL_ESTATE.name, 1000.0),
            CategoryPoint(2L, AssetCategory.REAL_ESTATE.name, 1200.0),
            CategoryPoint(1L, AssetCategory.STOCK.name, 200.0),
            CategoryPoint(2L, AssetCategory.STOCK.name, 300.0),
        )

        val result = buildStackedTrend(days, points)

        // day1 总和 = 1000 + 200 = 1200；day2 = 1200 + 300 = 1500
        val day1Total = result.series.sumOf { it.values[0] }
        val day2Total = result.series.sumOf { it.values[1] }
        assertEquals(1200.0, day1Total, 0.0001)
        assertEquals(1500.0, day2Total, 0.0001)
    }

    @Test
    fun `series ordered largest to smallest from bottom`() {
        // REAL_ESTATE 末日 1200 > STOCK 末日 300：底部（首项）应是 REAL_ESTATE。
        val days = listOf(1L, 2L)
        val points = listOf(
            CategoryPoint(1L, AssetCategory.STOCK.name, 200.0),
            CategoryPoint(2L, AssetCategory.STOCK.name, 300.0),
            CategoryPoint(1L, AssetCategory.REAL_ESTATE.name, 1000.0),
            CategoryPoint(2L, AssetCategory.REAL_ESTATE.name, 1200.0),
        )

        val result = buildStackedTrend(days, points)

        // 首项（底部色带）= 最近规模最大的类别。
        assertEquals(AssetCategory.REAL_ESTATE, result.series.first().category)
        assertEquals(AssetCategory.STOCK, result.series[1].category)
    }

    @Test
    fun `ranking uses last non-zero to survive trailing zero days`() {
        // STOCK 前高后零（末日 0），REAL_ESTATE 持续：按最近非零值，STOCK 末非零=400 > REAL_ESTATE 末非零=300。
        val days = listOf(1L, 2L, 3L)
        val points = listOf(
            CategoryPoint(1L, AssetCategory.STOCK.name, 400.0),
            CategoryPoint(2L, AssetCategory.STOCK.name, 0.0),
            CategoryPoint(3L, AssetCategory.STOCK.name, 0.0),
            CategoryPoint(1L, AssetCategory.REAL_ESTATE.name, 100.0),
            CategoryPoint(2L, AssetCategory.REAL_ESTATE.name, 200.0),
            CategoryPoint(3L, AssetCategory.REAL_ESTATE.name, 300.0),
        )

        val result = buildStackedTrend(days, points)

        assertEquals(AssetCategory.STOCK, result.series.first().category)
    }
}
