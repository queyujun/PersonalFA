package com.yingjing.pfa.core.security

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.data.sync.SyncStateStore
import com.yingjing.pfa.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用锁：进程级单一真相源，持有 [isLocked] 状态。
 *
 * 观察整个应用进程的前后台切换（[ProcessLifecycleOwner] 在 MainActivity 注册），
 * 从后台切回前台（ON_STOP 触发）时要求重新认证。
 *
 * 锁定规则（闭合无竞态）：
 * - **初始值 = true**：冷启动默认锁。未登录用户不受影响——AppRoot 仅在 LoggedIn 时挂遮罩，
 *   且下方 collect `currentUserId` 发出 null 时会把 [isLocked] 重置为 false。
 * - **ON_STOP**：仅当「已登录且 `appLockEnabled`」→ true。每次切回都锁。
 * - **collect `currentUserId`**：变 null（登出 / 未登录）→ false（重置，避免下次登录即锁）。
 *   非 null 时**不主动设 true**（登录不触发锁；只有冷启动初始 true 与 ON_STOP 才锁）。
 * - [unlock] / [unlockByBiometric]：→ false。
 */
@Singleton
class AppLockManager @Inject constructor(
    private val sessionManager: SessionManager,
    private val syncStateStore: SyncStateStore,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    // 缓存最新值供同步的 onStop 回调读取（生命周期回调不可 suspend）。
    private var currentUserId: Long? = null
    private var appLockEnabled: Boolean = true

    init {
        scope.launch {
            sessionManager.currentUserId.collect { id ->
                currentUserId = id
                // 登出 / 未登录：重置锁定，避免下次登录即被锁。
                if (id == null) _isLocked.value = false
            }
        }
        scope.launch {
            syncStateStore.appLockEnabled.collect { enabled ->
                appLockEnabled = enabled
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        // 仅当已登录且启用应用锁时锁定；每次切回都锁。
        if (currentUserId != null && appLockEnabled) {
            _isLocked.value = true
        }
    }

    /** 密码校验通过后调用。 */
    fun unlock() {
        _isLocked.value = false
    }

    /** 指纹 / 面容成功后调用（设备指纹=已登录身份凭证，不重新校验密码）。 */
    fun unlockByBiometric() {
        _isLocked.value = false
    }

    /**
     * 仅测试用：在已登录前提下显式置为锁定状态。
     *
     * 生产路径下锁定只由冷启动初始值（true）与 ON_STOP 触发——登录本身不锁。
     * 单测验证解锁逻辑时，若本管理器在 currentUserId 非 null 排放**之前**构造，init collector
     * 会先读到 null 排放而把 `_isLocked` 重置为 false，无法进入锁定态。此入口绕开该时序，直接置锁。
     */
    @VisibleForTesting
    internal fun lockForTesting() {
        _isLocked.value = true
    }

    /**
     * 仅测试用：显式覆盖 [appLockEnabled] 缓存。
     *
     * 生产路径下该缓存由 collect [SyncStateStore.appLockEnabled] 异步更新；单测在
     * [StandardTestDispatcher] 下无法可靠驱动 DataStore 的异步排放（主 looper 未 pump，
     * Robolectric 会以 "Main looper has queued unexecuted runnables" 告警），故提供此入口
     * 以确定性验证 [onStop] 的「关闭应用锁时不锁」分支。
     */
    @VisibleForTesting
    internal fun setAppLockEnabledForTesting(enabled: Boolean) {
        appLockEnabled = enabled
    }
}
