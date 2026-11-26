package com.yingjing.pfa.ui.screens.sentiment

import com.yingjing.pfa.domain.model.FearIndexId
import com.yingjing.pfa.domain.model.SeriesPoint
import com.yingjing.pfa.domain.usecase.FearRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FearBuildersTest {

    private fun daily(count: Int, endDay: Long = 20_000, value: (Int) -> Double): List<SeriesPoint> =
        (0 until count).map { i -> SeriesPoint(endDay - (count - 1 - i), value(i)) }

    @Test
    fun `overview builds cards in enum order, marks stale and computes composite`() {
        val series = mapOf(
            FearIndexId.OVX.code to daily(10) { 40.0 + it },
            FearIndexId.VIX.code to daily(10) { 20.0 + it },
            FearIndexId.CRYPTO_FNG.code to daily(10) { 50.0 - it },
        )
        val ui = FearOverviewBuilder.build(series, failedCodes = setOf(FearIndexId.OVX.code), refreshing = true)
        assertEquals(listOf(FearIndexId.VIX, FearIndexId.CRYPTO_FNG, FearIndexId.OVX), ui.cards.map { it.summary.id })
        assertTrue(ui.cards.single { it.summary.id == FearIndexId.OVX }.stale)
        assertEquals(3, ui.composite!!.used)
        assertEquals(20_000L, ui.asOfDay)
        assertTrue(ui.refreshing)
        assertTrue(ui.loaded)
    }

    @Test
    fun `overview spark is capped and empty input gives empty state`() {
        val ui = FearOverviewBuilder.build(mapOf(FearIndexId.VIX.code to daily(400) { it.toDouble() + 1 }), emptySet(), false)
        assertTrue(ui.cards.single().spark.size <= FearOverviewBuilder.SPARK_POINTS)
        val empty = FearOverviewBuilder.build(emptyMap(), emptySet(), false)
        assertTrue(empty.isEmpty)
        assertNull(empty.composite)
        assertTrue(empty.loaded)
    }

    @Test
    fun `detail default range is one year and overlay is clipped to the range`() {
        val primary = daily(800) { 15.0 + (it % 20) }
        val overlay = daily(800) { 5_000.0 - (it % 20) * 10 }
        val c = FearDetailBuilder.build(FearIndexId.VIX, primary, overlay, FearRange.DEFAULT)!!
        val chart = c.chart!!
        assertEquals(20_000L, chart.endDay)
        assertEquals(20_000L - 365, chart.startDay)
        assertTrue(chart.overlay.all { it.epochDay in chart.startDay..chart.endDay })
        assertNotNull(chart.bandLow)
        assertNotNull(chart.median)
        assertEquals(366, c.rangeStats!!.count)
        // 走势完全反向 → 相关系数为负
        assertTrue(c.correlation!! < -0.9)
    }

    @Test
    fun `detail supports two and three year ranges`() {
        val primary = daily(1_200) { 15.0 + (it % 7) }
        val y2 = FearDetailBuilder.build(FearIndexId.VXN, primary, emptyList(), FearRange.Y2)!!
        val y3 = FearDetailBuilder.build(FearIndexId.VXN, primary, emptyList(), FearRange.Y3)!!
        assertEquals(731, y2.rangeStats!!.count)
        assertEquals(1_096, y3.rangeStats!!.count)
        assertTrue(y3.chart!!.primary.size <= FearDetailBuilder.MAX_CHART_POINTS)
        assertNull(y2.correlation)
    }

    @Test
    fun `detail positions include one five and all windows`() {
        val c = FearDetailBuilder.build(FearIndexId.VIX, daily(2_500) { 10.0 + it % 30 }, emptyList(), FearRange.ALL)!!
        assertEquals(listOf(PositionWindow.Y1, PositionWindow.Y5, PositionWindow.ALL), c.positions.map { it.window })
        assertEquals(2_500, c.positions.last().stats.count)
    }

    @Test
    fun `fear greed detail has no percentile band`() {
        val c = FearDetailBuilder.build(FearIndexId.CRYPTO_FNG, daily(100) { (it % 90).toDouble() + 1 }, emptyList(), FearRange.M3)!!
        assertNull(c.chart!!.bandLow)
        assertNull(c.chart!!.median)
    }

    @Test
    fun `detail with a single point has no chart and empty series returns null`() {
        val one = FearDetailBuilder.build(FearIndexId.GVZ, daily(1) { 30.0 }, emptyList(), FearRange.Y1)!!
        assertNull(one.chart)
        assertNull(FearDetailBuilder.build(FearIndexId.GVZ, emptyList(), emptyList(), FearRange.Y1))
    }

    @Test
    fun `nice scale produces round ticks covering the data`() {
        val s = niceScale(13.47, 35.89)
        assertTrue(s.min <= 13.47 && s.max >= 35.89)
        assertTrue(s.ticks.size in 3..7)
        val step = s.ticks[1] - s.ticks[0]
        assertTrue(listOf(1.0, 2.0, 2.5, 5.0, 10.0).any { kotlin.math.abs(step - it) < 1e-9 })
        val flat = niceScale(20.0, 20.0)
        assertTrue(flat.max > flat.min)
    }

    @Test
    fun `nearest finds closest point and respects max gap`() {
        val pts = listOf(SeriesPoint(10, 1.0), SeriesPoint(20, 2.0), SeriesPoint(30, 3.0))
        assertEquals(20L, nearest(pts, 23)!!.epochDay)
        assertEquals(30L, nearest(pts, 99)!!.epochDay)
        assertEquals(10L, nearest(pts, -5)!!.epochDay)
        assertNull(nearest(pts, 26, maxGap = 3))
        assertNull(nearest(emptyList(), 1))
    }
}
