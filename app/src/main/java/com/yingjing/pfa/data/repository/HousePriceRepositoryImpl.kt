package com.yingjing.pfa.data.repository

import com.yingjing.pfa.data.local.AppMetaDao
import com.yingjing.pfa.data.local.AppMetaEntity
import com.yingjing.pfa.data.local.HousePriceDao
import com.yingjing.pfa.data.local.HousePriceIndexEntity
import com.yingjing.pfa.data.remote.HousePricePoint
import com.yingjing.pfa.data.remote.HousePriceRemote
import com.yingjing.pfa.domain.repository.HousePriceRepository
import javax.inject.Inject

class HousePriceRepositoryImpl @Inject constructor(
    private val remote: HousePriceRemote,
    private val dao: HousePriceDao,
    private val appMetaDao: AppMetaDao,
) : HousePriceRepository {

    override suspend fun refresh(cities: List<String>): Boolean {
        if (cities.isEmpty()) return false
        if (!isStale()) return false
        val points = remote.fetch(cities)
        if (points.isEmpty()) return false
        // 仅刷新本次涉及城市，避免误删他城缓存
        cities.forEach { dao.deleteByCity(it) }
        dao.upsertAll(points.map { it.toEntity() })
        appMetaDao.put(AppMetaEntity(HousePriceRepository.LAST_FETCH_KEY, nowMs().toString()))
        return true
    }

    override suspend fun history(city: String): List<HousePricePoint> =
        dao.historyByCity(city).map { it.toPoint() }

    private suspend fun isStale(): Boolean {
        val last = appMetaDao.get(HousePriceRepository.LAST_FETCH_KEY)?.toLongOrNull() ?: 0L
        return nowMs() - last >= HousePriceRepository.FRESHNESS_MS
    }

    /** 注入当前时间，便于测试覆盖。 */
    internal fun nowMs(): Long = nowMsOverride ?: System.currentTimeMillis()

    /** 测试用：置非 null 时覆盖 [nowMs] 返回值，固定新鲜度判定基准。 */
    internal var nowMsOverride: Long? = null

    private fun HousePricePoint.toEntity() = HousePriceIndexEntity(
        city = city,
        month = month,
        newSequential = newSequential,
        newSame = newSame,
        secondSequential = secondSequential,
        secondSame = secondSame,
    )

    private fun HousePriceIndexEntity.toPoint() = HousePricePoint(
        city = city,
        month = month,
        newSequential = newSequential,
        newSame = newSame,
        secondSequential = secondSequential,
        secondSame = secondSame,
    )
}
