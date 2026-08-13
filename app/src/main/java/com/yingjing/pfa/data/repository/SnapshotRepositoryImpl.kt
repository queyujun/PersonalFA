package com.yingjing.pfa.data.repository

import com.yingjing.pfa.data.local.NetWorthSnapshotDao
import com.yingjing.pfa.data.local.NetWorthSnapshotEntity
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.NetWorthPoint
import com.yingjing.pfa.domain.repository.SnapshotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SnapshotRepositoryImpl @Inject constructor(
    private val dao: NetWorthSnapshotDao,
) : SnapshotRepository {

    override fun observe(userId: Long): Flow<List<NetWorthPoint>> =
        dao.observeByUser(userId).map { list ->
            list.map { NetWorthPoint(epochDay = it.dayEpochDay, netWorth = it.netWorth) }
        }

    override suspend fun record(
        userId: Long,
        currency: Currency,
        totalAssets: Double,
        totalLiabilities: Double,
        netWorth: Double,
        nowMs: Long,
    ) {
        dao.upsert(
            NetWorthSnapshotEntity(
                userId = userId,
                dayEpochDay = SnapshotRepository.epochDay(nowMs),
                currency = currency.code,
                totalAssets = totalAssets,
                totalLiabilities = totalLiabilities,
                netWorth = netWorth,
                createdAt = nowMs,
            ),
        )
    }
}
