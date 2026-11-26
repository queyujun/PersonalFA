package com.yingjing.pfa.domain.usecase

import androidx.annotation.StringRes
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.FearIndexId
import com.yingjing.pfa.domain.model.FearScale
import com.yingjing.pfa.domain.model.SeriesPoint
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 详情页时间区间（默认 1 年；按自然日回看，锚点为序列最新数据日）。 */
enum class FearRange(@StringRes val labelRes: Int, val days: Long?) {
    M1(R.string.fear_range_1m, 31),
    M3(R.string.fear_range_3m, 92),
    M6(R.string.fear_range_6m, 183),
    Y1(R.string.fear_range_1y, 366),
    Y2(R.string.fear_range_2y, 731),
    Y3(R.string.fear_range_3y, 1_096),
    ALL(R.string.fear_range_all, null),
    ;

    companion object {
        val DEFAULT = Y1
    }
}

/**
 * 「当前处于历史什么位置」评级，与具体指数方向无关：一律按**恐慌程度**分档，
 * UI 统一配色（高恐慌=红、平静=绿）。
 */
enum class FearLevel(@StringRes val labelRes: Int) {
    VERY_LOW(R.string.fear_level_very_low),
    LOW(R.string.fear_level_low),
    NEUTRAL(R.string.fear_level_neutral),
    HIGH(R.string.fear_level_high),
    VERY_HIGH(R.string.fear_level_very_high),
}

/** 加密恐惧贪婪固定五档（alternative.me 官方口径：0–25 / 26–46 / 47–54 / 55–75 / 76–100）。 */
enum class FearGreedZone(@StringRes val labelRes: Int, val lower: Int, val upper: Int) {
    EXTREME_FEAR(R.string.fng_zone_extreme_fear, 0, 25),
    FEAR(R.string.fng_zone_fear, 26, 46),
    NEUTRAL(R.string.fng_zone_neutral, 47, 54),
    GREED(R.string.fng_zone_greed, 55, 75),
    EXTREME_GREED(R.string.fng_zone_extreme_greed, 76, 100),
    ;

    /** 映射为恐慌程度（极度恐慌 = 恐慌最高）。 */
    val fearLevel: FearLevel
        get() = when (this) {
            EXTREME_FEAR -> FearLevel.VERY_HIGH
            FEAR -> FearLevel.HIGH
            NEUTRAL -> FearLevel.NEUTRAL
            GREED -> FearLevel.LOW
            EXTREME_GREED -> FearLevel.VERY_LOW
        }

    companion object {
        /** 按四舍五入后的整数值归档（数据源本身为整数）。 */
        fun of(value: Double): FearGreedZone {
            val v = value.roundToInt().coerceIn(0, 100)
            return entries.first { v <= it.upper }
        }
    }
}

/** 区间统计。[percentile] 为当前值在窗口内的分位（0–100，midrank 口径）。 */
data class WindowStats(
    val count: Int,
    val min: Double,
    val max: Double,
    val mean: Double,
    val median: Double,
    val p25: Double,
    val p75: Double,
    val percentile: Double,
)

/** 单个恐慌指数的摘要（总览卡 / 详情头部）。 */
data class FearSummary(
    val id: FearIndexId,
    val latest: SeriesPoint,
    val previous: SeriesPoint?,
    /** 近 1 年窗口统计（总览卡、评级、综合温度均基于此）。 */
    val oneYear: WindowStats,
    /** 恐慌程度 0–100（越高越恐慌）：波动率取近 1 年分位；恐惧贪婪取 100 − 读数。 */
    val fearScore: Double,
    val level: FearLevel,
    val zone: FearGreedZone?,
) {
    val change: Double? get() = previous?.let { latest.value - it.value }
    val changePct: Double? get() = previous?.takeIf { it.value != 0.0 }?.let { (latest.value - it.value) / it.value * 100.0 }
}

