package com.yingjing.pfa.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 自动备份：用设备 Keystore 派生口令加密，写入 App 外部私有目录（本机安全副本），
 * 并记录成功时刻供备份页展示「上次备份时间」。
 *
 * 备份/恢复的实际逻辑封装在 [AutoBackupStore]，本类只负责 WorkManager 调度入口。
 * 便携备份请用「设置 → 立即备份」自选口令导出到用户选定文件。
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val autoBackupStore: AutoBackupStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        autoBackupStore.write()
        Result.success()
    }.getOrElse { Result.retry() }
}
