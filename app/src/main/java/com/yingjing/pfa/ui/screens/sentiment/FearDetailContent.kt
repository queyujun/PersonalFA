package com.yingjing.pfa.ui.screens.sentiment

import com.yingjing.pfa.domain.model.FearIndexId
import com.yingjing.pfa.domain.model.FearScale
import com.yingjing.pfa.domain.model.SeriesPoint
import com.yingjing.pfa.domain.usecase.FearIndexStats
import com.yingjing.pfa.domain.usecase.FearRange
import com.yingjing.pfa.domain.usecase.FearSummary
import com.yingjing.pfa.domain.usecase.WindowStats

/** 历史分位三档（近 1 年 / 近 5 年 / 全历史）。 */
data class PositionRow(
    val window: PositionWindow,
    val stats: WindowStats,
)

enum class PositionWindow(val days: Long?) {
    Y1(FearIndexStats.ONE_YEAR_DAYS),
    Y5(1_827),
    ALL(null),
}

/**
 * 双轴走势图数据：左轴为恐慌指数，右轴为叠加序列（价格或 VIX）。
 * 两条线共用同一时间轴（按 epochDay 线性映射），叠加序列的缺口（节假日不同步）自然断开。
 */
data class FearChartData(
    val primary: List<SeriesPoint>,
    val overlay: List<SeriesPoint>,
    val startDay: Long,
    val endDay: Long,
    /** 波动率：区间 25/75 分位带 + 中位数；恐惧贪婪：null（画固定五档背景）。 */
    val bandLow: Double?,
    val bandHigh: Double?,
    val median: Double?,
)

/** 详情页内容（[range] 变化时重算）。 */
data class FearDetailContent(
    val id: FearIndexId,
    val summary: FearSummary,
    val positions: List<PositionRow>,
    val range: FearRange,
    val rangeStats: WindowStats?,
    val chart: FearChartData?,
    /** 区间内指数与叠加序列的日对数变动相关系数；样本不足为 null。 */
    val correlation: Double?,
    val sinceYear: Int,
)

object FearDetailBuilder {

    /** 绘图上限点数：3 年约 750 交易日，全历史 VIX 9000+ → 抽样，保证滑动流畅。 */
    const val MAX_CHART_POINTS = 800

    fun build(
        id: FearIndexId,
        primary: List<SeriesPoint>,
        overlay: List<SeriesPoint>,
        range: FearRange,
    ): FearDetailContent? {
        val summary = FearIndexStats.summarize(id, primary) ?: return null
        val anchor = summary.latest.epochDay
        val positions = PositionWindow.entries.mapNotNull { w ->
            FearIndexStats.stats(FearIndexStats.window(primary, anchor, w.days))?.let { PositionRow(w, it) }
        }
        val rangePoints = FearIndexStats.window(primary, anchor, range.days)
        val rangeStats = FearIndexStats.stats(rangePoints)
        val startDay = rangePoints.firstOrNull()?.epochDay ?: anchor
        val overlayInRange = overlay.filter { it.epochDay in startDay..anchor }
        val chart = if (rangePoints.size >= 2 && rangeStats != null) {
            val isVol = id.scale == FearScale.VOLATILITY
            FearChartData(
                primary = FearIndexStats.downsample(rangePoints, MAX_CHART_POINTS),
                overlay = FearIndexStats.downsample(overlayInRange, MAX_CHART_POINTS),
                startDay = startDay,
                endDay = anchor,
                bandLow = if (isVol) rangeStats.p25 else null,
                bandHigh = if (isVol) rangeStats.p75 else null,
                median = if (isVol) rangeStats.median else null,
            )
        } else {
            null
        }
        return FearDetailContent(
            id = id,
            summary = summary,
            positions = positions,
            range = range,
            rangeStats = rangeStats,
            chart = chart,
            correlation = FearIndexStats.changeCorrelation(rangePoints, overlayInRange),
            sinceYear = java.time.LocalDate.ofEpochDay(primary.first().epochDay).year,
        )
    }
}
