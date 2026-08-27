package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.model.CategoryPoint
import com.yingjing.pfa.domain.model.NetWorthPoint
import java.time.LocalDate

/** 时间粒度。 */
enum class TimeGranularity(val label: String) {
    DAY("日"),
    MONTH("月"),
    YEAR("年"),
}

/** 一条走势序列，值对齐到 [TrendChartData.bucketLabels]（null 表示该桶无数据）。 */
data class TrendSeries(val id: String, val name: String, val values: List<Double?>)

data class TrendChartData(val bucketLabels: List<String>, val series: List<TrendSeries>)

/** 总净值序列的固定 id。 */
const val TREND_TOTAL_ID = "TOTAL"

/** 自定义组合序列的固定 id（用户多选若干资产类别，按日求和成一条线）。 */
const val TREND_CUSTOM_ID = "CUSTOM"

/** 自定义组合序列的展示名。 */
const val TREND_CUSTOM_LABEL = "自定义组合"

/**
 * 把每日总净值 + 各类别金额，按粒度重采样为多序列走势（纯函数，可测）。
 * 每个时间桶取该桶内「最后一天」的值（净值/金额为时点量）。
 *
 * [customCategories] 仅对 [TREND_CUSTOM_ID] 序列生效：把这些类别同一 epochDay 的金额
 * 求和，作为该序列在该日的值（某类别某日无数据视为 0，即不贡献）。某类别当日缺失不影响
 * 其它在场类别，这与「多类别合计」语义一致。
 */
object TrendSeriesBuilder {

    fun build(
        totals: List<NetWorthPoint>,
        categories: List<CategoryPoint>,
        selectedIds: List<String>,
        granularity: TimeGranularity,
        categoryLabel: (String) -> String,
        customCategories: Set<String> = emptySet(),
    ): TrendChartData {
        // 每个选中序列 → (epochDay → 值)
        val sources: Map<String, Map<Long, Double>> = selectedIds.associateWith { id ->
            when (id) {
                TREND_TOTAL_ID -> totals.associate { it.epochDay to it.netWorth }
                TREND_CUSTOM_ID ->
                    // 多类别按日求和（不可变：groupBy + mapValues，缺失类别天然不贡献=0）
                    categories
                        .filter { it.category in customCategories }
                        .groupBy { it.epochDay }
                        .mapValues { (_, pts) -> pts.sumOf { it.amount } }
                else -> categories.filter { it.category == id }.associate { it.epochDay to it.amount }
            }
        }

        val allDays = sources.values.flatMap { it.keys }.toSortedSet()
        if (allDays.isEmpty()) return TrendChartData(emptyList(), emptyList())

        val dayToBucketKey = allDays.associateWith { bucketKey(it, granularity) }
        // 有序去重的桶（key → 该桶代表标签用最后一天）
        val orderedBucketKeys = allDays.map { dayToBucketKey.getValue(it) }.distinct().sorted()
        val bucketLabel: Map<Long, String> = orderedBucketKeys.associateWith { key ->
            val lastDay = allDays.filter { dayToBucketKey.getValue(it) == key }.max()
            label(lastDay, granularity)
        }

        val series = selectedIds.map { id ->
            val src = sources.getValue(id)
            val byBucket = src.entries.groupBy { dayToBucketKey.getValue(it.key) }
            val values = orderedBucketKeys.map { key ->
                byBucket[key]?.maxByOrNull { it.key }?.value
            }
            TrendSeries(
                id = id,
                name = when (id) {
                    TREND_TOTAL_ID -> "总净值"
                    TREND_CUSTOM_ID -> TREND_CUSTOM_LABEL
                    else -> categoryLabel(id)
                },
                values = values,
            )
        }
        return TrendChartData(orderedBucketKeys.map { bucketLabel.getValue(it) }, series)
    }

    private fun bucketKey(epochDay: Long, granularity: TimeGranularity): Long {
        val date = LocalDate.ofEpochDay(epochDay)
        return when (granularity) {
            TimeGranularity.DAY -> epochDay
            TimeGranularity.MONTH -> date.year * 100L + date.monthValue
            TimeGranularity.YEAR -> date.year.toLong()
        }
    }

    private fun label(epochDay: Long, granularity: TimeGranularity): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return when (granularity) {
            TimeGranularity.DAY -> "%02d-%02d".format(date.monthValue, date.dayOfMonth)
            TimeGranularity.MONTH -> "%d-%02d".format(date.year, date.monthValue)
            TimeGranularity.YEAR -> date.year.toString()
        }
    }
}
