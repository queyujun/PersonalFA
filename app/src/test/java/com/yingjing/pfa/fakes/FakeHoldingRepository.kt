package com.yingjing.pfa.fakes

import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.repository.HoldingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** 内存版 HoldingRepository，用于 UseCase / ViewModel 测试。 */
class FakeHoldingRepository : HoldingRepository {
    private val holdings = MutableStateFlow<List<Holding>>(emptyList())
    private var nextId = 1L

    override fun observeHoldings(userId: Long): Flow<List<Holding>> =
        holdings.map { list -> list.filter { it.userId == userId } }

    override suspend fun observeHoldingsSnapshot(userId: Long): List<Holding> =
        holdings.value.filter { it.userId == userId }

    override suspend fun getHolding(id: Long): Holding? = holdings.value.firstOrNull { it.id == id }

    override suspend fun addHolding(holding: Holding): Long {
        val id = nextId++
        holdings.value = holdings.value + holding.copy(id = id)
        return id
    }

    override suspend fun updateHolding(holding: Holding) {
        holdings.value = holdings.value.map { if (it.id == holding.id) holding else it }
    }

    override suspend fun deleteHolding(id: Long) {
        holdings.value = holdings.value.filterNot { it.id == id }
    }
}
