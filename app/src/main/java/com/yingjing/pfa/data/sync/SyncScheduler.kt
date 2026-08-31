package com.yingjing.pfa.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** 调度行情同步（周期天数 + 整点时间可配）与立即刷新。提醒随行情一并抓取。 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * 按周期(天)与整点(小时)注册行情同步。
     * [forceReplace]=false 保留已有调度（启动场景），=true 重排（配置变更场景）。
     * 定时同步标记为非手动（失败时静默记录、不强制弹窗）。
     */
    fun schedule(intervalDays: Int, hour: Int, forceReplace: Boolean) {
        val interval = Duration.ofDays(intervalDays.coerceAtLeast(1).toLong())
        val delay = ScheduleTime.initialDelayMillis(System.currentTimeMillis(), hour)
        val request = PeriodicWorkRequestBuilder<SyncWorker>(interval)
            .setConstraints(networkConstraint)
            .setInitialDelay(Duration.ofMillis(delay))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(10))
            .setInputData(workDataOf(KEY_MANUAL to false))
            .build()
        val policy =
            if (forceReplace) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP
        workManager.enqueueUniquePeriodicWork(DAILY_WORK, policy, request)
    }

    /** 用户手动「立即刷新」；标记 manual，使完成后弹窗提示结果。 */
    fun refreshNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setInputData(workDataOf(KEY_MANUAL to true))
            .build()
        workManager.enqueueUniqueWork(REFRESH_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val DAILY_WORK = "daily_sync"
        const val REFRESH_WORK = "refresh_now"
        const val KEY_MANUAL = "manual_refresh"
    }
}
