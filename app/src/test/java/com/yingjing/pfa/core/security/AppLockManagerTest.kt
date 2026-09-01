package com.yingjing.pfa.core.security

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.data.sync.SyncStateStore
import com.yingjing.pfa.fakes.FakeSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AppLockManager 锁定规则集测试。每个测试用独立的 [StandardTestDispatcher] + [CoroutineScope]
 * 驱动 init collectors（@Singleton 无 viewModelScope，需注入作用域）。
 *
 * 会话用 [FakeSessionManager]（确定性 StateFlow，立即发射当前值）；
 * 应用锁开关用真实 [SyncStateStore]（同 [com.yingjing.pfa.data.sync.SyncManagerTest] 模式）。
 *
 * 注意：`dispatcher` / `scope` 用类型推断声明（`val dispatcher = StandardTestDispatcher()`），
 * 与 [com.yingjing.pfa.ui.auth.AuthViewModelTest] 一致——避免显式 `StandardTestDispatcher` 类型
 * 标注在测试源集下的解析问题。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AppLockManagerTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(dispatcher)
    private lateinit var session: FakeSessionManager
    private lateinit var syncStateStore: SyncStateStore

    @Before
    fun setup() {
        session = FakeSessionManager()
        syncStateStore = SyncStateStore(ApplicationProvider.getApplicationContext<Context>())
        // 隔离：应用锁默认启用，避免上一个测试关闭后污染本测试的 DataStore 持久态。
        runBlocking { syncStateStore.setAppLock(true) }
    }

    private fun newManager() = AppLockManager(session, syncStateStore, scope)

    /** onStop 忽略 owner 实参（[AppLockManager.onStop] 仅读缓存），这里仅提供满足类型的占位实例。 */
    private val noopOwner = object : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry(this)
    }

    @Test
    fun coldStart_loggedOut_isLockedFalse() = runTest(dispatcher.scheduler) {
        val manager = newManager()
        advanceUntilIdle()
        // 未登录：collect currentUserId==null → 重置为 false
        assertFalse(manager.isLocked.value)
    }

    @Test
    fun coldStart_loggedIn_isLockedTrue() = runTest(dispatcher.scheduler) {
        session.setCurrentUser(1)
        val manager = newManager()
        advanceUntilIdle()
        // 已登录且未解锁：初始 true，非 null 不重置
        assertTrue(manager.isLocked.value)
    }

    @Test
    fun onStop_loggedInWithAppLock_setsLocked() = runTest(dispatcher.scheduler) {
        session.setCurrentUser(1)
        val manager = newManager()
        advanceUntilIdle()
        manager.unlock()
        assertFalse(manager.isLocked.value)
        manager.onStop(noopOwner)
        // 已登录且启用应用锁 → onStop 触发锁定
        assertTrue(manager.isLocked.value)
    }

    @Test
    fun onStop_appLockDisabled_notLocked() = runTest(dispatcher.scheduler) {
        session.setCurrentUser(1)
        val manager = newManager()
        advanceUntilIdle()
        manager.unlock()
        // 确定性覆盖 appLockEnabled 缓存（绕开 DataStore 异步在测试调度器下的不可靠性）。
        manager.setAppLockEnabledForTesting(false)
        manager.onStop(noopOwner)
        // 应用锁关闭 → onStop 不锁
        assertFalse(manager.isLocked.value)
    }

    @Test
    fun unlock_setsFalse() = runTest(dispatcher.scheduler) {
        session.setCurrentUser(1)
        val manager = newManager()
        advanceUntilIdle()
        assertTrue(manager.isLocked.value)
        manager.unlock()
        assertFalse(manager.isLocked.value)
    }

    @Test
    fun logout_resetsLock() = runTest(dispatcher.scheduler) {
        session.setCurrentUser(1)
        val manager = newManager()
        advanceUntilIdle()
        manager.onStop(noopOwner)
        assertTrue(manager.isLocked.value)
        session.clear()
        advanceUntilIdle()
        // 登出：currentUserId→null → 重置锁定为 false
        assertFalse(manager.isLocked.value)
    }
}
