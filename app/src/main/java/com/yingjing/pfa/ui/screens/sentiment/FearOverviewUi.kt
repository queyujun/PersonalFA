package com.yingjing.pfa.ui.screens.sentiment

import com.yingjing.pfa.domain.model.FearIndexId
import com.yingjing.pfa.domain.model.SeriesPoint
import com.yingjing.pfa.domain.usecase.FearComposite
import com.yingjing.pfa.domain.usecase.FearIndexStats
import com.yingjing.pfa.domain.usecase.FearSummary

/** 一个指数卡片：摘要 + 近 1 年迷你走势 + 是否本次刷新失败（显示旧缓存）。 */
data class FearCardUi(
    val summary: FearSummary,
    val spark: List<Double>,
    val stale: Boolean,
)

/** 恐慌指数总览 / 总览入口卡共用的状态。 */
data class FearOverviewUi(
    val cards: List<FearCardUi> = emptyList(),
    val composite: FearComposite? = null,
    /** 所有卡片中最新的数据日（「数据截至」）。 */
    val asOfDay: Long? = null,
    val refreshing: Boolean = false,
    /** 完成过至少一次读取（区分「加载中」与「确实无数据」）。 */
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = cards.isEmpty()
}

/** 把缓存序列组装为总览状态（纯函数，可单测）。 */
object FearOverviewBuilder {

    /** 迷你走势最多点数（近 1 年约 250 个交易日 → 抽样到 60 点足够平滑）。 */
    const val SPARK_POINTS = 60

    fun build(
        series: Map<String, List<SeriesPoint>>,
        failedCodes: Set<String>,
        refreshing: Boolean,
    ): FearOverviewUi {
        val cards = FearIndexId.entries.mapNotNull { id ->
            val points = series[id.code].orEmpty()
            val summary = FearIndexStats.summarize(id, points) ?: return@mapNotNull null
            val window = FearIndexStats.window(points, summary.latest.epochDay, FearIndexStats.ONE_YEAR_DAYS)
            FearCardUi(
                summary = summary,
                spark = FearIndexStats.downsample(window, SPARK_POINTS).map { it.value },
                stale = id.code in failedCodes,
            )
        }
        return FearOverviewUi(
            cards = cards,
            composite = FearIndexStats.composite(cards.map { it.summary }),
            asOfDay = cards.maxOfOrNull { it.summary.latest.epochDay },
            refreshing = refreshing,
            loaded = true,
        )
    }
}
