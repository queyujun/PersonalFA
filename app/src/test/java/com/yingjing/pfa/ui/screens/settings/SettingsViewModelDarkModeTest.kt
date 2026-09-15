package com.yingjing.pfa.ui.screens.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.core.security.DatabaseKeyProvider
import com.yingjing.pfa.data.backup.AutoBackupStore
import com.yingjing.pfa.data.backup.BackupManager
import com.yingjing.pfa.data.backup.BackupScheduler
import com.yingjing.pfa.data.local.AppDatabase
import com.yingjing.pfa.data.sync.SyncScheduler
import com.yingjing.pfa.data.sync.SyncStateStore
import com.yingjing.pfa.domain.model.CategoryPoint
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.NetWorthPoint
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.SnapshotRepository
import com.yingjing.pfa.fakes.FakeAiSettingsStore
import com.yingjing.pfa.fakes.FakeLanguageStore
import com.yingjing.pfa.fakes.FakeSessionManager
import com.yingjing.pfa.fakes.FakeThemeStore
import com.yingjing.pfa.fakes.FakeUserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SettingsViewModel 深浅模式三档逻辑测试：档位读取、强制浅色/深色写入、
 * 切回 null = 跟随系统（清键）与状态提示。
 *
 * 依赖按项目 Fakes-over-Mocks 约定组装：轻依赖用内存 Fake，
 * SyncStateStore/BackupManager 等重依赖用 Robolectric 真 DataStore + 内存 Room；
 * 本测试只关心 darkMode 流转，导出/恢复/调度路径不会被触发。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsViewModelDarkModeTest {

    private lateinit var themeStore: FakeThemeStore
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
        Dispatchers.resetMain()
    }

    /** 组装完整 SettingsViewModel（仅为满足构造的重依赖用内存实现）。 */
    private fun buildViewModel(): SettingsViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val syncStateStore = SyncStateStore(context)
        val backupManager = BackupManager(
            db.userDao(), db.holdingDao(), db.netWorthSnapshotDao(),
            db.categorySnapshotDao(), db.alertDao(), db.subscriptionDao(),
            db.aiReportRecordDao(), FakeAiSettingsStore(), FakeFxRepo(),
        )
        return SettingsViewModel(
            context = context,
            sessionManager = FakeSessionManager(),
            userRepository = FakeUserRepository(),
            syncScheduler = SyncScheduler(context),
            syncStateStore = syncStateStore,
            backupManager = backupManager,
            backupScheduler = BackupScheduler(context),
            autoBackupStore = AutoBackupStore(
                // 固定口令替身绕开 Robolectric 缺失的 Android Keystore（本测试不触发备份读写）
                context, FixedKeyProvider(context), backupManager, syncStateStore,
            ),
            snapshotRepository = FakeSnapshotRepository(),
            fxRepository = FakeFxRepo(),
            languageStore = FakeLanguageStore(),
            themeStore = themeStore,
            aiSettingsStore = FakeAiSettingsStore(),
        )
    }

    @Test
    fun init_darkModeNull_showsFollowSystem() = runTest {
        themeStore = FakeThemeStore(initialDarkMode = null)
        val viewModel = buildViewModel()
        // 未设置档位 → 状态为 null（设置页副标题显示「跟随系统」）
        assertNull(viewModel.uiState.value.currentDarkMode)
    }

    @Test
    fun init_darkModePersisted_reflectedInState() = runTest {
        themeStore = FakeThemeStore(initialDarkMode = DARK_MODE_DARK)
        val viewModel = buildViewModel()
        assertEquals(DARK_MODE_DARK, viewModel.uiState.value.currentDarkMode)
    }

    @Test
    fun setDarkMode_persistsAndUpdatesState() = runTest {
        themeStore = FakeThemeStore(initialDarkMode = null)
        val viewModel = buildViewModel()

        viewModel.setDarkMode(DARK_MODE_LIGHT)
        assertEquals(DARK_MODE_LIGHT, themeStore.storedDarkMode)
        assertEquals(DARK_MODE_LIGHT, viewModel.uiState.value.currentDarkMode)
        assertNotNull(viewModel.uiState.value.statusMessage)

        // 切回跟随系统：null 清键（回退系统深浅）
        viewModel.setDarkMode(null)
        assertNull(themeStore.storedDarkMode)
        assertNull(viewModel.uiState.value.currentDarkMode)
    }
}

/** 固定口令的 DatabaseKeyProvider 替身：绕开 Robolectric 缺失的 Android Keystore。 */
private class FixedKeyProvider(context: Context) : DatabaseKeyProvider(context) {
    override fun getOrCreatePassphrase(): ByteArray = ByteArray(32) { (it + 1).toByte() }
}

/** 空快照仓库：本测试不触发快照读写。 */
private class FakeSnapshotRepository : SnapshotRepository {
    override fun observe(userId: Long): Flow<List<NetWorthPoint>> = flowOf(emptyList())
    override fun observeCategories(userId: Long): Flow<List<CategoryPoint>> = flowOf(emptyList())

    override suspend fun record(
        userId: Long,
        currency: Currency,
        totalAssets: Double,
        totalLiabilities: Double,
        netWorth: Double,
        categoryAmounts: Map<String, Double>,
        nowMs: Long,
    ) = Unit

    override suspend fun deleteBefore(userId: Long, dayEpochDay: Long) = Unit
}

/** 内存汇率仓库（仿 BackupManagerTest.FakeFxRepo）：本测试不触发联网。 */
private class FakeFxRepo : FxRepository {
    override fun observeRates(): Flow<FxRates> = flowOf(FxRates())
    override suspend fun current(): FxRates = FxRates()
    override suspend fun refresh(): FxRates = FxRates()
    override suspend fun save(rates: FxRates) = Unit
}
