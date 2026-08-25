package com.yingjing.pfa.data.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore by preferencesDataStore(name = "sync_state")

/**
 * 偏好存储：上次同步时间、自动备份开关、指纹开关，
 * 以及行情/备份的调度配置（周期天数 + 整点小时）。
 */
@Singleton
class SyncStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val lastSync: Flow<Long?> = context.syncDataStore.data.map { it[LAST_SYNC] }

    val autoBackupEnabled: Flow<Boolean> = context.syncDataStore.data.map { it[AUTO_BACKUP] ?: false }

    /** 指纹/面容快速登录开关；默认启用（仅在设备支持时生效）。 */
    val biometricEnabled: Flow<Boolean> = context.syncDataStore.data.map { it[BIOMETRIC] ?: true }

    // 行情刷新调度（提醒跟随行情，一并抓取）
    val syncIntervalDays: Flow<Int> = context.syncDataStore.data.map { it[SYNC_INTERVAL] ?: 1 }
    val syncHour: Flow<Int> = context.syncDataStore.data.map { it[SYNC_HOUR] ?: 9 }

    // 自动备份调度
    val backupIntervalDays: Flow<Int> = context.syncDataStore.data.map { it[BACKUP_INTERVAL] ?: 7 }
    val backupHour: Flow<Int> = context.syncDataStore.data.map { it[BACKUP_HOUR] ?: 3 }

    suspend fun setLastSync(epochMs: Long) {
        context.syncDataStore.edit { it[LAST_SYNC] = epochMs }
    }

    suspend fun setAutoBackup(enabled: Boolean) {
        context.syncDataStore.edit { it[AUTO_BACKUP] = enabled }
    }

    suspend fun setBiometric(enabled: Boolean) {
        context.syncDataStore.edit { it[BIOMETRIC] = enabled }
    }

    suspend fun setSyncSchedule(intervalDays: Int, hour: Int) {
        context.syncDataStore.edit {
            it[SYNC_INTERVAL] = intervalDays.coerceAtLeast(1)
            it[SYNC_HOUR] = hour.coerceIn(0, 23)
        }
    }

    suspend fun setBackupSchedule(intervalDays: Int, hour: Int) {
        context.syncDataStore.edit {
            it[BACKUP_INTERVAL] = intervalDays.coerceAtLeast(1)
            it[BACKUP_HOUR] = hour.coerceIn(0, 23)
        }
    }

    private companion object {
        val LAST_SYNC = longPreferencesKey("last_sync_ms")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup_enabled")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val SYNC_INTERVAL = intPreferencesKey("sync_interval_days")
        val SYNC_HOUR = intPreferencesKey("sync_hour")
        val BACKUP_INTERVAL = intPreferencesKey("backup_interval_days")
        val BACKUP_HOUR = intPreferencesKey("backup_hour")
    }
}
