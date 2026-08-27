package com.yingjing.pfa.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.domain.auth.RegisterResult
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.model.User
import com.yingjing.pfa.domain.alert.AlertNotifier
import com.yingjing.pfa.domain.model.Alert
import com.yingjing.pfa.data.remote.HousePricePoint
import com.yingjing.pfa.data.remote.MarketIndexRemote
import com.yingjing.pfa.data.remote.IpoRemote
import com.yingjing.pfa.domain.repository.AlertRepository
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.HousePriceRepository
import com.yingjing.pfa.domain.repository.QuoteRepository
import com.yingjing.pfa.domain.repository.SnapshotRepository
import com.yingjing.pfa.domain.repository.UserRepository
import com.yingjing.pfa.fakes.FakeHoldingRepository
import com.yingjing.pfa.fakes.FakeHousePriceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private val userRepo = object : UserRepository {
        override suspend fun register(username: String, password: String, defaultCurrency: Currency) =
            RegisterResult.UsernameTaken
        override suspend fun login(username: String, password: String) =
            com.yingjing.pfa.domain.auth.LoginResult.InvalidCredentials
        override suspend fun listUsers() = listOf(User(1, "alex", Currency.CNY, 0))
        override suspend fun getUser(id: Long) = null
        override suspend fun updateDefaultCurrency(userId: Long, currency: Currency) {}
        override suspend fun changePassword(userId: Long, oldPassword: String, newPassword: String) = false
        override suspend fun changeUsername(userId: Long, newUsername: String) = false
        override suspend fun updateProfile(userId: Long, nickname: String?, gender: String?, age: Int?) {}
        override suspend fun deleteUser(userId: Long) {}
    }

    private var fxRefreshed = false
    private val fxRepo = object : FxRepository {
        override fun observeRates(): Flow<FxRates> = flowOf(FxRates())
        override suspend fun current() = FxRates()
        override suspend fun refresh(): FxRates { fxRefreshed = true; return FxRates(7.0, 0.9) }
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

    private val marketIndexRemote = MarketIndexRemote { emptyMap() }
    private val ipoRemote = IpoRemote { emptyList() }

    @Before
    fun setup() {
        syncStateStore = SyncStateStore(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun sync_refreshesFx_andWritesPrices() = runTest {
        val id = holdingRepo.addHolding(
            Holding(userId = 1, type = AssetType.A_SHARE, name = "茅台", currency = Currency.CNY, symbol = "600519", quantity = 100.0, currentPrice = 1000.0),
        )
        val quoteRepo = object : QuoteRepository {
            override suspend fun fetchPrices(holdings: List<Holding>) = mapOf(id to 1354.5)
        }
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, marketIndexRemote, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore, housePriceRepo)

        val ok = manager.sync()

        assertTrue(ok)
        assertTrue(fxRefreshed)
        assertEquals(1354.5, holdingRepo.getHolding(id)!!.currentPrice!!, 0.001)
        assertEquals(1, recorded.size) // 记录了一条净值快照
        // 1000 -> 1354.5 约 +35%，超过 ±7% → 生成并通知一条提醒
        assertEquals(1, insertedAlerts.size)
        assertEquals(1, notified.size)
    }

    @Test
    fun sync_returnsFalse_onException() = runTest {
        val quoteRepo = object : QuoteRepository {
            override suspend fun fetchPrices(holdings: List<Holding>): Map<Long, Double> =
                throw RuntimeException("network")
        }
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, marketIndexRemote, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore, housePriceRepo)
        // 加一个持仓触发抓取路径
        holdingRepo.addHolding(
            Holding(userId = 1, type = AssetType.A_SHARE, name = "茅台", currency = Currency.CNY, symbol = "600519", quantity = 1.0),
        )
        assertEquals(false, manager.sync())
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
            override suspend fun fetchPrices(holdings: List<Holding>) = emptyMap<Long, Double>()
        }
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, marketIndexRemote, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore, housePriceRepo)
        manager.nowProvider = { nowMs }

        val ok = manager.sync()

        assertTrue(ok)
        // 估算值写回 estimatedValue
        assertEquals(1_010_000.0, holdingRepo.getHolding(id)!!.estimatedValue!!, 0.01)
        // 批量刷新了北京（去重 + 70 城校验）
        assertEquals(listOf("北京"), housePriceRepo.refreshCalledWith)
        // 净值快照反映估算值：101 万
        assertEquals(1, recorded.size)
        assertEquals(1_010_000.0, recorded[0], 0.01)
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
            override suspend fun fetchPrices(holdings: List<Holding>) = emptyMap<Long, Double>()
        }
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, marketIndexRemote, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore, housePriceRepo)
        manager.nowProvider = { msOf(2026, 7) }

        assertTrue(manager.sync())
        assertNull(housePriceRepo.refreshCalledWith) // 无开启估算的房产 → 不刷新指数
        assertNull(holdingRepo.getHolding(id)!!.estimatedValue)
        assertEquals(2_000_000.0, recorded[0], 0.01)
    }

    private fun msOf(year: Int, month: Int): Long {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        cal.clear()
        cal.set(year, month - 1, 1, 0, 0, 0)
        return cal.timeInMillis
    }
}
