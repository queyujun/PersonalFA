package com.yingjing.pfa.ui.screens.sentiment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.domain.model.FearIndexId
import com.yingjing.pfa.domain.repository.MarketSentimentRepository
import com.yingjing.pfa.domain.repository.SentimentRefreshResult
import com.yingjing.pfa.domain.usecase.FearRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FearDetailUi(
    val id: FearIndexId? = null,
    val content: FearDetailContent? = null,
    val range: FearRange = FearRange.DEFAULT,
    val showOverlay: Boolean = true,
    val refreshing: Boolean = false,
    val stale: Boolean = false,
    val loaded: Boolean = false,
)

/**
 * 单个恐慌指数详情：区间切换（默认 1 年，可选 1 月–3 年 / 全部）+ 叠加序列开关。
 * 指数 code 来自导航参数 `id`；未知 code 时 [FearDetailUi.id] 为 null（页面显示空态）。
 */
@HiltViewModel
class FearIndexDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MarketSentimentRepository,
) : ViewModel() {

    private val id: FearIndexId? = FearIndexId.fromCode(savedStateHandle.get<String>(ARG_ID))

    private val range = MutableStateFlow(FearRange.DEFAULT)
    private val showOverlay = MutableStateFlow(true)

    private val _refreshEvents = MutableSharedFlow<SentimentRefreshResult>(extraBufferCapacity = 1)
    val refreshEvents: SharedFlow<SentimentRefreshResult> = _refreshEvents.asSharedFlow()

    val selectedRange: StateFlow<FearRange> = range.asStateFlow()

    val uiState: StateFlow<FearDetailUi> = if (id == null) {
        MutableStateFlow(FearDetailUi(loaded = true)).asStateFlow()
    } else {
        combine(
            repository.observeSeries(listOf(id.code, id.overlay.code)),
            repository.syncState,
            range,
            showOverlay,
        ) { series, sync, r, overlayOn ->
            val content = FearDetailBuilder.build(
                id = id,
                primary = series[id.code].orEmpty(),
                overlay = series[id.overlay.code].orEmpty(),
                range = r,
            )
            FearDetailUi(
                id = id,
                content = content,
                range = r,
                showOverlay = overlayOn,
                refreshing = sync.refreshing,
                stale = id.code in sync.failedCodes,
                loaded = true,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FearDetailUi(id = id))
    }

    fun selectRange(value: FearRange) {
        range.value = value
    }

    fun toggleOverlay() {
        showOverlay.value = !showOverlay.value
    }

    fun refreshIfStale() {
        viewModelScope.launch { repository.refresh(force = false) }
    }

    fun refreshNow() {
        viewModelScope.launch { _refreshEvents.emit(repository.refresh(force = true)) }
    }

    companion object {
        const val ARG_ID = "id"
    }
}
