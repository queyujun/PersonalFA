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
import com.yingjing.pfa.domain.repository.AlertRepository
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.QuoteRepository
import com.yingjing.pfa.domain.repository.SnapshotRepository
import com.yingjing.pfa.domain.repository.UserRepository
import com.yingjing.pfa.fakes.FakeHoldingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncManagerTest {

    private val holdingRepo = FakeHoldingRepository()
    private lateinit var syncStateStore: SyncStateStore

    private val userRepo = object : UserRepository {
        override suspend fun register(username: String, password: String, defaultCurrency: Currency) =
            RegisterResult.UsernameTaken
        override suspend fun login(username: String, password: String) =
            com.yingjing.pfa.domain.auth.LoginResult.InvalidCredentials
        override suspend fun listUsers() = listOf(User(1, "alex", Currency.CNY, 0))
        override suspend fun getUser(id: Long) = null
        override suspend fun updateDefaultCurrency(userId: Long, currency: Currency) {}
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
        override suspend fun record(
            userId: Long,
            currency: Currency,
            totalAssets: Double,
            totalLiabilities: Double,
            netWorth: Double,
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

    private val marketIndexRemote = com.yingjing.pfa.data.remote.MarketIndexRemote { emptyMap() }
    private val ipoRemote = com.yingjing.pfa.data.remote.IpoRemote { emptyList() }

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
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, marketIndexRemote, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore)

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
        val manager = SyncManager(userRepo, holdingRepo, quoteRepo, fxRepo, marketIndexRemote, ipoRemote, snapshotRepo, alertRepo, notifier, syncStateStore)
        // 加一个持仓触发抓取路径
        holdingRepo.addHolding(
            Holding(userId = 1, type = AssetType.A_SHARE, name = "茅台", currency = Currency.CNY, symbol = "600519", quantity = 1.0),
        )
        assertEquals(false, manager.sync())
    }
}
