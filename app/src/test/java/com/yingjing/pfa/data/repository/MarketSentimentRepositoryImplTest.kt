package com.yingjing.pfa.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.data.local.AppDatabase
import com.yingjing.pfa.data.local.AppMetaEntity
import com.yingjing.pfa.data.remote.MarketSeriesRemote
import com.yingjing.pfa.data.remote.SeriesFetch
import com.yingjing.pfa.domain.model.FearIndexId
import com.yingjing.pfa.domain.model.PriceSeriesId
import com.yingjing.pfa.domain.model.SeriesPoint
import com.yingjing.pfa.domain.repository.SentimentRefreshOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class MarketSentimentRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var remote: FakeMarketSeriesRemote
    private lateinit var repo: MarketSentimentRepositoryImpl

    private val now = 1_795_600_000_000L // 2026-11-25 UTC

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        remote = FakeMarketSeriesRemote()
        repo = MarketSentimentRepositoryImpl(remote, db.marketSeriesDao(), db.appMetaDao(), scope).apply {
            nowMsOverride = now
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun pts(vararg pairs: Pair<Long, Double>) = pairs.map { SeriesPoint(it.first, it.second) }

    @Test
    fun `first refresh stores every series and reports refreshed`() = runBlocking {
        val result = repo.refresh()
        assertEquals(SentimentRefreshOutcome.REFRESHED, result.outcome)
        val all = repo.observeSeries(MarketSentimentRepositoryImpl.ALL_CODES).first()
        assertEquals(MarketSentimentRepositoryImpl.ALL_CODES.toSet(), all.keys)
        // 首次：恐慌指数无增量起点、无条件请求；价格从 7 年前开始
        assertNull(remote.fearCalls.first { it.id == FearIndexId.VIX }.since)
        assertNull(remote.fearCalls.first { it.id == FearIndexId.VIX }.ifModifiedSince)
        assertEquals(LocalDate.of(2019, 11, 25), remote.priceCalls.first { it.id == PriceSeriesId.SPX }.since)
        assertFalse(repo.syncState.value.refreshing)
        assertEquals(now, repo.syncState.value.lastSuccessMs)
    }

    @Test
    fun `refresh within freshness window is skipped unless forced`() = runBlocking {
        repo.refresh()
        val calls = remote.totalCalls.get()
        repo.nowMsOverride = now + 60_000
        assertEquals(SentimentRefreshOutcome.SKIPPED, repo.refresh().outcome)
        assertEquals(calls, remote.totalCalls.get())
        assertEquals(SentimentRefreshOutcome.REFRESHED, repo.refresh(force = true).outcome)
        assertTrue(remote.totalCalls.get() > calls)
    }

    @Test
    fun `stale after freshness window and second refresh sends conditional and incremental requests`() = runBlocking {
        remote.lastModified = "LM-1"
        repo.refresh()
        remote.fearCalls.clear()
        remote.priceCalls.clear()
        repo.nowMsOverride = now + MarketSentimentRepositoryImpl.FRESHNESS_MS + 1
        repo.refresh()
        val vix = remote.fearCalls.first { it.id == FearIndexId.VIX }
        assertEquals("LM-1", vix.ifModifiedSince)
        assertEquals(LocalDate.ofEpochDay(FakeMarketSeriesRemote.LAST_DAY), vix.since)
        val spx = remote.priceCalls.first { it.id == PriceSeriesId.SPX }
        assertEquals(LocalDate.ofEpochDay(FakeMarketSeriesRemote.LAST_DAY).minusDays(7), spx.since)
    }

    @Test
    fun `not modified keeps cached rows`() = runBlocking {
        repo.refresh()
        val before = db.marketSeriesDao().series(FearIndexId.VIX.code)
        remote.fearOverride = { SeriesFetch.NotModified }
        repo.refresh(force = true)
        assertEquals(before, db.marketSeriesDao().series(FearIndexId.VIX.code))
    }

    @Test
    fun `partial failure keeps old data for failed series and records failed codes`() = runBlocking {
        repo.refresh()
        val before = db.marketSeriesDao().series(FearIndexId.GVZ.code)
        remote.fearOverride = { id -> if (id == FearIndexId.GVZ) SeriesFetch.Failed else null }
        val result = repo.refresh(force = true)
        assertEquals(SentimentRefreshOutcome.PARTIAL, result.outcome)
        assertEquals(setOf(FearIndexId.GVZ.code), result.failedCodes)
        assertEquals(setOf(FearIndexId.GVZ.code), repo.syncState.value.failedCodes)
        assertEquals(before, db.marketSeriesDao().series(FearIndexId.GVZ.code))
        assertEquals(FearIndexId.GVZ.code, db.appMetaDao().get(MarketSentimentRepositoryImpl.KEY_FAILED))
    }

    @Test
    fun `all failed reports failed and empty cache retries on next visit`() = runBlocking {
        remote.fearOverride = { SeriesFetch.Failed }
        remote.priceOverride = { SeriesFetch.Failed }
        assertEquals(SentimentRefreshOutcome.FAILED, repo.refresh().outcome)
        // 缓存完全为空 → 即使在重试间隔内也再次尝试
        repo.nowMsOverride = now + 1_000
        remote.fearOverride = { null }
        remote.priceOverride = { null }
        assertEquals(SentimentRefreshOutcome.REFRESHED, repo.refresh().outcome)
    }

    @Test
    fun `existing cache only rewrites revision window of a full download`() = runBlocking {
        val day = FakeMarketSeriesRemote.LAST_DAY
        repo.refresh()
        // 源端「修订」了很久以前的值与近期的值：只有近 30 天内的修订会落库
        remote.fearOverride = { id ->
            if (id == FearIndexId.VIX) SeriesFetch.Updated(pts(day - 100 to 99.0, day - 1 to 55.0, day + 1 to 30.0), replaceAll = true) else null
        }
        repo.refresh(force = true)
        val vix = db.marketSeriesDao().series(FearIndexId.VIX.code).associate { it.epochDay to it.value }
        assertFalse("30 天修订窗口之外的数据不应写入", vix.containsKey(day - 100))
        assertEquals(55.0, vix.getValue(day - 1), 1e-9)
        assertEquals(30.0, vix.getValue(day + 1), 1e-9)
        // 窗口内未被源端返回的旧点保持不变（不因「全量」而被删除）
        assertEquals(10.0, vix.getValue(day - 2), 1e-9)
    }

    @Test
    fun `concurrent refresh calls share one fetch`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        remote.gate = gate
        val a = async(Dispatchers.IO) { repo.refresh() }
        val b = async(Dispatchers.IO) { repo.refresh() }
        while (remote.totalCalls.get() == 0) Thread.sleep(5)
        gate.complete(Unit)
        val ra = a.await()
        val rb = b.await()
        assertEquals(ra, rb)
        assertEquals(MarketSentimentRepositoryImpl.ALL_CODES.size, remote.totalCalls.get())
    }

    @Test
    fun `observe with fromDay filters older rows`() = runBlocking {
        repo.refresh()
        val day = FakeMarketSeriesRemote.LAST_DAY
        val recent = repo.observeSeries(listOf(FearIndexId.VIX.code), fromDay = day - 1).first()
        assertEquals(listOf(day - 1, day), recent.getValue(FearIndexId.VIX.code).map { it.epochDay })
    }

    @Test
    fun `persisted failed codes are restored on next refresh decision`() = runBlocking {
        db.appMetaDao().put(AppMetaEntity(MarketSentimentRepositoryImpl.KEY_FAILED, "fx_vix,px_btc"))
        db.appMetaDao().put(AppMetaEntity(MarketSentimentRepositoryImpl.KEY_LAST_ATTEMPT, now.toString()))
        // 有缓存才会走「新鲜跳过」分支
        db.marketSeriesDao().upsertAll(
            MarketSentimentRepositoryImpl.ALL_CODES.map { com.yingjing.pfa.data.local.MarketSeriesPointEntity(it, 1, 1.0) },
        )
        assertEquals(SentimentRefreshOutcome.SKIPPED, repo.refresh().outcome)
        assertEquals(setOf("fx_vix", "px_btc"), repo.syncState.value.failedCodes)
    }
}

