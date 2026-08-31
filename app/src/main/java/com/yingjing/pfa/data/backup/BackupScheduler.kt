package com.yingjing.pfa.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yingjing.pfa.data.sync.ScheduleTime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** 自动备份调度（周期天数 + 整点时间可配）。 */
@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * 注册/取消自动备份。
     * [forceReplace]=false 保留已有调度（启动场景），=true 重排（配置变更场景）。
     */
    fun schedule(enabled: Boolean, intervalDays: Int, hour: Int, forceReplace: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK)
            return
        }
        val interval = Duration.ofDays(intervalDays.coerceAtLeast(1).toLong())
        val delay = ScheduleTime.initialDelayMillis(System.currentTimeMillis(), hour)
        val request = PeriodicWorkRequestBuilder<BackupWorker>(interval)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .setInitialDelay(Duration.ofMillis(delay))
            .build()
        val policy =
            if (forceReplace) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP
        workManager.enqueueUniquePeriodicWork(WORK, policy, request)
    }

    private companion object {
        const val WORK = "auto_backup"
    }
}
