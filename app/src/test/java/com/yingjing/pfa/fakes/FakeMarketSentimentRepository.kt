package com.yingjing.pfa.fakes

import com.yingjing.pfa.domain.model.SeriesPoint
import com.yingjing.pfa.domain.repository.MarketSentimentRepository
import com.yingjing.pfa.domain.repository.SentimentRefreshOutcome
import com.yingjing.pfa.domain.repository.SentimentRefreshResult
import com.yingjing.pfa.domain.repository.SentimentSyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** 内存版市场情绪仓库：可预置序列、记录 refresh 调用、指定刷新结果。 */
class FakeMarketSentimentRepository : MarketSentimentRepository {
    private val data = MutableStateFlow<Map<String, List<SeriesPoint>>>(emptyMap())
    override val syncState = MutableStateFlow(SentimentSyncState())

    val refreshCalls = mutableListOf<Boolean>()
    var nextResult = SentimentRefreshResult(SentimentRefreshOutcome.REFRESHED)

    fun seed(code: String, points: List<SeriesPoint>) {
        data.value = data.value + (code to points)
    }

    override fun observeSeries(codes: List<String>, fromDay: Long?): Flow<Map<String, List<SeriesPoint>>> =
        data.map { all ->
            all.filterKeys { it in codes }
                .mapValues { (_, pts) -> if (fromDay == null) pts else pts.filter { it.epochDay >= fromDay } }
                .filterValues { it.isNotEmpty() }
        }

    override suspend fun refresh(force: Boolean): SentimentRefreshResult {
        refreshCalls += force
        return nextResult
    }
}
