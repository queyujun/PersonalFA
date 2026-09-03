package com.yingjing.pfa.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.data.local.AppDatabase
import com.yingjing.pfa.data.local.HoldingEntity
import com.yingjing.pfa.data.local.UserEntity
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.repository.FxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {

    private lateinit var db: AppDatabase
    private lateinit var fx: FakeFxRepo
    private lateinit var manager: BackupManager

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        fx = FakeFxRepo()
        manager = BackupManager(
            db.userDao(), db.holdingDao(), db.netWorthSnapshotDao(),
            db.categorySnapshotDao(), db.alertDao(), db.subscriptionDao(), fx,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun holding(id: Long, userId: Long, name: String) = HoldingEntity(
        id = id, userId = userId, type = "A_SHARE", name = name, currency = "CNY",
        quantity = 100.0, costPrice = 1650.0, currentPrice = 1680.0, symbol = "600519",
        manualValue = null, city = null, areaSqm = null, annualRatePercent = null,
        startDateEpochMs = null, maturityDateEpochMs = null, depositType = null, sharePercent = null,
        liabilityType = null, monthlyPayment = null, createdAt = 1L, updatedAt = 1L,
    )

    @Test
    fun export_thenWipe_thenImport_restoresData() = runTest {
        db.userDao().insert(UserEntity(id = 1, username = "alex", passwordHash = "h", defaultCurrency = "CNY", createdAt = 1))
        db.holdingDao().insertAll(listOf(holding(10, 1, "茅台"), holding(11, 1, "宁德")))

        val blob = manager.export("pw123".toCharArray())

        // 清空
        db.holdingDao().deleteAll()
        db.userDao().deleteAll()
        assertEquals(0, db.userDao().count())

        val ok = manager.import(blob, "pw123".toCharArray())
        assertTrue(ok)
        assertEquals(1, db.userDao().count())
        assertEquals("alex", db.userDao().findById(1)!!.username)
        assertEquals(2, db.holdingDao().getByUser(1).size)
        assertEquals("茅台", db.holdingDao().findById(10)!!.name)
    }

    @Test
    fun import_wrongPassphrase_returnsFalse_andKeepsData() = runTest {
        db.userDao().insert(UserEntity(id = 1, username = "alex", passwordHash = "h", defaultCurrency = "CNY", createdAt = 1))
        val blob = manager.export("right".toCharArray())
        val ok = manager.import(blob, "wrong".toCharArray())
        assertFalse(ok)
        assertEquals(1, db.userDao().count()) // 未被清空
    }

    @Test
    fun export_includesFxRates_andImportWritesThemBack() = runTest {
        // 导出时刻汇率写入备份；恢复后写回 fake 的 save。
        fx.currentRates = FxRates(usdToCny = 7.1, hkdToCny = 0.92)
        db.userDao().insert(UserEntity(id = 1, username = "alex", passwordHash = "h", defaultCurrency = "CNY", createdAt = 1))

        val blob = manager.export("pw123".toCharArray())

        // 模拟另一台设备：导入前本机从未有汇率（save 尚未被调用）。
        assertNull(fx.savedRates)

        assertTrue(manager.import(blob, "pw123".toCharArray()))
        assertEquals(FxRates(7.1, 0.92), fx.savedRates)
    }

    @Test
    fun import_legacyBackupWithoutFxRates_doesNotWriteRates() = runTest {
        // 老备份 JSON 不含 fxRates 字段 → ignoreUnknownKeys/可选字段 → null → 不写汇率（向后兼容）。
        val legacyJson = """
            {"version":1,"users":[],"holdings":[],"snapshots":[],"categorySnapshots":[],"alerts":[]}
        """.trimIndent()
        val blob = com.yingjing.pfa.core.backup.BackupCrypto.encrypt(
            legacyJson.toByteArray(Charsets.UTF_8), "pw123".toCharArray(),
        )
        assertTrue(manager.import(blob, "pw123".toCharArray()))
        assertNull(fx.savedRates) // 未写汇率
    }

    @Test
    fun export_thenImport_subscriptionsRoundTrip() = runTest {
        db.userDao().insert(UserEntity(id = 1, username = "alex", passwordHash = "h", defaultCurrency = "CNY", createdAt = 1))
        db.subscriptionDao().insertAll(
            listOf(
                com.yingjing.pfa.data.local.SubscriptionEntity(
                    id = 5, userId = 1, name = "视频会员", category = "VIDEO", note = null,
                    currency = "CNY", amount = 30.0, cycle = "MONTHLY",
                    firstBillEpochMs = 1_700_000_000_000L, nextRenewalEpochMs = 1_700_100_000_000L,
                    reminderDaysBefore = 3, paymentMethod = "支付宝", active = true,
                    createdAt = 1L, updatedAt = 1L,
                ),
                com.yingjing.pfa.data.local.SubscriptionEntity(
                    id = 6, userId = 1, name = "云存储", category = "CLOUD", note = "家庭版",
                    currency = "USD", amount = 9.9, cycle = "YEARLY",
                    firstBillEpochMs = 1_700_200_000_000L, nextRenewalEpochMs = 1_700_300_000_000L,
                    reminderDaysBefore = 7, paymentMethod = null, active = false,
                    createdAt = 2L, updatedAt = 2L,
                ),
            ),
        )

        val blob = manager.export("pw123".toCharArray())
        db.subscriptionDao().deleteAll()
        assertEquals(0, db.subscriptionDao().getByUser(1).size)

        assertTrue(manager.import(blob, "pw123".toCharArray()))
        val restored = db.subscriptionDao().getByUser(1)
        assertEquals(2, restored.size)
        val video = restored.first { it.id == 5L }
        assertEquals("视频会员", video.name)
        assertEquals("MONTHLY", video.cycle)
        assertEquals(1_700_100_000_000L, video.nextRenewalEpochMs)
        assertEquals("支付宝", video.paymentMethod)
        val cloud = restored.first { it.id == 6L }
        assertEquals("USD", cloud.currency)
        assertFalse(cloud.active) // 停用状态一并保留
        assertEquals("家庭版", cloud.note)
    }

    @Test
    fun import_legacyBackupWithoutSubscriptions_restoresEmpty() = runTest {
        // 老备份（无 subscriptions 字段）导入后订阅表为空 → 向后兼容不崩溃。
        val legacyJson = """
            {"version":1,"users":[],"holdings":[],"snapshots":[],"categorySnapshots":[],"alerts":[]}
        """.trimIndent()
        val blob = com.yingjing.pfa.core.backup.BackupCrypto.encrypt(
            legacyJson.toByteArray(Charsets.UTF_8), "pw123".toCharArray(),
        )
        assertTrue(manager.import(blob, "pw123".toCharArray()))
        assertEquals(0, db.subscriptionDao().getAllForBackup().size)
    }

    /** 内存 fake FxRepository：current 返回预设值，save 记录之。 */
    private class FakeFxRepo : FxRepository {
        var currentRates: FxRates = FxRates()
        var savedRates: FxRates? = null

        override fun observeRates(): Flow<FxRates> = flowOf(currentRates)
        override suspend fun current(): FxRates = currentRates
        override suspend fun refresh(): FxRates = currentRates
        override suspend fun save(rates: FxRates) { savedRates = rates }
    }
}
