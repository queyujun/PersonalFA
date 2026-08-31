package com.yingjing.pfa.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.data.local.AppDatabase
import com.yingjing.pfa.data.local.AppMetaEntity
import com.yingjing.pfa.data.remote.HousePricePoint
import com.yingjing.pfa.data.remote.HousePriceRemote
import com.yingjing.pfa.domain.repository.HousePriceRepository
import com.yingjing.pfa.domain.repository.HouseRefreshOutcome
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HousePriceRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: HousePriceRepositoryImpl
    private val remote = FakeHousePriceRemote()

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = HousePriceRepositoryImpl(remote, db.housePriceDao(), db.appMetaDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun pt(month: String, second: Double?) =
        HousePricePoint("北京", month, null, null, second, null)

    @Test
    fun refresh_upsertsAndHistory_returnsAscendingByMonth() = runTest {
        remote.seed("北京", listOf(pt("2026-05", 100.0), pt("2026-04", 101.0), pt("2026-03", 100.5)))
        // 固定当前时间，确保 isStale 为 true（无上次抓取记录 → stale）
        repository.nowMsOverride = 10_000_000_000L

        assertEquals(HouseRefreshOutcome.REFRESHED, repository.refresh(listOf("北京")))

        val history = repository.history("北京")
        assertEquals(listOf("2026-03", "2026-04", "2026-05"), history.map { it.month })
        assertEquals(101.0, history.first { it.month == "2026-04" }.secondSequential!!, 0.001)
    }

    @Test
    fun refresh_replacesCityHistory() = runTest {
        repository.nowMsOverride = 10_000_000_000L
        remote.seed("北京", listOf(pt("2026-04", 101.0)))
        repository.refresh(listOf("北京"))
        // 再次用更少的数据刷新（模拟接口只返回新窗口）
        remote.seed("北京", listOf(pt("2026-05", 99.9)))
        repository.nowMsOverride = 10_000_000_000L + HousePriceRepository.FRESHNESS_MS + 1
        repository.refresh(listOf("北京"))

        val history = repository.history("北京")
        assertEquals(listOf("2026-05"), history.map { it.month })
    }

    @Test
    fun refresh_skipsWhenFresh() = runTest {
        repository.nowMsOverride = 10_000_000_000L
        remote.seed("北京", listOf(pt("2026-04", 101.0)))
        assertEquals(HouseRefreshOutcome.REFRESHED, repository.refresh(listOf("北京")))

        // 新鲜度窗口内再次刷新 → 跳过，不抓远端
        remote.seed("北京", listOf(pt("2026-05", 99.9)))
        repository.nowMsOverride = 10_000_000_000L + 1_000L
        assertEquals(HouseRefreshOutcome.SKIPPED, repository.refresh(listOf("北京")))
        // 缓存未被覆盖
        assertEquals(listOf("2026-04"), repository.history("北京").map { it.month })
    }

    @Test
    fun refresh_emptyCities_returnsFailed() = runTest {
        repository.nowMsOverride = 10_000_000_000L
        assertEquals(HouseRefreshOutcome.FAILED, repository.refresh(emptyList()))
        assertFalse(remote.fetchCalled)
    }

    @Test
    fun refresh_emptyRemoteResult_returnsFailedAndDoesNotInvalidateCache() = runTest {
        repository.nowMsOverride = 10_000_000_000L
        remote.seed("北京", listOf(pt("2026-04", 101.0)))
        assertEquals(HouseRefreshOutcome.REFRESHED, repository.refresh(listOf("北京")))

        // 远端空返回 → 不删除已有缓存、不更新新鲜度戳
        remote.clear()
        repository.nowMsOverride = 10_000_000_000L + HousePriceRepository.FRESHNESS_MS + 1
        assertEquals(HouseRefreshOutcome.FAILED, repository.refresh(listOf("北京")))
        assertEquals(listOf("2026-04"), repository.history("北京").map { it.month })
    }

    @Test
    fun history_unknownCity_returnsEmpty() = runTest {
        assertEquals(emptyList<HousePricePoint>(), repository.history("不存在"))
    }
}

/** 内存版 HousePriceRemote：按城市预置数据，空列表表示抓取失败。 */
private class FakeHousePriceRemote : HousePriceRemote {
    private val stored = mutableMapOf<String, MutableList<HousePricePoint>>()
    var fetchCalled = false
    var emptyMode = false

    fun seed(city: String, points: List<HousePricePoint>) {
        stored.getOrPut(city) { mutableListOf() }.apply {
            clear()
            addAll(points)
        }
    }

    fun clear() {
        emptyMode = true
    }

    override suspend fun fetch(cities: List<String>): List<HousePricePoint> {
        fetchCalled = true
        if (emptyMode) return emptyList()
        return cities.flatMap { stored[it] ?: emptyList() }
    }
}
