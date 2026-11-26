package com.yingjing.pfa.data.repository

import android.util.Log
import com.yingjing.pfa.data.local.AppMetaDao
import com.yingjing.pfa.data.local.AppMetaEntity
import com.yingjing.pfa.data.local.MarketSeriesDao
import com.yingjing.pfa.data.local.MarketSeriesPointEntity
import com.yingjing.pfa.data.remote.MarketSeriesRemote
import com.yingjing.pfa.data.remote.SeriesFetch
import com.yingjing.pfa.di.ApplicationScope
import com.yingjing.pfa.domain.model.FearIndexId
import com.yingjing.pfa.domain.model.PriceSeriesId
import com.yingjing.pfa.domain.model.SeriesPoint
import com.yingjing.pfa.domain.repository.MarketSentimentRepository
import com.yingjing.pfa.domain.repository.SentimentRefreshOutcome
import com.yingjing.pfa.domain.repository.SentimentRefreshResult
import com.yingjing.pfa.domain.repository.SentimentSyncState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketSentimentRepositoryImpl @Inject constructor(
    private val remote: MarketSeriesRemote,
    private val dao: MarketSeriesDao,
    private val appMetaDao: AppMetaDao,
    @ApplicationScope private val appScope: CoroutineScope,
) : MarketSentimentRepository {

    private val _syncState = MutableStateFlow(SentimentSyncState())
    override val syncState: StateFlow<SentimentSyncState> = _syncState.asStateFlow()

    private val lock = Mutex()
    private var inFlight: Deferred<SentimentRefreshResult>? = null

    /** 测试用：置非 null 时覆盖当前时间。 */
    internal var nowMsOverride: Long? = null

    private fun nowMs(): Long = nowMsOverride ?: System.currentTimeMillis()

    private fun today(): LocalDate = Instant.ofEpochMilli(nowMs()).atZone(ZoneOffset.UTC).toLocalDate()

    override fun observeSeries(codes: List<String>, fromDay: Long?): Flow<Map<String, List<SeriesPoint>>> =
        (if (fromDay == null) dao.observeSeries(codes) else dao.observeSeriesSince(codes, fromDay))
            .map { rows -> rows.groupBy({ it.seriesCode }, { SeriesPoint(it.epochDay, it.value) }) }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()

    override suspend fun refresh(force: Boolean): SentimentRefreshResult {
        val result = joinOrStart(force).await()
        // 强制刷新撞上一次「新鲜度跳过」的进行中任务时，再真正执行一次。
        return if (force && result.outcome == SentimentRefreshOutcome.SKIPPED) joinOrStart(true).await() else result
    }

    private suspend fun joinOrStart(force: Boolean): Deferred<SentimentRefreshResult> = lock.withLock {
        inFlight?.takeIf { it.isActive } ?: appScope.async { runRefresh(force) }.also { inFlight = it }
    }

    private suspend fun runRefresh(force: Boolean): SentimentRefreshResult {
        loadPersistedState()
        if (!force && !isStale()) {
            return SentimentRefreshResult(SentimentRefreshOutcome.SKIPPED, _syncState.value.failedCodes)
        }
        _syncState.update { it.copy(refreshing = true) }
        return try {
            val fetched = fetchAll()
            persist(fetched)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 数据库写入等非网络异常：记为整体失败，保留旧缓存，不让异常冒泡到 UI 协程。
            Log.w(TAG, "market sentiment refresh failed: ${e.javaClass.simpleName}")
            val failed = ALL_CODES.toSet()
            recordAttempt(failed, success = false)
            SentimentRefreshResult(SentimentRefreshOutcome.FAILED, failed)
        } finally {
            _syncState.update { it.copy(refreshing = false) }
        }
    }

    /** 一个序列的抓取结果 + 抓取前本地最新日（决定写库方式）。 */
    private data class Fetched(val code: String, val localLatest: Long?, val fetch: SeriesFetch)

    /** 并行抓取全部序列（每个序列独立成败）。 */
    private suspend fun fetchAll(): List<Fetched> = coroutineScope {
        val fear = FearIndexId.entries.map { id -> async { fetchFear(id) } }
        val price = PriceSeriesId.entries.map { id -> async { fetchPrice(id) } }
        (fear + price).awaitAll()
    }

    private suspend fun fetchFear(id: FearIndexId): Fetched {
        val latest = dao.latestDay(id.code)
        val since = latest?.let(LocalDate::ofEpochDay)
        // 本地无数据时不能发条件请求（否则 304 后仍是空缓存）。
        val lastModified = if (latest != null) appMetaDao.get(lastModifiedKey(id.code)) else null
        return Fetched(id.code, latest, remote.fetchFear(id, since, lastModified))
    }

    private suspend fun fetchPrice(id: PriceSeriesId): Fetched {
        val latest = dao.latestDay(id.code)
        val since = latest?.let { LocalDate.ofEpochDay(it).minusDays(OVERLAP_DAYS) }
            ?: today().minusYears(PRICE_HISTORY_YEARS)
        return Fetched(id.code, latest, remote.fetchPrice(id, since))
    }

    /**
     * 单事务写库：
     * - 本地为空且源返回全量 → 整体替换；
     * - 本地已有数据 → 只 upsert 最近 [REVISION_WINDOW_DAYS] 天（CBOE 每次返回全量，
     *   但旧数据几乎不会修订，避免每天重写数万行）。
     */
    private suspend fun persist(fetched: List<Fetched>): SentimentRefreshResult {
        val replaced = mutableMapOf<String, List<MarketSeriesPointEntity>>()
        val upserted = mutableListOf<MarketSeriesPointEntity>()
        val failed = mutableSetOf<String>()
        fetched.forEach { (code, localLatest, fetch) ->
            when (fetch) {
                is SeriesFetch.Updated -> {
                    val rows = fetch.points.map { MarketSeriesPointEntity(code, it.epochDay, it.value) }
                    when {
                        localLatest == null && fetch.replaceAll -> replaced[code] = rows
                        localLatest == null -> upserted += rows
                        else -> upserted += rows.filter { it.epochDay >= localLatest - REVISION_WINDOW_DAYS }
                    }
                }
                SeriesFetch.NotModified -> Unit
                SeriesFetch.Failed -> failed += code
            }
        }
        dao.applyBatch(replaced, upserted)
        fetched.forEach { (code, _, fetch) ->
            val lm = (fetch as? SeriesFetch.Updated)?.lastModified
            if (lm != null) appMetaDao.put(AppMetaEntity(lastModifiedKey(code), lm))
        }
        val outcome = when {
            failed.isEmpty() -> SentimentRefreshOutcome.REFRESHED
            failed.size == fetched.size -> SentimentRefreshOutcome.FAILED
            else -> SentimentRefreshOutcome.PARTIAL
        }
        recordAttempt(failed, success = outcome != SentimentRefreshOutcome.FAILED)
        return SentimentRefreshResult(outcome, failed)
    }

    private suspend fun recordAttempt(failed: Set<String>, success: Boolean) {
        val now = nowMs()
        appMetaDao.put(AppMetaEntity(KEY_LAST_ATTEMPT, now.toString()))
        appMetaDao.put(AppMetaEntity(KEY_FAILED, failed.sorted().joinToString(",")))
        if (success) appMetaDao.put(AppMetaEntity(KEY_LAST_SUCCESS, now.toString()))
        _syncState.update {
            it.copy(failedCodes = failed, lastSuccessMs = if (success) now else it.lastSuccessMs)
        }
    }

    private suspend fun loadPersistedState() {
        val failed = appMetaDao.get(KEY_FAILED).orEmpty().split(',').filter { it.isNotBlank() }.toSet()
        val lastSuccess = appMetaDao.get(KEY_LAST_SUCCESS)?.toLongOrNull()
        _syncState.update { it.copy(failedCodes = failed, lastSuccessMs = lastSuccess) }
    }

    /**
     * 新鲜度判定：
     * - 从未尝试 / 时钟回拨 / 超过 [FRESHNESS_MS] / 完全无缓存 → 过期；
     * - 不足 [RETRY_AFTER_FAILURE_MS] → 新鲜（避免反复打开页面时连环请求失败的源）；
     * - 介于两者之间：上次有失败或有序列缺数据才重试。
     */
    private suspend fun isStale(): Boolean {
        val lastAttempt = appMetaDao.get(KEY_LAST_ATTEMPT)?.toLongOrNull() ?: return true
        val elapsed = nowMs() - lastAttempt
        if (elapsed < 0 || elapsed >= FRESHNESS_MS) return true
        val missing = ALL_CODES.count { dao.latestDay(it) == null }
        // 完全没有缓存（如首次离线失败）：每次进入都重试，否则页面会空白一小时。
        if (missing == ALL_CODES.size) return true
        if (elapsed < RETRY_AFTER_FAILURE_MS) return false
        return _syncState.value.failedCodes.isNotEmpty() || missing > 0
    }

    companion object {
        private const val TAG = "MarketSentiment"

        /**
         * 新鲜度窗口 4 小时。CBOE 走条件请求（无更新返回 304，约几百字节），其余源均为增量，
         * 因此缩短窗口只在数据真正更新时才产生下载，不会额外耗流量。
         */
        const val FRESHNESS_MS: Long = 4 * 60 * 60 * 1000L

        /** 失败后最短重试间隔 1 小时。 */
        const val RETRY_AFTER_FAILURE_MS: Long = 60 * 60 * 1000L

        /** 价格首次抓取回看 7 年（腾讯单次上限约 2000 条交易日）。 */
        const val PRICE_HISTORY_YEARS: Long = 7

        /** 增量回看天数：覆盖周末 / 节假日并吸收源端修订。 */
        const val OVERLAP_DAYS: Long = 7

        /** 已有缓存时只回写最近 30 天（吸收源端近期修订）。 */
        const val REVISION_WINDOW_DAYS: Long = 30

        const val KEY_LAST_ATTEMPT = "msent_last_attempt_ms"
        const val KEY_LAST_SUCCESS = "msent_last_success_ms"
        const val KEY_FAILED = "msent_failed_codes"

        fun lastModifiedKey(code: String) = "msent_lm_$code"

        val ALL_CODES: List<String> = FearIndexId.entries.map { it.code } + PriceSeriesId.entries.map { it.code }
    }
}
