package com.yingjing.pfa.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yingjing.pfa.data.sync.SyncScheduler
import com.yingjing.pfa.data.sync.SyncStateStore
import com.yingjing.pfa.domain.model.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 根界面轻量 ViewModel：顶栏「立即刷新行情」入口 + 同步完成结果上报（弹窗提示成功/失败）。 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val syncScheduler: SyncScheduler,
    private val syncStateStore: SyncStateStore,
) : ViewModel() {

    /**
     * 待展示的同步结果：存在「尚未确认」的结果时为该结果，否则为 null。
     *
     * 判定规则：
     * - 结果完成时刻晚于已确认时刻（dismiss 后写入 shownAt >= completedAt），才算「未见」。
     * - 手动触发：无论成败都弹窗（让用户知晓刷新结果）。
     * - 自动定时：仅在存在失败源时弹窗（全成功不打扰）。
     */
    val pendingResult: StateFlow<SyncResult?> = combine(
        syncStateStore.lastResult,
        syncStateStore.lastResultShownAt,
    ) { result, shownAt ->
        if (result == null) return@combine null
        if (shownAt != null && shownAt >= result.completedAt) return@combine null
        if (result.manual || result.hasFailure) result else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshQuotesNow() = syncScheduler.refreshNow()

    /** 用户已确认当前结果，标记不再重复弹窗。 */
    fun dismissResult() {
        viewModelScope.launch {
            pendingResult.value?.let { syncStateStore.setLastResultShownAt(it.completedAt) }
        }
    }
}
