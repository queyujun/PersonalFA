package com.yingjing.pfa.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.data.session.SessionManager
import com.yingjing.pfa.domain.model.CategoryPoint
import com.yingjing.pfa.domain.model.NetWorthPoint
import com.yingjing.pfa.domain.repository.SnapshotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TrendRaw(
    val totals: List<NetWorthPoint> = emptyList(),
    val categories: List<CategoryPoint> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrendDetailViewModel @Inject constructor(
    sessionManager: SessionManager,
    snapshotRepository: SnapshotRepository,
) : ViewModel() {

    val raw: StateFlow<TrendRaw> = sessionManager.currentUserId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(TrendRaw())
            } else {
                combine(
                    snapshotRepository.observe(id),
                    snapshotRepository.observeCategories(id),
                ) { totals, categories -> TrendRaw(totals, categories) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendRaw())
}
