package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.data.remote.HousePricePoint
import kotlin.math.max

/**
 * 房产指数估算（纯函数，可单元测试）。
 *
 * 以用户录入值 [Holding.manualValue] 为基准，按基准月之后的各月二手住宅环比指数
 * 累乘，得到估算现值。环比指数：100=持平，100.1=环比涨 0.1%。
 *
 * 估算公式：`estimatedValue = manualValue × Π(secondSequential / 100)`，
 * 累乘区间为 (基准月, 最新可用月]，即基准月本身不计入（其值即录入值）。
 */
object RealEstateEstimator {

    /**
     * @param manualValue 录入基准值（>0）
     * @param baseDateMs 录入值对应的基准月时间戳（通常为用户录入该值的时间）
     * @param history 该城市指数历史（任意顺序，内部按月份排序）
     * @param nowMs 当前时间戳，用于截断未来月份
     * @return 估算现值；manualValue 非正或无可用指数时返回 null（调用方回退到 manualValue）
     */
    fun estimate(
        manualValue: Double,
        baseDateMs: Long,
        history: List<HousePricePoint>,
        nowMs: Long,
    ): Double? {
        if (manualValue <= 0.0) return null
        if (history.isEmpty()) return null

        val baseMonth = monthKey(baseDateMs)
        val nowMonth = monthKey(nowMs)
        // 仅取 (baseMonth, nowMonth] 区间、二手环比有效的月份，按月份升序
        val factors = history
            .filter { it.month > baseMonth && it.month <= nowMonth }
            .sortedBy { it.month }
            .map { it.secondSequential ?: 100.0 } // 缺月/缺字段视为持平(100)

        if (factors.isEmpty()) return null

        val product = factors.fold(1.0) { acc, seq -> acc * (seq / 100.0) }
        return manualValue * product
    }

    /**
     * 累计调整百分比（估算值相对录入值的涨跌%）。无估算时返回 null。
     * 例：估算值=105000、录入值=100000 → 5.0（涨 5%）。
     */
    fun cumulativeAdjustPercent(
        manualValue: Double,
        estimatedValue: Double?,
    ): Double? {
        if (estimatedValue == null || manualValue <= 0.0) return null
        return (estimatedValue - manualValue) / manualValue * 100.0
    }

    /** 时间戳 → "yyyy-MM" 月份键。 */
    fun monthKey(epochMs: Long): String {
        val cal = java.util.Calendar.getInstance().apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
            timeInMillis = epochMs
        }
        val y = cal.get(java.util.Calendar.YEAR)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        return "%04d-%02d".format(y, m)
    }

    /** 两个月份键之间的月数差（b - a，按自然月计，含部分月）。用于详情页展示跨度。 */
    fun monthsBetween(a: String, b: String): Int {
        val (ya, ma) = parseMonth(a) ?: return 0
        val (yb, mb) = parseMonth(b) ?: return 0
        return max(0, (yb - ya) * 12 + (mb - ma))
    }

    private fun parseMonth(key: String): Pair<Int, Int>? {
        val parts = key.split("-")
        if (parts.size != 2) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return y to m
    }
}
