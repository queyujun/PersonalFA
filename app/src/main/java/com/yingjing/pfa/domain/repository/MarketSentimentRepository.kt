package com.yingjing.pfa.domain.repository

import com.yingjing.pfa.domain.model.SeriesPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 市场情绪（恐慌指数 + 叠加价格）仓库：本地缓存 + 按需刷新。
 *
 * 刷新策略：进入页面调用 [refresh]（force=false），距上次尝试不足新鲜度窗口则跳过；
 * 用户手动刷新传 force=true。并发调用合并为同一次抓取，抓取在应用级作用域执行，
 * 离开页面不会中断已开始的下载。
 */
interface MarketSentimentRepository {

    /** 刷新进行中 / 上次失败的序列，供 UI 展示进度与「部分失败」提示。 */
    val syncState: StateFlow<SentimentSyncState>

    /**
     * 订阅若干序列（code → 升序日线）。缺数据的序列不出现在 map 中。
     * [fromDay] 非 null 时只返回该日（含）之后的数据。
     */
    fun observeSeries(codes: List<String>, fromDay: Long? = null): Flow<Map<String, List<SeriesPoint>>>

    suspend fun refresh(force: Boolean = false): SentimentRefreshResult
}

data class SentimentSyncState(
    val refreshing: Boolean = false,
    /** 上次刷新失败的序列 code（恐慌指数或价格）。 */
    val failedCodes: Set<String> = emptySet(),
    val lastSuccessMs: Long? = null,
)

enum class SentimentRefreshOutcome {
    /** 全部序列成功（含 304 未变化）。 */
    REFRESHED,

    /** 部分序列失败，其余已更新。 */
    PARTIAL,

    /** 新鲜度窗口内跳过（非失败）。 */
    SKIPPED,

    /** 全部失败。 */
    FAILED,
}

data class SentimentRefreshResult(
    val outcome: SentimentRefreshOutcome,
    val failedCodes: Set<String> = emptySet(),
)
