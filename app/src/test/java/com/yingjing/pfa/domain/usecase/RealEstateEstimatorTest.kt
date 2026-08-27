package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.data.remote.HousePricePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RealEstateEstimatorTest {

    // 2026-03 月初（基准月）
    private val baseMs = msOf(2026, 3)
    private val nowMs = msOf(2026, 7)

    private fun pt(month: String, second: Double?) =
        HousePricePoint(city = "北京", month = month, newSequential = null, newSame = null, secondSequential = second, secondSame = null)

    private fun msOf(year: Int, month: Int): Long {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        cal.clear()
        cal.set(year, month - 1, 1, 0, 0, 0)
        return cal.timeInMillis
    }

    @Test
    fun estimate_multipliesSequentialAfterBaseMonth() {
        // 基准月 2026-03，现值 = 100000 × (99.9/100) × (100.0/100) = 99900
        val history = listOf(
            pt("2026-03", 100.5), // 基准月本身不计入
            pt("2026-04", 99.9),
            pt("2026-05", 100.0),
        )
        val result = RealEstateEstimator.estimate(100_000.0, baseMs, history, msOf(2026, 5))
        assertEquals(99_900.0, result!!, 0.01)
    }

    @Test
    fun estimate_skipsMissingMonth_treatedAsFlat() {
        // 缺月跳过（= 持平），仅累乘可用月份
        val history = listOf(
            pt("2026-04", 101.0),
            // 2026-05 缺失
            pt("2026-06", 100.0),
        )
        val result = RealEstateEstimator.estimate(100_000.0, baseMs, history, nowMs)
        // 100000 × 1.01 × 1.00 = 101000
        assertEquals(101_000.0, result!!, 0.01)
    }

    @Test
    fun estimate_nullSequential_treatedAs100() {
        val history = listOf(
            pt("2026-04", null), // 视为 100 持平
            pt("2026-05", 100.5),
        )
        val result = RealEstateEstimator.estimate(100_000.0, baseMs, history, nowMs)
        assertEquals(100_500.0, result!!, 0.01)
    }

    @Test
    fun estimate_emptyHistory_returnsNull() {
        assertNull(RealEstateEstimator.estimate(100_000.0, baseMs, emptyList(), nowMs))
    }

    @Test
    fun estimate_nonPositiveManualValue_returnsNull() {
        val history = listOf(pt("2026-04", 100.5))
        assertNull(RealEstateEstimator.estimate(0.0, baseMs, history, nowMs))
        assertNull(RealEstateEstimator.estimate(-1.0, baseMs, history, nowMs))
    }

    @Test
    fun estimate_baseMonthInFuture_returnsNull() {
        // 基准月在 2026-09，但 now=2026-07 → 区间内无月份
        val history = listOf(pt("2026-04", 100.5), pt("2026-05", 100.0))
        assertNull(RealEstateEstimator.estimate(100_000.0, msOf(2026, 9), history, nowMs))
    }

    @Test
    fun estimate_ignoresFutureMonthsAfterNow() {
        // now=2026-05，未来月份 2026-06 不应计入
        val history = listOf(
            pt("2026-04", 100.5),
            pt("2026-06", 200.0), // 未来，忽略
        )
        val result = RealEstateEstimator.estimate(100_000.0, baseMs, history, msOf(2026, 5))
        assertEquals(100_500.0, result!!, 0.01)
    }

    @Test
    fun cumulativeAdjustPercent_computesCorrectly() {
        assertEquals(5.0, RealEstateEstimator.cumulativeAdjustPercent(100_000.0, 105_000.0)!!, 0.001)
        assertEquals(-2.5, RealEstateEstimator.cumulativeAdjustPercent(100_000.0, 97_500.0)!!, 0.001)
    }

    @Test
    fun cumulativeAdjustPercent_nullEstimated_returnsNull() {
        assertNull(RealEstateEstimator.cumulativeAdjustPercent(100_000.0, null))
    }

    @Test
    fun monthKey_formatsYearMonth() {
        assertEquals("2026-07", RealEstateEstimator.monthKey(msOf(2026, 7)))
        assertEquals("2026-01", RealEstateEstimator.monthKey(msOf(2026, 1)))
    }
}
