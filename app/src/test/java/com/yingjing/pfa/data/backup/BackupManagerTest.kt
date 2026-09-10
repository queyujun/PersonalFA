package com.yingjing.pfa.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.data.ai.AiApiProtocol
import com.yingjing.pfa.data.ai.AiProfile
import com.yingjing.pfa.data.ai.AiProviderPreset
import com.yingjing.pfa.data.local.AiReportRecordEntity
import com.yingjing.pfa.data.local.AppDatabase
import com.yingjing.pfa.data.local.HoldingEntity
import com.yingjing.pfa.data.local.UserEntity
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.fakes.FakeAiSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    private lateinit var aiSettingsStore: FakeAiSettingsStore
    private lateinit var manager: BackupManager

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        fx = FakeFxRepo()
        aiSettingsStore = FakeAiSettingsStore()
        manager = BackupManager(
            db.userDao(), db.holdingDao(), db.netWorthSnapshotDao(),
            db.categorySnapshotDao(), db.alertDao(), db.subscriptionDao(),
            db.aiReportRecordDao(), aiSettingsStore, fx,
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

    @Test
    fun export_thenImport_aiRecordsAndProfilesRoundTrip() = runTest {
        db.userDao().insert(UserEntity(id = 1, username = "alex", passwordHash = "h", defaultCurrency = "CNY", createdAt = 1))
        db.aiReportRecordDao().insertAll(
            listOf(
                AiReportRecordEntity(
                    id = 0, userId = 1, kind = AiReportRecordEntity.KIND_REPORT,
                    title = "报告 2026-09-04", model = "deepseek-chat",
                    markdown = "## Executive Summary\n净值 **42.3%** 增长", createdAt = 1_700_000_000_000L,
                ),
                AiReportRecordEntity(
                    id = 0, userId = 1, kind = AiReportRecordEntity.KIND_INSIGHT,
                    title = "分析 2026-09-04", model = null,
                    markdown = "## Overall Assessment\n**优**", createdAt = 1_700_100_000_000L,
                ),
            ),
        )
        val customId = AiProfile.newCustomId()
        aiSettingsStore.seed(
            AiProfile(
                id = customId, name = "我的代理", providerId = AiProviderPreset.CUSTOM.id,
                baseUrl = "https://my-proxy.example.com/v1", model = "my-model",
                protocol = AiApiProtocol.RESPONSES, includeDetails = false,
            ),
        )
        aiSettingsStore.seed(
            AiProfile(
                id = AiProfile.presetIdOf(AiProviderPreset.DEEPSEEK),
                providerId = AiProviderPreset.DEEPSEEK.id,
                baseUrl = "https://api.deepseek.com/v1", model = "deepseek-chat",
            ),
            isActive = true,
        )
        aiSettingsStore.setConsented(true)
        // API Key 只存在本机密钥层，绝不进备份——导出前后不动它。
        aiSettingsStore.setApiKey(customId, "sk-custom-local")
        aiSettingsStore.setApiKey(AiProfile.presetIdOf(AiProviderPreset.DEEPSEEK), "sk-deepseek-local")

        val blob = manager.export("pw123".toCharArray())
        db.aiReportRecordDao().deleteAll()
        // 模拟换机：配置被重置。
        val freshStore = FakeAiSettingsStore()
        val freshManager = BackupManager(
            db.userDao(), db.holdingDao(), db.netWorthSnapshotDao(),
            db.categorySnapshotDao(), db.alertDao(), db.subscriptionDao(),
            db.aiReportRecordDao(), freshStore, fx,
        )
        assertEquals(0, db.aiReportRecordDao().getAllForBackup().size)
        // 备份明文中不含 API Key（key 只在本机密钥层，绝不进备份文件）
        val backupPlain = String(
            requireNotNull(com.yingjing.pfa.core.backup.BackupCrypto.decrypt(blob, "pw123".toCharArray())),
            Charsets.UTF_8,
        )
        assertFalse(backupPlain.contains("sk-custom-local"))
        assertFalse(backupPlain.contains("sk-deepseek-local"))

        assertTrue(freshManager.import(blob, "pw123".toCharArray()))
        val records = db.aiReportRecordDao().getAllForBackup()
        assertEquals(2, records.size)
        val report = records.first { it.kind == AiReportRecordEntity.KIND_REPORT }
        assertEquals("报告 2026-09-04", report.title)
        assertEquals("deepseek-chat", report.model)
        assertTrue(report.markdown.contains("**42.3%**"))
        val insight = records.first { it.kind == AiReportRecordEntity.KIND_INSIGHT }
        assertNull(insight.model)

        // 档案列表 + 生效 id + 全局同意整体还原。
        val restored = freshStore.profiles.first()
        assertEquals(2, restored.size)
        val restoredCustom = restored.first { it.id == customId }
        assertEquals("我的代理", restoredCustom.name)
        assertEquals("https://my-proxy.example.com/v1", restoredCustom.baseUrl)
        assertEquals(AiApiProtocol.RESPONSES, restoredCustom.protocol)
        assertFalse(restoredCustom.includeDetails)
        assertEquals(
            AiProfile.presetIdOf(AiProviderPreset.DEEPSEEK),
            freshStore.activeProfileId.first(),
        )
        assertTrue(freshStore.consented.first())
        assertTrue(freshStore.keys.isEmpty()) // 换机 fake 无 key → 须重录
    }

    @Test
    fun import_legacyBackupWithSingleAiSettings_mapsToPresetProfile() = runTest {
        // 老备份（仅 aiSettings，无 aiProfiles）：映射为对应预设档案并设为生效。
        val legacyJson = """
            {"version":1,"users":[],"holdings":[],"snapshots":[],"categorySnapshots":[],"alerts":[],
             "aiSettings":{"providerId":"hunyuan","baseUrl":"https://api.hunyuan.cloud.tencent.com/v1",
                           "model":"hunyuan-turbos-latest","protocol":"responses",
                           "includeDetails":false,"consented":true}}
        """.trimIndent()
        val blob = com.yingjing.pfa.core.backup.BackupCrypto.encrypt(
            legacyJson.toByteArray(Charsets.UTF_8), "pw123".toCharArray(),
        )
        assertTrue(manager.import(blob, "pw123".toCharArray()))

        val profiles = aiSettingsStore.profiles.first()
        val hunyuan = profiles.first { it.id == AiProfile.presetIdOf(AiProviderPreset.HUNYUAN) }
        assertEquals("hunyuan-turbos-latest", hunyuan.model)
        assertEquals(AiApiProtocol.RESPONSES, hunyuan.protocol)
        assertFalse(hunyuan.includeDetails)
        assertEquals(hunyuan.id, aiSettingsStore.activeProfileId.first())
        assertTrue(aiSettingsStore.consented.first())
    }

    @Test
    fun import_legacyBackupWithoutAiData_keepsLocalSettingsAndEmptyRecords() = runTest {
        // 老备份（无 aiRecords/aiSettings/aiProfiles 字段）→ 不崩溃、不清本机 AI 配置、记录表为空。
        aiSettingsStore.seed(
            AiProfile(
                id = AiProfile.presetIdOf(AiProviderPreset.OPENAI),
                providerId = AiProviderPreset.OPENAI.id,
                baseUrl = "https://api.openai.com/v1", model = "gpt-4o-mini",
            ),
            isActive = true,
        )
        val legacyJson = """
            {"version":1,"users":[],"holdings":[],"snapshots":[],"categorySnapshots":[],"alerts":[]}
        """.trimIndent()
        val blob = com.yingjing.pfa.core.backup.BackupCrypto.encrypt(
            legacyJson.toByteArray(Charsets.UTF_8), "pw123".toCharArray(),
        )
        assertTrue(manager.import(blob, "pw123".toCharArray()))
        assertEquals(0, db.aiReportRecordDao().getAllForBackup().size)
        val local = aiSettingsStore.profiles.first()
        assertEquals(1, local.size) // 未被覆盖
        assertEquals(AiProfile.presetIdOf(AiProviderPreset.OPENAI), aiSettingsStore.activeProfileId.first())
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
