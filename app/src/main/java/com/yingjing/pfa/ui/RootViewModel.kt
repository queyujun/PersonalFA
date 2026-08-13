package com.yingjing.pfa.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 顶层会话状态：加载中 / 未登录 / 已登录。 */
sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val userId: Long) : SessionState
}

@HiltViewModel
class RootViewModel @Inject constructor(
    sessionManager: SessionManager,
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = sessionManager.currentUserId
        .map { id -> if (id == null) SessionState.LoggedOut else SessionState.LoggedIn(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)
}
