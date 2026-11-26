package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.model.FearIndexId
import com.yingjing.pfa.domain.model.SeriesPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

class FearIndexStatsTest {

    private fun series(vararg values: Double, startDay: Long = 1_000) =
        values.mapIndexed { i, v -> SeriesPoint(startDay + i, v) }

    @Test
    fun `percentile uses midrank so ties do not bias upward`() {
        assertEquals(50.0, FearIndexStats.percentileOf(listOf(5.0, 5.0, 5.0, 5.0), 5.0), 1e-9)
        // 3 个低于 + 0.5×1 个等于 → 3.5/4
        assertEquals(87.5, FearIndexStats.percentileOf(listOf(1.0, 2.0, 3.0, 4.0), 4.0), 1e-9)
        assertEquals(12.5, FearIndexStats.percentileOf(listOf(1.0, 2.0, 3.0, 4.0), 1.0), 1e-9)
        assertEquals(100.0, FearIndexStats.percentileOf(listOf(1.0, 2.0), 9.0), 1e-9)
        assertEquals(0.0, FearIndexStats.percentileOf(listOf(1.0, 2.0), 0.5), 1e-9)
        assertEquals(50.0, FearIndexStats.percentileOf(emptyList(), 3.0), 1e-9)
    }

    @Test
    fun `quantile interpolates linearly`() {
        val sorted = listOf(10.0, 20.0, 30.0, 40.0)
        assertEquals(25.0, FearIndexStats.quantile(sorted, 0.5), 1e-9)
        assertEquals(17.5, FearIndexStats.quantile(sorted, 0.25), 1e-9)
        assertEquals(10.0, FearIndexStats.quantile(sorted, 0.0), 1e-9)
        assertEquals(40.0, FearIndexStats.quantile(sorted, 1.0), 1e-9)
        assertEquals(7.0, FearIndexStats.quantile(listOf(7.0), 0.3), 1e-9)
    }

    @Test
    fun `window is inclusive of both ends and null means all`() {
        val pts = series(1.0, 2.0, 3.0, 4.0, 5.0) // days 1000..1004
        assertEquals(listOf(3.0, 4.0, 5.0), FearIndexStats.window(pts, 1004, 3).map { it.value })
        assertEquals(5, FearIndexStats.window(pts, 1004, null).size)
        assertEquals(listOf(1.0, 2.0), FearIndexStats.window(pts, 1001, null).map { it.value })
    }

    @Test
    fun `stats computes min max mean median and current percentile`() {
        val s = FearIndexStats.stats(series(10.0, 30.0, 20.0, 40.0))!!
        assertEquals(4, s.count)
        assertEquals(10.0, s.min, 1e-9)
        assertEquals(40.0, s.max, 1e-9)
        assertEquals(25.0, s.mean, 1e-9)
        assertEquals(25.0, s.median, 1e-9)
        assertEquals(87.5, s.percentile, 1e-9) // 当前值 = 最后一点 40
        assertNull(FearIndexStats.stats(emptyList()))
    }

    @Test
    fun `volatility level thresholds`() {
        assertEquals(FearLevel.VERY_LOW, FearIndexStats.levelForVolatility(10.0))
        assertEquals(FearLevel.LOW, FearIndexStats.levelForVolatility(10.1))
        assertEquals(FearLevel.LOW, FearIndexStats.levelForVolatility(30.0))
        assertEquals(FearLevel.NEUTRAL, FearIndexStats.levelForVolatility(69.9))
        assertEquals(FearLevel.HIGH, FearIndexStats.levelForVolatility(70.0))
        assertEquals(FearLevel.HIGH, FearIndexStats.levelForVolatility(89.9))
        assertEquals(FearLevel.VERY_HIGH, FearIndexStats.levelForVolatility(90.0))
    }

    @Test
    fun `fear greed zones follow alternative me boundaries`() {
        assertEquals(FearGreedZone.EXTREME_FEAR, FearGreedZone.of(0.0))
        assertEquals(FearGreedZone.EXTREME_FEAR, FearGreedZone.of(25.0))
        assertEquals(FearGreedZone.FEAR, FearGreedZone.of(26.0))
        assertEquals(FearGreedZone.FEAR, FearGreedZone.of(46.0))
        assertEquals(FearGreedZone.NEUTRAL, FearGreedZone.of(47.0))
        assertEquals(FearGreedZone.NEUTRAL, FearGreedZone.of(54.0))
        assertEquals(FearGreedZone.GREED, FearGreedZone.of(55.0))
        assertEquals(FearGreedZone.GREED, FearGreedZone.of(75.0))
        assertEquals(FearGreedZone.EXTREME_GREED, FearGreedZone.of(76.0))
        assertEquals(FearGreedZone.EXTREME_GREED, FearGreedZone.of(100.0))
        assertEquals(FearGreedZone.EXTREME_GREED, FearGreedZone.of(150.0))
        // 五档首尾相接、覆盖 0..100
        val zones = FearGreedZone.entries
        assertEquals(0, zones.first().lower)
        assertEquals(100, zones.last().upper)
        zones.zipWithNext().forEach { (a, b) -> assertEquals(a.upper + 1, b.lower) }
    }

