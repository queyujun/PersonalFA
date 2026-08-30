package com.yingjing.pfa.domain.repository

import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.CategoryPoint
import com.yingjing.pfa.domain.model.NetWorthPoint
import kotlinx.coroutines.flow.Flow

/** 每日净值 + 分类金额快照仓库。 */
interface SnapshotRepository {
    fun observe(userId: Long): Flow<List<NetWorthPoint>>

    fun observeCategories(userId: Long): Flow<List<CategoryPoint>>

    /** 记录（同一天覆盖）当日净值与各类别金额（categoryAmounts: 类别名 → 金额）。 */
    suspend fun record(
        userId: Long,
        currency: Currency,
        totalAssets: Double,
        totalLiabilities: Double,
        netWorth: Double,
        categoryAmounts: Map<String, Double>,
        nowMs: Long,
    )

    /** 删除某用户指定日期（不含当天）之前的所有历史快照（净值 + 分类）。 */
    suspend fun deleteBefore(userId: Long, dayEpochDay: Long)

    companion object {
        const val MS_PER_DAY = 86_400_000L
        fun epochDay(nowMs: Long): Long = nowMs / MS_PER_DAY
    }
}
