package com.yingjing.pfa.fakes

import com.yingjing.pfa.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 内存版会话，用于 ViewModel 测试。 */
class FakeSessionManager : SessionManager {
    private val _currentUserId = MutableStateFlow<Long?>(null)
    private val _lastUserId = MutableStateFlow<Long?>(null)

    override val currentUserId: StateFlow<Long?> = _currentUserId.asStateFlow()
    override val lastUserId: StateFlow<Long?> = _lastUserId.asStateFlow()

    override suspend fun setCurrentUser(userId: Long) {
        _currentUserId.value = userId
        _lastUserId.value = userId
    }

    override suspend fun clear() {
        _currentUserId.value = null
    }

    val current: Long? get() = _currentUserId.value
}
