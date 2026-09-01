package com.yingjing.pfa.data.repository

import com.yingjing.pfa.data.local.AppMetaDao
import com.yingjing.pfa.data.local.AppMetaEntity
import com.yingjing.pfa.data.local.HousePriceDao
import com.yingjing.pfa.data.local.HousePriceIndexEntity
import com.yingjing.pfa.data.remote.HousePricePoint
import com.yingjing.pfa.data.remote.HousePriceRemote
import com.yingjing.pfa.domain.repository.HousePriceRepository
import com.yingjing.pfa.domain.repository.HouseRefreshOutcome
import com.yingjing.pfa.domain.repository.HouseRefreshResult
import javax.inject.Inject

class HousePriceRepositoryImpl @Inject constructor(
    private val remote: HousePriceRemote,
    private val dao: HousePriceDao,
    private val appMetaDao: AppMetaDao,
) : HousePriceRepository {

    override suspend fun refresh(cities: List<String>): HouseRefreshResult {
        if (cities.isEmpty()) return HouseRefreshResult(HouseRefreshOutcome.FAILED, null)
        if (!isStale()) {
            // 新鲜度窗口内跳过：用缓存里已有的最新月份告诉用户指数停在哪个月。
            val cachedLatest = cities.mapNotNull { latestCachedMonth(it) }.maxByOrNull { it }
            return HouseRefreshResult(HouseRefreshOutcome.SKIPPED, cachedLatest)
        }
        val points = runCatching { remote.fetch(cities) }
            .onFailure { return HouseRefreshResult(HouseRefreshOutcome.FAILED, null) }
            .getOrDefault(emptyList())
        if (points.isEmpty()) return HouseRefreshResult(HouseRefreshOutcome.FAILED, null)
        // 仅刷新本次涉及城市，避免误删他城缓存
        cities.forEach { dao.deleteByCity(it) }
        dao.upsertAll(points.map { it.toEntity() })
        appMetaDao.put(AppMetaEntity(HousePriceRepository.LAST_FETCH_KEY, nowMs().toString()))
        val latest = points.maxByOrNull { it.month }?.month
        return HouseRefreshResult(HouseRefreshOutcome.REFRESHED, latest)
    }

    override suspend fun history(city: String): List<HousePricePoint> =
        dao.historyByCity(city).map { it.toPoint() }

    private suspend fun isStale(): Boolean {
        val last = appMetaDao.get(HousePriceRepository.LAST_FETCH_KEY)?.toLongOrNull() ?: 0L
        return nowMs() - last >= HousePriceRepository.FRESHNESS_MS
    }

    /** 取 [city] 缓存中月份最大的记录（SKIPPED 时用于反馈「指数已是 X 月」）。 */
    private suspend fun latestCachedMonth(city: String): String? =
        dao.historyByCity(city).maxByOrNull { it.month }?.month

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