    @Test
    fun `summarize volatility uses one year percentile as fear score`() {
        val pts = series(10.0, 12.0, 14.0, 16.0, 30.0)
        val s = FearIndexStats.summarize(FearIndexId.VIX, pts)!!
        assertEquals(30.0, s.latest.value, 1e-9)
        assertEquals(16.0, s.previous!!.value, 1e-9)
        assertEquals(90.0, s.fearScore, 1e-9) // (4 + 0.5)/5
        assertEquals(FearLevel.VERY_HIGH, s.level)
        assertNull(s.zone)
        assertEquals(14.0, s.change!!, 1e-9)
        assertEquals(87.5, s.changePct!!, 1e-9)
    }

    @Test
    fun `summarize fear greed inverts reading and maps zone to fear level`() {
        val s = FearIndexStats.summarize(FearIndexId.CRYPTO_FNG, series(50.0, 40.0, 13.0))!!
        assertEquals(87.0, s.fearScore, 1e-9)
        assertEquals(FearGreedZone.EXTREME_FEAR, s.zone)
        assertEquals(FearLevel.VERY_HIGH, s.level)
    }

    @Test
    fun `summarize single point has no previous and empty yields null`() {
        val s = FearIndexStats.summarize(FearIndexId.VIX, series(20.0))!!
        assertNull(s.previous)
        assertNull(s.change)
        assertNull(FearIndexStats.summarize(FearIndexId.VIX, emptyList()))
    }

    @Test
    fun `one year window excludes older points`() {
        val old = SeriesPoint(0, 100.0)
        val recent = series(10.0, 11.0, startDay = 1_000)
        val s = FearIndexStats.summarize(FearIndexId.VIX, listOf(old) + recent)!!
        assertEquals(2, s.oneYear.count)
        assertEquals(11.0, s.oneYear.max, 1e-9)
    }

    @Test
    fun `composite averages fear scores and reports sample size`() {
        val a = FearIndexStats.summarize(FearIndexId.VIX, series(1.0, 2.0, 3.0, 4.0))!! // 87.5
        val b = FearIndexStats.summarize(FearIndexId.CRYPTO_FNG, series(60.0, 40.0))!! // 60
        val c = FearIndexStats.composite(listOf(a, b))!!
        assertEquals(73.75, c.score, 1e-9)
        assertEquals(FearLevel.HIGH, c.level)
        assertEquals(2, c.used)
        assertEquals(FearIndexId.entries.size, c.total)
        assertNull(FearIndexStats.composite(emptyList()))
    }

    @Test
    fun `composite level thresholds`() {
        assertEquals(FearLevel.VERY_LOW, FearIndexStats.levelForComposite(20.0))
        assertEquals(FearLevel.LOW, FearIndexStats.levelForComposite(40.0))
        assertEquals(FearLevel.NEUTRAL, FearIndexStats.levelForComposite(59.9))
        assertEquals(FearLevel.HIGH, FearIndexStats.levelForComposite(60.0))
        assertEquals(FearLevel.VERY_HIGH, FearIndexStats.levelForComposite(80.0))
    }

    @Test
    fun `change correlation detects inverse moves on matching days`() {
        val n = 40
        val returns = (0 until n).map { if (it % 2 == 0) 0.02 else -0.015 }
        var x = 100.0
        var y = 100.0
        val a = mutableListOf(SeriesPoint(0, x))
        val b = mutableListOf(SeriesPoint(0, y))
        returns.forEachIndexed { i, r ->
            x *= exp(r)
            y *= exp(-r)
            a += SeriesPoint(i + 1L, x)
            b += SeriesPoint(i + 1L, y)
        }
        assertEquals(-1.0, FearIndexStats.changeCorrelation(a, b)!!, 1e-6)
        assertEquals(1.0, FearIndexStats.changeCorrelation(a, a)!!, 1e-6)
    }

    @Test
    fun `change correlation only uses overlapping days and needs enough samples`() {
        val a = series(*DoubleArray(10) { 10.0 + it })
        val b = series(*DoubleArray(10) { 20.0 - it }, startDay = 5_000) // 无重叠
        assertNull(FearIndexStats.changeCorrelation(a, b))
        assertNull(FearIndexStats.changeCorrelation(a, a)) // 样本不足 20
        val flat = series(*DoubleArray(30) { 5.0 })
        assertNull(FearIndexStats.changeCorrelation(flat, flat)) // 零方差
    }

    @Test
    fun `correlation strength bands`() {
        assertEquals(CorrelationStrength.WEAK, FearIndexStats.correlationStrength(0.19))
        assertEquals(CorrelationStrength.MODERATE, FearIndexStats.correlationStrength(-0.2))
        assertEquals(CorrelationStrength.MODERATE, FearIndexStats.correlationStrength(0.49))
        assertEquals(CorrelationStrength.STRONG, FearIndexStats.correlationStrength(-0.75))
    }

    @Test
    fun `downsample keeps first and last and caps size`() {
        val pts = series(*DoubleArray(1_000) { it.toDouble() })
        val d = FearIndexStats.downsample(pts, 60)
        assertTrue(d.size <= 60)
        assertEquals(pts.first(), d.first())
        assertEquals(pts.last(), d.last())
        assertTrue(d.zipWithNext().all { (p, q) -> p.epochDay < q.epochDay })
        assertEquals(pts.take(5), FearIndexStats.downsample(pts.take(5), 60))
        assertNotNull(FearIndexStats.downsample(pts, 1).takeIf { it.size == 2 })
    }

    @Test
    fun `range enum defaults to one year and includes two and three years`() {
        assertEquals(FearRange.Y1, FearRange.DEFAULT)
        assertEquals(731L, FearRange.Y2.days)
        assertEquals(1_096L, FearRange.Y3.days)
        assertNull(FearRange.ALL.days)
    }
}
