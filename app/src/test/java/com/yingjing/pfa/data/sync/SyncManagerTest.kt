package com.yingjing.pfa.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.domain.auth.RegisterResult
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.model.HousePriceSyncStatus
import com.yingjing.pfa.domain.model.QuoteFetchResult
import com.yingjing.pfa.domain.model.SyncSource
import com.yingjing.pfa.domain.model.User
import com.yingjing.pfa.domain.alert.AlertNotifier
import com.yingjing.pfa.domain.model.Alert
import com.yingjing.pfa.domain.model.AlertCategory
import com.yingjing.pfa.data.remote.HousePricePoint
import com.yingjing.pfa.data.remote.CommodityRemote
import com.yingjing.pfa.data.remote.MarketIndexRemote
import com.yingjing.pfa.data.remote.IpoRemote
import com.yingjing.pfa.domain.repository.AlertRepository
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.HousePriceRepository
import com.yingjing.pfa.domain.repository.HouseRefreshOutcome
import com.yingjing.pfa.domain.repository.QuoteRepository
import com.yingjing.pfa.domain.repository.SnapshotRepository
import com.yingjing.pfa.domain.repository.UserRepository
import com.yingjing.pfa.fakes.FakeHoldingRepository
import com.yingjing.pfa.fakes.FakeHousePriceRepository
import com.yingjing.pfa.fakes.FakeStringResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncManagerTest {

    private val holdingRepo = FakeHoldingRepository()
    private val housePriceRepo = FakeHousePriceRepository()
    private lateinit var syncStateStore: SyncStateStore
    private lateinit var globalRatesStore: GlobalRatesStore
    // 背景数据源：返回不触发任何提醒的非空小值（市场 <1.5% 警戒档、贵金属 <0.8% 警戒档），
    // 使测试聚焦各自目标（FX/价格/房产），不被「空结果计入失败」的新语义干扰。
    private var commodityResult: Map<String, Double> = mapOf("hf_XAU" to 0.5)
    private val commodityRemote = CommodityRemote { commodityResult }

    private val userRepo = object : UserRepository {
        override suspend fun register(username: String, password: String, defaultCurrency: Currency) =
            RegisterResult.UsernameTaken
        override suspend fun login(username: String, password: String) =
            com.yingjing.pfa.domain.auth.LoginResult.InvalidCredentials
        override suspend fun listUsers() = listOf(User(1, "alex", Currency.CNY, 0))
        override suspend fun getUser(id: Long) = null
        override fun observeUser(id: Long) = flowOf<User?>(null)
        override suspend fun updateDefaultCurrency(userId: Long, currency: Currency) {}
        override suspend fun changePassword(userId: Long, oldPassword: String, newPassword: String) = false
        override suspend fun changeUsername(userId: Long, newUsername: String) = false
        override suspend fun updateProfile(userId: Long, nickname: String?, gender: String?, age: Int?) {}
        override suspend fun deleteUser(userId: Long) {}
    }

    private var fxRefreshed = false
    private var currentRates = FxRates(7.0, 0.9)
    private val fxRepo = object : FxRepository {
        override fun observeRates(): Flow<FxRates> = flowOf(currentRates)
        override suspend fun current() = currentRates
        override suspend fun refresh(): FxRates { fxRefreshed = true; return currentRates }
        override suspend fun save(rates: FxRates) {}
    }

    private val recorded = mutableListOf<Double>()
    private val snapshotRepo = object : SnapshotRepository {
        override fun observe(userId: Long) =
            flowOf(emptyList<com.yingjing.pfa.domain.model.NetWorthPoint>())
        override fun observeCategories(userId: Long) =
            flowOf(emptyList<com.yingjing.pfa.domain.model.CategoryPoint>())
        override suspend fun record(
            userId: Long,
            currency: Currency,
            totalAssets: Double,
            totalLiabilities: Double,
            netWorth: Double,
            categoryAmounts: Map<String, Double>,
            nowMs: Long,
        ) {
            recorded += netWorth
        }
        override suspend fun deleteBefore(userId: Long, dayEpochDay: Long) {}
    }

    private val insertedAlerts = mutableListOf<Alert>()
    private val alertRepo = object : AlertRepository {
        override fun observe(userId: Long) = flowOf(emptyList<Alert>())
        override fun observeUnreadCount(userId: Long) = flowOf(0)
        override suspend fun insertIfNew(alert: Alert): Boolean {
            insertedAlerts += alert
            return true
        }
        override suspend fun markRead(id: Long) {}
        override suspend fun markAllRead(userId: Long) {}
    }
    private val notified = mutableListOf<Alert>()
    private val notifier = AlertNotifier { notified += it }

    private val marketIndexRemote = MarketIndexRemote { mapOf("sh000300" to 0.5) }
    private val ipoRemote = IpoRemote { emptyList() }

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        syncStateStore = SyncStateStore(ctx)
        globalRatesStore = GlobalRatesStore(ctx)
    }

    @Test
    fun sync_refreshesFx_andWritesPrices() = runTest {
        val id = holdingRepo.addHolding(
            Holding(userId = 1, type = AssetType.A_SHARE, name = "茅台", currency = Currency.CNY, symbol = "600519", quantity = 100.0, currentPrice = 1000.0),
        )
        val quoteRepo = object : QuoteRepository {
            override suspend fun fetchPrices(holdings: List<Holding>) =
                QuoteFetchResult(mapOf(id to 1354.5), emptyList())
        }
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, marketIndexRemote, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore, housePriceRepo, FakeStringResolver(), commodityRemote, globalRatesStore)

        val result = manager.sync()

        assertTrue(result.success)
        assertTrue(fxRefreshed)
        assertEquals(1354.5, holdingRepo.getHolding(id)!!.currentPrice!!, 0.001)
        assertEquals(1, recorded.size) // 记录了一条净值快照
        // 1000 -> 1354.5 约 +35%，超过 ±7% → 生成并通知一条提醒
        assertEquals(1, insertedAlerts.size)
        assertEquals(1, notified.size)
    }

    @Test
    fun sync_returnsFailureResult_onException() = runTest {
        val quoteRepo = object : QuoteRepository {
            override suspend fun fetchPrices(holdings: List<Holding>): QuoteFetchResult =
                throw RuntimeException("network")
        }
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, marketIndexRemote, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore, housePriceRepo, FakeStringResolver(), commodityRemote, globalRatesStore)
        // 加一个持仓触发抓取路径
        holdingRepo.addHolding(
            Holding(userId = 1, type = AssetType.A_SHARE, name = "茅台", currency = Currency.CNY, symbol = "600519", quantity = 1.0),
        )
        // 整体异常 → 全 8 源失败
        val result = manager.sync()
        assertFalse(result.success)
        assertEquals(SyncSource.entries.size, result.failedSources.size)
    }

    @Test
    fun sync_estimatesRealEstate_andWritesBackIntoSnapshot() = runTest {
        // 录入基准月 2026-03，现在 2026-07；录入值 100 万，北京
        val baseMs = msOf(2026, 3)
        val nowMs = msOf(2026, 7)
        val id = holdingRepo.addHolding(
            Holding(
                userId = 1, type = AssetType.REAL_ESTATE, name = "滨江一号",
                currency = Currency.CNY, manualValue = 1_000_000.0,
                city = "北京", autoEstimate = true, valueBaseDateEpochMs = baseMs,
            ),
        )
        // 北京指数：基准月之后两期环比，100 万 × 1.01 × 1.00 = 101 万
        housePriceRepo.seed(
            "北京",
            listOf(
                HousePricePoint("北京", "2026-03", null, null, 100.5, null), // 基准月不计入
                HousePricePoint("北京", "2026-04", null, null, 101.0, null),
                HousePricePoint("北京", "2026-05", null, null, 100.0, null),
            ),
        )
        val quoteRepo = object : QuoteRepository {
            override suspend fun fetchPrices(holdings: List<Holding>) =
                QuoteFetchResult(emptyMap(), emptyList())
        }
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, marketIndexRemote, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore, housePriceRepo, FakeStringResolver(), commodityRemote, globalRatesStore)
        manager.nowProvider = { nowMs }

        val result = manager.sync()

        assertTrue(result.success)
        // 估算值写回 estimatedValue
        assertEquals(1_010_000.0, holdingRepo.getHolding(id)!!.estimatedValue!!, 0.01)
        // 批量刷新了北京（去重 + 70 城校验）
        assertEquals(listOf("北京"), housePriceRepo.refreshCalledWith)
        // 净值快照反映估算值：101 万
        assertEquals(1, recorded.size)
        assertEquals(1_010_000.0, recorded[0], 0.01)
        // 房价指数同步备注：REFRESHED + 最新月份 2026-05，反馈给 UI 弹窗
        assertEquals(HousePriceSyncStatus.REFRESHED, result.housePriceNote?.status)
        assertEquals("2026-05", result.housePriceNote?.latestMonth)
    }

    @Test
    fun sync_realEstateEstimateOff_doesNotEstimate() = runTest {
        // autoEstimate=false 的房产：不应触发指数刷新，estimatedValue 保持 null，净值用手动值
        val id = holdingRepo.addHolding(
            Holding(
                userId = 1, type = AssetType.REAL_ESTATE, name = "自住",
                currency = Currency.CNY, manualValue = 2_000_000.0, city = "上海",
            ),
        )
        val quoteRepo = object : QuoteRepository {
            override suspend fun fetchPrices(holdings: List<Holding>) =
                QuoteFetchResult(emptyMap(), emptyList())
        }
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, marketIndexRemote, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore, housePriceRepo, FakeStringResolver(), commodityRemote, globalRatesStore)
        manager.nowProvider = { msOf(2026, 7) }

        assertTrue(manager.sync().success)
        assertNull(housePriceRepo.refreshCalledWith) // 无开启估算的房产 → 不刷新指数
        assertNull(holdingRepo.getHolding(id)!!.estimatedValue)
        assertEquals(2_000_000.0, recorded[0], 0.01)
    }

    @Test
    fun sync_housePriceNote_isNullWhenNoRealEstateHolding() = runTest {
        // 无房产持仓 → 不触发指数刷新，note 为 null（弹窗不展示房价行）。
        holdingRepo.addHolding(
            Holding(userId = 1, type = AssetType.A_SHARE, name = "茅台", currency = Currency.CNY, symbol = "600519", quantity = 1.0),
        )
        val quoteRepo = object : QuoteRepository {
            override suspend fun fetchPrices(holdings: List<Holding>) =
                QuoteFetchResult(emptyMap(), emptyList())
        }
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, marketIndexRemote, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore, housePriceRepo, FakeStringResolver(), commodityRemote, globalRatesStore)

        val result = manager.sync()

        assertNull(housePriceRepo.refreshCalledWith)
        assertNull(result.housePriceNote)
    }

    @Test
    fun sync_housePriceNote_reportsSkippedWithCachedMonth() = runTest {
        // 指数刷新返回 SKIPPED（新鲜度窗口内）→ note 状态 SKIPPED，仍反馈缓存里最新月份。
        val baseMs = msOf(2026, 3)
        val id = holdingRepo.addHolding(
            Holding(
                userId = 1, type = AssetType.REAL_ESTATE, name = "滨江一号",
                currency = Currency.CNY, manualValue = 1_000_000.0,
                city = "北京", autoEstimate = true, valueBaseDateEpochMs = baseMs,
            ),
        )
        housePriceRepo.seed(
            "北京",
            listOf(HousePricePoint("北京", "2026-05", null, null, 100.0, null)),
        )
        housePriceRepo.refreshResult = HouseRefreshOutcome.SKIPPED
        val quoteRepo = object : QuoteRepository {
            override suspend fun fetchPrices(holdings: List<Holding>) =
                QuoteFetchResult(emptyMap(), emptyList())
        }
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, marketIndexRemote, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore, housePriceRepo, FakeStringResolver(), commodityRemote, globalRatesStore)
        manager.nowProvider = { msOf(2026, 7) }

        val result = manager.sync()

        assertTrue(result.success)
        assertEquals(HousePriceSyncStatus.SKIPPED, result.housePriceNote?.status)
        assertEquals("2026-05", result.housePriceNote?.latestMonth)
    }

    private fun msOf(year: Int, month: Int): Long {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        cal.clear()
        cal.set(year, month - 1, 1, 0, 0, 0)
        return cal.timeInMillis
    }

    @Test
    fun sync_globalAlert_firesWhenCommodityChangeAboveThreshold() = runTest {
        // 伦敦金 +2.5 ≥ 严重档 2.0 → 1 条 GLOBAL SERIOUS。prevFx 未 seed → 汇率不触发。
        holdingRepo.addHolding(
            Holding(userId = 1, type = AssetType.A_SHARE, name = "茅台", currency = Currency.CNY, symbol = "600519", quantity = 1.0),
        )
        val quoteRepo = object : QuoteRepository {
            override suspend fun fetchPrices(holdings: List<Holding>) =
                QuoteFetchResult(emptyMap(), emptyList())
        }
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, marketIndexRemote, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore, housePriceRepo, FakeStringResolver(), commodityRemote, globalRatesStore)
        commodityResult = mapOf("hf_XAU" to 2.5)

        assertTrue(manager.sync().success)

        val global = insertedAlerts.filter { it.category == AlertCategory.GLOBAL }
        assertEquals(1, global.size)
    }

    @Test
    fun sync_globalAlert_fxFiresAfterPrevSeeded() = runTest {
        // prev USD=7.0，当前 7.2 → +2.85% ≥ 严重档 2.0 → 1 条 GLOBAL SERIOUS（usd_cny）。
        holdingRepo.addHolding(
            Holding(userId = 1, type = AssetType.A_SHARE, name = "茅台", currency = Currency.CNY, symbol = "600519", quantity = 1.0),
        )
        globalRatesStore.savePrevFx(FxRates(7.0, 0.9))
        currentRates = FxRates(7.2, 0.9)
        val quoteRepo = object : QuoteRepository {
            override suspend fun fetchPrices(holdings: List<Holding>) =
                QuoteFetchResult(emptyMap(), emptyList())
        }
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, marketIndexRemote, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore, housePriceRepo, FakeStringResolver(), commodityRemote, globalRatesStore)

        assertTrue(manager.sync().success)

        val global = insertedAlerts.filter { it.category == AlertCategory.GLOBAL }
        assertEquals(1, global.size)
    }

    @Test
    fun sync_marketIndexRetryOnEmpty() = runTest {
        // marketIndexRemote 第一次返回空、第二次返回沪深300 +3.5 → 重试后产生 1 条 MARKET SERIOUS。
        holdingRepo.addHolding(
            Holding(userId = 1, type = AssetType.A_SHARE, name = "茅台", currency = Currency.CNY, symbol = "600519", quantity = 1.0),
        )
        val quoteRepo = object : QuoteRepository {
            override suspend fun fetchPrices(holdings: List<Holding>) =
                QuoteFetchResult(emptyMap(), emptyList())
        }
        var callCount = 0
        val retryRemote = MarketIndexRemote {
            callCount++
            if (callCount == 1) emptyMap() else mapOf("sh000300" to 3.5)
        }
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, retryRemote, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore, housePriceRepo, FakeStringResolver(), commodityRemote, globalRatesStore)

        assertTrue(manager.sync().success)
        assertEquals(2, callCount) // 触发了重试
        val market = insertedAlerts.filter { it.category == AlertCategory.MARKET }
        assertEquals(1, market.size)
    }

    // —— 失败源追踪：海外源失败只拖自己，整体仍可成功部分 —— //

    @Test
    fun sync_partialFailure_reportsFailedSourcesButSucceedsOthers() = runTest {
        // 市场指数空 + 贵金属空（海外源模拟不可达），但 FX 正常、持仓无 → 仅部分源失败。
        holdingRepo.addHolding(
            Holding(userId = 1, type = AssetType.A_SHARE, name = "茅台", currency = Currency.CNY, symbol = "600519", quantity = 1.0),
        )
        // 股票抓取也返回空 → STOCK 失败
        val quoteRepo = object : QuoteRepository {
            override suspend fun fetchPrices(holdings: List<Holding>) =
                QuoteFetchResult(emptyMap(), listOf(SyncSource.STOCK))
        }
        // 局部空 remote，模拟海外源不可达（不依赖背景非空小值）
        val emptyMarketIndex = MarketIndexRemote { emptyMap() }
        val emptyCommodity = CommodityRemote { emptyMap() }
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, emptyMarketIndex, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore, housePriceRepo, FakeStringResolver(), emptyCommodity, globalRatesStore)

        val result = manager.sync()

        assertFalse(result.success) // 有失败源 → success=false
        assertTrue(result.failedSources.contains(SyncSource.MARKET_INDEX))
        assertTrue(result.failedSources.contains(SyncSource.COMMODITY))
        assertTrue(result.failedSources.contains(SyncSource.STOCK))
        assertFalse(result.failedSources.contains(SyncSource.FX)) // FX 正常（7.0/0.9）
    }

    @Test
    fun sync_manualFlag_propagatedToResult() = runTest {
        holdingRepo.addHolding(
            Holding(userId = 1, type = AssetType.A_SHARE, name = "茅台", currency = Currency.CNY, symbol = "600519", quantity = 1.0),
        )
        val quoteRepo = object : QuoteRepository {
            override suspend fun fetchPrices(holdings: List<Holding>) =
                QuoteFetchResult(emptyMap(), emptyList())
        }
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, marketIndexRemote, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore, housePriceRepo, FakeStringResolver(), commodityRemote, globalRatesStore)

        val manualResult = manager.sync(manual = true)
        assertTrue(manualResult.manual)

        val autoResult = manager.sync(manual = false)
        assertFalse(autoResult.manual)
    }
}
