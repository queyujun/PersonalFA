package com.yingjing.pfa.fakes

import com.yingjing.pfa.data.remote.HousePricePoint
import com.yingjing.pfa.domain.repository.HousePriceRepository

/** 内存版 HousePriceRepository，用于 SyncManager 测试。 */
class FakeHousePriceRepository : HousePriceRepository {
    val stored = mutableMapOf<String, MutableList<HousePricePoint>>()
    var refreshCalledWith: List<String>? = null
    var refreshResult: Boolean = true

    fun seed(city: String, points: List<HousePricePoint>) {
        stored.getOrPut(city) { mutableListOf() }.apply {
            clear()
            addAll(points.sortedBy { it.month })
        }
    }

    override suspend fun refresh(cities: List<String>): Boolean {
        refreshCalledWith = cities
        return refreshResult
    }

    override suspend fun history(city: String): List<HousePricePoint> =
        stored[city]?.toList() ?: emptyList()
}
