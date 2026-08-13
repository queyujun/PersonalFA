package com.yingjing.pfa.domain.repository

import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.NetWorthPoint
import kotlinx.coroutines.flow.Flow

/** 每日净值快照仓库。 */
interface SnapshotRepository {
    fun observe(userId: Long): Flow<List<NetWorthPoint>>

    /** 记录（同一天覆盖）当日净值快照。 */
    suspend fun record(
        userId: Long,
        currency: Currency,
        totalAssets: Double,
        totalLiabilities: Double,
        netWorth: Double,
        nowMs: Long,
    )

    companion object {
        const val MS_PER_DAY = 86_400_000L
        fun epochDay(nowMs: Long): Long = nowMs / MS_PER_DAY
    }
}