/** 综合恐慌温度：可用指数 [fearScore] 的等权平均；[used] / [total] 为样本数。 */
data class FearComposite(
    val score: Double,
    val level: FearLevel,
    val used: Int,
    val total: Int,
)

/**
 * 恐慌指数统计纯函数（无 Android 依赖，可单测）。
 *
 * 分位采用 **midrank**：`(低于当前值的个数 + 0.5 × 等于当前值的个数) / 样本数 × 100`，
 * 对恐惧贪婪这类整数序列（大量并列值）不会系统性偏高。
 */
object FearIndexStats {

    /** 近 1 年窗口（366 天，含闰年与锚点当日）。 */
    const val ONE_YEAR_DAYS: Long = 366

    /** 序列最新日超过该天数视为「数据可能过期」（周末 + 美国长假 + 源端延迟）。 */
    const val STALE_DAYS: Long = 5

    fun percentileOf(values: List<Double>, current: Double): Double {
        if (values.isEmpty()) return 50.0
        var below = 0
        var equal = 0
        values.forEach {
            when {
                it < current -> below++
                it == current -> equal++
            }
        }
        return (below + 0.5 * equal) / values.size * 100.0
    }

    /** 线性插值分位数（q ∈ [0,1]），[sorted] 必须升序且非空。 */
    fun quantile(sorted: List<Double>, q: Double): Double {
        require(sorted.isNotEmpty()) { "quantile of empty list" }
        if (sorted.size == 1) return sorted[0]
        val pos = (sorted.size - 1) * q.coerceIn(0.0, 1.0)
        val lo = pos.toInt()
        val hi = minOf(lo + 1, sorted.lastIndex)
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
    }

    /** 以 [anchorDay] 为终点回看 [days] 天（含两端）的窗口；[days] 为 null 时返回全部。 */
    fun window(points: List<SeriesPoint>, anchorDay: Long, days: Long?): List<SeriesPoint> {
        if (days == null) return points.filter { it.epochDay <= anchorDay }
        val from = anchorDay - days + 1
        return points.filter { it.epochDay in from..anchorDay }
    }

    /** 窗口统计；空窗口返回 null。当前值取窗口内最后一个点。 */
    fun stats(window: List<SeriesPoint>): WindowStats? {
        if (window.isEmpty()) return null
        val values = window.map { it.value }
        val sorted = values.sorted()
        return WindowStats(
            count = values.size,
            min = sorted.first(),
            max = sorted.last(),
            mean = values.average(),
            median = quantile(sorted, 0.5),
            p25 = quantile(sorted, 0.25),
            p75 = quantile(sorted, 0.75),
            percentile = percentileOf(values, window.last().value),
        )
    }

    /** 波动率指数按分位评级：≤10 极低、≤30 偏低、<70 中性、<90 偏高、≥90 极高。 */
    fun levelForVolatility(percentile: Double): FearLevel = when {
        percentile <= 10.0 -> FearLevel.VERY_LOW
        percentile <= 30.0 -> FearLevel.LOW
        percentile < 70.0 -> FearLevel.NEUTRAL
        percentile < 90.0 -> FearLevel.HIGH
        else -> FearLevel.VERY_HIGH
    }

    /** 综合温度评级：≤20 平静、≤40 偏平静、<60 中性、<80 偏恐慌、≥80 极度恐慌。 */
    fun levelForComposite(score: Double): FearLevel = when {
        score <= 20.0 -> FearLevel.VERY_LOW
        score <= 40.0 -> FearLevel.LOW
        score < 60.0 -> FearLevel.NEUTRAL
        score < 80.0 -> FearLevel.HIGH
        else -> FearLevel.VERY_HIGH
    }

