package com.yingjing.pfa.ui.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.core.security.AppLockManager
import com.yingjing.pfa.data.sync.SyncStateStore
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.fakes.FakeSessionManager
import com.yingjing.pfa.fakes.FakeStringResolver
import com.yingjing.pfa.fakes.FakeUserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AppLockViewModel 解锁逻辑测试（同 [AuthViewModelTest] 模式：Robolectric + 真实 [SyncStateStore]
 * + Fake 会话 / 仓库 / 文案解析）。
 *
 * 密码解锁复用 [com.yingjing.pfa.domain.repository.UserRepository.login]（内部 PBKDF2 校验），
 * 成功 → [AppLockManager.unlock]；失败 → 错误文案且保持锁定。
 *
 * 时序注意：[AppLockManager] 在 `currentUserId` 非 null 排放**之前**构造时，init collector
 * 会先读到 null 排放而把 `isLocked` 重置为 false，后续非 null 排放不重新锁（设计规则：登录不锁）。
 * 故单测用 [AppLockManager.lockForTesting] 显式置锁，再断言解锁/保持锁定逻辑。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AppLockViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var appLockManager: AppLockManager
    private lateinit var repository: FakeUserRepository
    private lateinit var session: FakeSessionManager
    private lateinit var viewModel: AppLockViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = FakeUserRepository()
        session = FakeSessionManager()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val syncStateStore = SyncStateStore(ctx)
        runBlocking { syncStateStore.setAppLock(true) }
        appLockManager = AppLockManager(session, syncStateStore, CoroutineScope(dispatcher))
        viewModel = AppLockViewModel(
            context = ctx,
            appLockManager = appLockManager,
            sessionManager = session,
            userRepository = repository,
            syncStateStore = syncStateStore,
            stringResolver = FakeStringResolver(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun unlockWithPassword_correct_unlocks() = runTest(dispatcher.scheduler) {
        repository.register("alex", "password1", Currency.CNY)
        session.setCurrentUser(1)
        advanceUntilIdle()
        // 进入锁定态（绕开 collector 时序）
        appLockManager.lockForTesting()
        assertTrue(appLockManager.isLocked.value)

        viewModel.onPasswordChange("password1")
        viewModel.unlockWithPassword()
        advanceUntilIdle()

        assertFalse(appLockManager.isLocked.value)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun unlockWithPassword_wrong_keepsLocked() = runTest(dispatcher.scheduler) {
        repository.register("alex", "password1", Currency.CNY)
        session.setCurrentUser(1)
        advanceUntilIdle()
        appLockManager.lockForTesting()

        viewModel.onPasswordChange("wrongpass")
        viewModel.unlockWithPassword()
        advanceUntilIdle()

        assertTrue(appLockManager.isLocked.value)
        // FakeStringResolver 把 lock_wrong_password 渲染为非空标识；此处只验错误键被触发，不绑 locale。
        assertNotNull(viewModel.uiState.value.error)
    }
}
