package com.yingjing.pfa.domain.repository

import com.yingjing.pfa.domain.model.Holding
import kotlinx.coroutines.flow.Flow

/** 持仓仓库：全类别资产的增删改查。 */
interface HoldingRepository {
    fun observeHoldings(userId: Long): Flow<List<Holding>>
    suspend fun observeHoldingsSnapshot(userId: Long): List<Holding>
    suspend fun getHolding(id: Long): Holding?
    suspend fun addHolding(holding: Holding): Long
    suspend fun updateHolding(holding: Holding)
    suspend fun deleteHolding(id: Long)
}