    /** 构建指数摘要；序列为空返回 null。[points] 须升序。 */
    fun summarize(id: FearIndexId, points: List<SeriesPoint>): FearSummary? {
        val latest = points.lastOrNull() ?: return null
        val oneYear = stats(window(points, latest.epochDay, ONE_YEAR_DAYS)) ?: return null
        val zone = if (id.scale == FearScale.FEAR_GREED) FearGreedZone.of(latest.value) else null
        val score = when (id.scale) {
            FearScale.VOLATILITY -> oneYear.percentile
            FearScale.FEAR_GREED -> (100.0 - latest.value).coerceIn(0.0, 100.0)
        }
        val level = zone?.fearLevel ?: levelForVolatility(oneYear.percentile)
        return FearSummary(
            id = id,
            latest = latest,
            previous = points.getOrNull(points.lastIndex - 1),
            oneYear = oneYear,
            fearScore = score,
            level = level,
            zone = zone,
        )
    }

    /** 综合恐慌温度；无任何可用指数返回 null。 */
    fun composite(summaries: List<FearSummary>, total: Int = FearIndexId.entries.size): FearComposite? {
        if (summaries.isEmpty()) return null
        val score = summaries.map { it.fearScore }.average()
        return FearComposite(score, levelForComposite(score), summaries.size, total)
    }

    /**
     * 叠加序列与指数的**日对数收益相关系数**（Pearson，按日期内连接后计算相邻交易日变动）。
     *
     * 用日变动而非原始水平，避免两条趋势序列的「伪相关」。样本不足 [minSamples] 返回 null。
     */
    fun changeCorrelation(a: List<SeriesPoint>, b: List<SeriesPoint>, minSamples: Int = 20): Double? {
        val bMap = b.associate { it.epochDay to it.value }
        val joined = a.mapNotNull { p -> bMap[p.epochDay]?.let { p.value to it } }
        if (joined.size < minSamples + 1) return null
        val dx = ArrayList<Double>(joined.size - 1)
        val dy = ArrayList<Double>(joined.size - 1)
        for (i in 1 until joined.size) {
            val (x0, y0) = joined[i - 1]
            val (x1, y1) = joined[i]
            if (x0 <= 0 || x1 <= 0 || y0 <= 0 || y1 <= 0) continue
            dx += ln(x1 / x0)
            dy += ln(y1 / y0)
        }
        if (dx.size < minSamples) return null
        return pearson(dx, dy)
    }

    fun pearson(x: List<Double>, y: List<Double>): Double? {
        if (x.size != y.size || x.size < 2) return null
        val mx = x.average()
        val my = y.average()
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in x.indices) {
            val ex = x[i] - mx
            val ey = y[i] - my
            sxy += ex * ey
            sxx += ex * ex
            syy += ey * ey
        }
        if (sxx <= 0.0 || syy <= 0.0) return null
        return (sxy / sqrt(sxx * syy)).coerceIn(-1.0, 1.0)
    }

    /** 相关强度描述档：|r| < 0.2 弱、< 0.5 中等、否则强。 */
    fun correlationStrength(r: Double): CorrelationStrength = when {
        abs(r) < 0.2 -> CorrelationStrength.WEAK
        abs(r) < 0.5 -> CorrelationStrength.MODERATE
        else -> CorrelationStrength.STRONG
    }

    /**
     * 等距抽样到最多 [maxPoints] 个点（保留首尾），用于长区间绘图降负载。
     * 不改变数值，只减少点数；[maxPoints] < 2 时按 2 处理。
     */
    fun downsample(points: List<SeriesPoint>, maxPoints: Int): List<SeriesPoint> {
        val limit = maxOf(2, maxPoints)
        if (points.size <= limit) return points
        val step = (points.size - 1).toDouble() / (limit - 1)
        return (0 until limit).map { i -> points[(i * step).roundToInt().coerceAtMost(points.lastIndex)] }
            .distinctBy { it.epochDay }
    }
}

enum class CorrelationStrength(@StringRes val labelRes: Int) {
    WEAK(R.string.corr_weak),
    MODERATE(R.string.corr_moderate),
    STRONG(R.string.corr_strong),
}
