package com.yingjing.pfa.ui.screens.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.domain.model.Alert
import com.yingjing.pfa.domain.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val alertRepository: AlertRepository,
) : ViewModel() {

    val alerts: StateFlow<List<Alert>> = sessionManager.currentUserId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else alertRepository.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markRead(id: Long) {
        viewModelScope.launch { alertRepository.markRead(id) }
    }

    fun markAllRead() {
        viewModelScope.launch {
            sessionManager.currentUserId.first()?.let { alertRepository.markAllRead(it) }
        }
    }

    /** 滑动删除单条。 */
    fun delete(id: Long) {
        viewModelScope.launch {
            sessionManager.currentUserId.first()?.let { alertRepository.delete(it, id) }
        }
    }

    /** 批量删除所选（多选模式）。 */
    fun deleteSelected(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            sessionManager.currentUserId.first()?.let { alertRepository.delete(it, ids) }
        }
    }

    /** 全部删除（当前用户）。 */
    fun deleteAll() {
        viewModelScope.launch {
            sessionManager.currentUserId.first()?.let { alertRepository.deleteAll(it) }
        }
    }
}
