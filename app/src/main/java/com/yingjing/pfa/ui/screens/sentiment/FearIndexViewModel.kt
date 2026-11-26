package com.yingjing.pfa.ui.screens.sentiment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.domain.model.FearIndexId
import com.yingjing.pfa.domain.repository.MarketSentimentRepository
import com.yingjing.pfa.domain.repository.SentimentRefreshResult
import com.yingjing.pfa.domain.usecase.FearIndexStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * 恐慌指数总览页与总览入口卡共用的 ViewModel。
 *
 * 只订阅近 [LOOKBACK_DAYS] 天的数据（足够算近 1 年分位），进入页面时按新鲜度窗口自动刷新；
 * 手动刷新的结果经 [refreshEvents] 一次性上报（Snackbar）。
 */
@HiltViewModel
class FearIndexViewModel @Inject constructor(
    private val repository: MarketSentimentRepository,
) : ViewModel() {

    private val fromDay: Long = LocalDate.now(ZoneOffset.UTC).toEpochDay() - LOOKBACK_DAYS

    val uiState: StateFlow<FearOverviewUi> = combine(
        repository.observeSeries(FearIndexId.entries.map { it.code }, fromDay),
        repository.syncState,
    ) { series, sync ->
        FearOverviewBuilder.build(series, sync.failedCodes, sync.refreshing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FearOverviewUi())

    private val _refreshEvents = MutableSharedFlow<SentimentRefreshResult>(extraBufferCapacity = 1)
    val refreshEvents: SharedFlow<SentimentRefreshResult> = _refreshEvents.asSharedFlow()

    /** 进入页面：按新鲜度窗口决定是否真正抓取（静默，不发事件）。 */
    fun refreshIfStale() {
        viewModelScope.launch { repository.refresh(force = false) }
    }

    /** 用户手动刷新：强制抓取并上报结果。 */
    fun refreshNow() {
        viewModelScope.launch { _refreshEvents.emit(repository.refresh(force = true)) }
    }

    companion object {
        /** 近 1 年窗口 + 2 周余量（锚点为序列最新日，可能早于今天数日）。 */
        const val LOOKBACK_DAYS: Long = FearIndexStats.ONE_YEAR_DAYS + 14
    }
}
