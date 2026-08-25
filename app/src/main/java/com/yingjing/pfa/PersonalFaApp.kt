package com.yingjing.pfa

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.yingjing.pfa.data.backup.BackupScheduler
import com.yingjing.pfa.data.sync.SyncScheduler
import com.yingjing.pfa.data.sync.SyncStateStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PersonalFaApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var backupScheduler: BackupScheduler
    @Inject lateinit var syncStateStore: SyncStateStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // 按已保存的调度配置注册后台任务；KEEP 不扰动已存在的调度（配置变更时由设置页 REPLACE）。
        CoroutineScope(Dispatchers.Default).launch {
            syncScheduler.schedule(
                intervalDays = syncStateStore.syncIntervalDays.first(),
                hour = syncStateStore.syncHour.first(),
                forceReplace = false,
            )
            if (syncStateStore.autoBackupEnabled.first()) {
                backupScheduler.schedule(
                    enabled = true,
                    intervalDays = syncStateStore.backupIntervalDays.first(),
                    hour = syncStateStore.backupHour.first(),
                    forceReplace = false,
                )
            }
        }
    }
}
