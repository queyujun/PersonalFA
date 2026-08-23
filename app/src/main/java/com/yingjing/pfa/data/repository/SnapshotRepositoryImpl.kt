package com.yingjing.pfa.data.repository

import com.yingjing.pfa.data.local.CategorySnapshotDao
import com.yingjing.pfa.data.local.CategorySnapshotEntity
import com.yingjing.pfa.data.local.NetWorthSnapshotDao
import com.yingjing.pfa.data.local.NetWorthSnapshotEntity
import com.yingjing.pfa.domain.model.CategoryPoint
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.NetWorthPoint
import com.yingjing.pfa.domain.repository.SnapshotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SnapshotRepositoryImpl @Inject constructor(
    private val dao: NetWorthSnapshotDao,
    private val categoryDao: CategorySnapshotDao,
) : SnapshotRepository {

    override fun observe(userId: Long): Flow<List<NetWorthPoint>> =
        dao.observeByUser(userId).map { list ->
            list.map { NetWorthPoint(epochDay = it.dayEpochDay, netWorth = it.netWorth) }
        }

    override fun observeCategories(userId: Long): Flow<List<CategoryPoint>> =
        categoryDao.observeByUser(userId).map { list ->
            list.map { CategoryPoint(epochDay = it.dayEpochDay, category = it.category, amount = it.amount) }
        }

    override suspend fun record(
        userId: Long,
        currency: Currency,
        totalAssets: Double,
        totalLiabilities: Double,
        netWorth: Double,
        categoryAmounts: Map<String, Double>,
        nowMs: Long,
    ) {
        val day = SnapshotRepository.epochDay(nowMs)
        dao.upsert(
            NetWorthSnapshotEntity(
                userId = userId,
                dayEpochDay = day,
                currency = currency.code,
                totalAssets = totalAssets,
                totalLiabilities = totalLiabilities,
                netWorth = netWorth,
                createdAt = nowMs,
            ),
        )
        if (categoryAmounts.isNotEmpty()) {
            categoryDao.upsertAll(
                categoryAmounts.map { (category, amount) ->
                    CategorySnapshotEntity(userId = userId, dayEpochDay = day, category = category, amount = amount)
                },
            )
        }
    }
}
