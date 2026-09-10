package com.yingjing.pfa.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** 每日行情同步 Worker（由 WorkManager 调度）。 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        val result = syncManager.sync(manual)
        // 成功或部分失败都算本次同步完成：部分失败已记录到结果流供 UI 提示，
        // 不重试——避免海外源持续不可达导致任务无限 retry 浪费电量。
        // 仅整体异常（result.success==false 且全部源失败）时考虑重试。
        return if (result.success || result.failedSources.size < ALL_FAILED_THRESHOLD) Result.success() else Result.retry()
    }

    private companion object {
        const val KEY_MANUAL = "manual_refresh"
        // SyncSource 枚举总数：失败源达到全部即视为整体失败需重试。
        const val ALL_FAILED_THRESHOLD = 9
    }
}
