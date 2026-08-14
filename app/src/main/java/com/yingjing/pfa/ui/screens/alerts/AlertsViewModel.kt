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
}