/** 可编程的假远端：默认每个序列返回 3 个点（LAST_DAY-2..LAST_DAY），可按序列覆盖结果。 */
private class FakeMarketSeriesRemote : MarketSeriesRemote {
    data class FearCall(val id: FearIndexId, val since: LocalDate?, val ifModifiedSince: String?)
    data class PriceCall(val id: PriceSeriesId, val since: LocalDate)

    val fearCalls = java.util.Collections.synchronizedList(mutableListOf<FearCall>())
    val priceCalls = java.util.Collections.synchronizedList(mutableListOf<PriceCall>())
    val totalCalls = AtomicInteger()
    var lastModified: String? = null
    var fearOverride: (FearIndexId) -> SeriesFetch? = { null }
    var priceOverride: (PriceSeriesId) -> SeriesFetch? = { null }
    var gate: CompletableDeferred<Unit>? = null

    private fun defaultPoints() = (LAST_DAY - 2..LAST_DAY).map { SeriesPoint(it, 10.0 + (it - LAST_DAY + 2)) }

    override suspend fun fetchFear(id: FearIndexId, since: LocalDate?, ifModifiedSince: String?): SeriesFetch {
        totalCalls.incrementAndGet()
        fearCalls += FearCall(id, since, ifModifiedSince)
        gate?.await()
        return fearOverride(id) ?: SeriesFetch.Updated(defaultPoints(), replaceAll = true, lastModified = lastModified)
    }

    override suspend fun fetchPrice(id: PriceSeriesId, since: LocalDate): SeriesFetch {
        totalCalls.incrementAndGet()
        priceCalls += PriceCall(id, since)
        gate?.await()
        return priceOverride(id) ?: SeriesFetch.Updated(defaultPoints())
    }

    companion object {
        val LAST_DAY: Long = LocalDate.of(2026, 11, 24).toEpochDay()
    }
}
