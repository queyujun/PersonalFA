package com.yingjing.pfa.domain.repository

import com.yingjing.pfa.data.remote.HousePricePoint

/** 70 城住宅价格指数仓库：刷新远端数据并按城市提供历史。 */
interface HousePriceRepository {
    /**
     * 拉取 [cities] 的指数历史并写入缓存。带新鲜度节流：距上次成功刷新不足
     * [FRESHNESS_MS] 时直接跳过（省流量）。返回实际执行结果的三态。
     */
    suspend fun refresh(cities: List<String>): HouseRefreshOutcome

    /** 返回 [city] 的指数历史（按月份升序）。 */
    suspend fun history(city: String): List<HousePricePoint>

    companion object {
        /** 刷新新鲜度窗口（6 天 = 518_400_000 毫秒）。 */
        const val FRESHNESS_MS: Long = 6 * 24 * 60 * 60 * 1000L
        const val LAST_FETCH_KEY: String = "house_index_last_fetch_ms"
    }
}

/**
 * 房价指数刷新结果三态。区分「跳过」（新鲜度窗口内，非失败）与「失败」，
 * 供 SyncManager 的失败源口径精确统计。
 */
enum class HouseRefreshOutcome {
    /** 实际执行了远端请求并成功更新缓存。 */
    REFRESHED,
    /** 新鲜度窗口内跳过（非失败，无需计入 failedSources）。 */
    SKIPPED,
    /** 本应刷新但因无城市 / 远端空返回 / 异常而未更新（计入 failedSources）。 */
    FAILED,
}
