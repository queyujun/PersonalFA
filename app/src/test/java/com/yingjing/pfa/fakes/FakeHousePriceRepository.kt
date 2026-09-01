package com.yingjing.pfa.fakes

import com.yingjing.pfa.data.remote.HousePricePoint
import com.yingjing.pfa.domain.repository.HousePriceRepository
import com.yingjing.pfa.domain.repository.HouseRefreshOutcome
import com.yingjing.pfa.domain.repository.HouseRefreshResult

/** 内存版 HousePriceRepository，用于 SyncManager 测试。 */
class FakeHousePriceRepository : HousePriceRepository {
    val stored = mutableMapOf<String, MutableList<HousePricePoint>>()
    var refreshCalledWith: List<String>? = null
    var refreshResult: HouseRefreshOutcome = HouseRefreshOutcome.REFRESHED

    fun seed(city: String, points: List<HousePricePoint>) {
        stored.getOrPut(city) { mutableListOf() }.apply {
            clear()
            addAll(points.sortedBy { it.month })
        }
    }

    override suspend fun refresh(cities: List<String>): HouseRefreshResult {
        refreshCalledWith = cities
        // REFRESHED 时反馈缓存里的最新月份；SKIPPED/FAILED 沿用真实实现语义（SKIPPED
        // 反馈缓存月份、FAILED 给 null），让 SyncManager 的 note 在测试中可断言。
        val latest = cities.mapNotNull { stored[it]?.maxByOrNull { p -> p.month }?.month }
            .maxByOrNull { it }
        return HouseRefreshResult(refreshResult, latest)
    }

    override suspend fun history(city: String): List<HousePricePoint> =
        stored[city]?.toList() ?: emptyList()
}
