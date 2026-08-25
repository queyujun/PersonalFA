package com.yingjing.pfa.ui.navigation

import androidx.lifecycle.ViewModel
import com.yingjing.pfa.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 根界面轻量 ViewModel：提供顶栏「立即刷新行情」入口。 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val syncScheduler: SyncScheduler,
) : ViewModel() {
    fun refreshQuotesNow() = syncScheduler.refreshNow()
}
