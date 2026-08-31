package com.yingjing.pfa.data.backup

import android.content.Context
import android.util.Base64
import com.yingjing.pfa.core.security.DatabaseKeyProvider
import com.yingjing.pfa.data.sync.SyncStateStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本机自动备份的文件读写与恢复。
 *
 * 自动备份用设备 Keystore 派生口令加密，落盘于 App 私有外部存储（本机安全副本），
 * 故口令无需用户记忆——恢复时直接从 [DatabaseKeyProvider] 取同一把口令解密。
 *
 * 与「立即备份」（用户自选口令导出到 SAF 选定文件）互补：
 * - 自动备份 = 同设备/同安装下的安全网，覆盖写单个文件；
 * - 立即备份 = 跨设备便携备份，用户保管口令。
 */
@Singleton
class AutoBackupStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyProvider: DatabaseKeyProvider,
    private val backupManager: BackupManager,
    private val syncStateStore: SyncStateStore,
) {
    private val backupDir: File get() = File(context.getExternalFilesDir(null), "backups")
    private val backupFile: File get() = File(backupDir, AUTO_BACKUP_NAME)

    /** 自动备份文件是否存在（用于 UI 决定是否显示「从本机自动备份恢复」）。 */
    fun exists(): Boolean = backupFile.exists()

    /**
     * 写入自动备份（用 Keystore 派生口令加密）并记录成功时刻。
     * 任意步骤失败抛异常，由调用方决定重试策略。
     */
    suspend fun write(): Long {
        backupDir.mkdirs()
        val passphrase = Base64.encodeToString(keyProvider.getOrCreatePassphrase(), Base64.NO_WRAP).toCharArray()
        val bytes = backupManager.export(passphrase)
        backupFile.writeBytes(bytes)
        val now = System.currentTimeMillis()
        syncStateStore.setLastBackupAt(now)
        return now
    }

    /**
     * 从本机自动备份恢复；口令错误或格式非法返回 false。
     * 恢复会清空现有数据并按原 ID 还原（与 [BackupManager.import] 语义一致）。
     */
    suspend fun restore(): Boolean {
        if (!backupFile.exists()) return false
        val passphrase = Base64.encodeToString(keyProvider.getOrCreatePassphrase(), Base64.NO_WRAP).toCharArray()
        return backupManager.import(backupFile.readBytes(), passphrase)
    }

    private companion object {
        const val AUTO_BACKUP_NAME = "auto-backup.pfa"
    }
}
