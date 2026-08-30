package com.yingjing.pfa.domain.repository

import com.yingjing.pfa.domain.model.FxRates
import kotlinx.coroutines.flow.Flow

/** 汇率仓库：本地缓存 + 远程刷新。 */
interface FxRepository {
    fun observeRates(): Flow<FxRates>
    suspend fun current(): FxRates
    suspend fun refresh(): FxRates

    /** 直接写入本地缓存（不联网）；用于从备份恢复汇率。 */
    suspend fun save(rates: FxRates)
}
