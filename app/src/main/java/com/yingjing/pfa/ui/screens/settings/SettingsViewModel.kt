package com.yingjing.pfa.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.data.sync.SyncScheduler
import com.yingjing.pfa.data.sync.SyncStateStore
import com.yingjing.pfa.domain.model.User
import com.yingjing.pfa.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val currentUser: User? = null,
    val users: List<User> = emptyList(),
    val lastSyncMs: Long? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val syncScheduler: SyncScheduler,
    private val syncStateStore: SyncStateStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            syncStateStore.lastSync.collect { ms ->
                _uiState.value = _uiState.value.copy(lastSyncMs = ms)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val currentId = sessionManager.currentUserId.first()
            val users = userRepository.listUsers()
            _uiState.value = _uiState.value.copy(
                currentUser = currentId?.let { id -> users.firstOrNull { it.id == id } },
                users = users,
            )
        }
    }

    fun refreshQuotesNow() {
        syncScheduler.refreshNow()
    }

    fun logout() {
        viewModelScope.launch { sessionManager.clear() }
    }

    fun switchUser(userId: Long) {
        viewModelScope.launch {
            sessionManager.setCurrentUser(userId)
            refresh()
        }
    }

    fun deleteUser(userId: Long) {
        viewModelScope.launch {
            userRepository.deleteUser(userId)
            if (sessionManager.currentUserId.first() == userId) sessionManager.clear()
            refresh()
        }
    }
}
