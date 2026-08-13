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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** 调度每日同步与立即刷新。 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** 注册每日周期同步（存在则保留）。 */
    fun scheduleDaily() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofHours(24))
            .setConstraints(networkConstraint)
            .setInitialDelay(Duration.ofMinutes(15))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(10))
            .build()
        workManager.enqueueUniquePeriodicWork(
            DAILY_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** 用户手动「立即刷新」。 */
    fun refreshNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .build()
        workManager.enqueueUniqueWork(REFRESH_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val DAILY_WORK = "daily_sync"
        const val REFRESH_WORK = "refresh_now"
    }
}
