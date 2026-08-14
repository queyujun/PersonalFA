package com.yingjing.pfa.data.backup

import android.content.Context
import android.util.Base64
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yingjing.pfa.core.security.DatabaseKeyProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * 每周自动备份：用设备 Keystore 派生口令加密，写入 App 外部私有目录（本机安全副本）。
 * 便携备份请用「设置 → 立即备份」自选口令导出到用户选定文件。
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
    private val keyProvider: DatabaseKeyProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val passphrase = Base64.encodeToString(keyProvider.getOrCreatePassphrase(), Base64.NO_WRAP).toCharArray()
        val bytes = backupManager.export(passphrase)
        val dir = File(applicationContext.getExternalFilesDir(null), "backups").apply { mkdirs() }
        File(dir, "auto-backup.pfa").writeBytes(bytes)
        Result.success()
    }.getOrElse { Result.retry() }
}
