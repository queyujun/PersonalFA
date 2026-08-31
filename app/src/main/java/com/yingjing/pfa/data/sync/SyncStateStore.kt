package com.yingjing.pfa.data.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yingjing.pfa.domain.model.SyncResult
import com.yingjing.pfa.domain.model.SyncSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore by preferencesDataStore(name = "sync_state")

/**
 * 偏好存储：上次同步时间、自动备份开关、指纹开关，
 * 以及行情/备份的调度配置（周期天数 + 整点小时）。
 *
 * 同步结果上报：[lastResult] 持久化最近一次同步的成败与失败源，供 UI 弹窗提示；
 * [lastResultShownAt] 记录用户已确认（dismiss）的结果时刻，用以区分「未见结果」。
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

    /** 最近一次自动备份成功的时间戳；尚未备份过时为 null。供备份页展示「上次备份时间」。 */
    val lastBackupAt: Flow<Long?> = context.syncDataStore.data.map { it[LAST_BACKUP_AT] }

    /** 最近一次同步结果；尚未同步过时为 null。 */
    val lastResult: Flow<SyncResult?> = context.syncDataStore.data.map { prefs ->
        val at = prefs[LAST_RESULT_AT] ?: return@map null
        val failed = prefs[LAST_RESULT_FAILED]
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { name -> runCatching { SyncSource.valueOf(name) }.getOrNull() }
            ?: emptyList()
        SyncResult(
            success = prefs[LAST_RESULT_SUCCESS] ?: false,
            failedSources = failed,
            completedAt = at,
            manual = prefs[LAST_RESULT_MANUAL] ?: false,
        )
    }

    /** 用户已 dismiss 的最近结果时刻；用于判断当前结果是否「尚未提示」。 */
    val lastResultShownAt: Flow<Long?> = context.syncDataStore.data.map { it[LAST_RESULT_SHOWN_AT] }

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

    /** 记录一次自动备份成功的时刻，供备份页展示。 */
    suspend fun setLastBackupAt(epochMs: Long) {
        context.syncDataStore.edit { it[LAST_BACKUP_AT] = epochMs }
    }

    /** 持久化一次同步结果，供 UI 上报。 */
    suspend fun setLastResult(result: SyncResult) {
        context.syncDataStore.edit {
            it[LAST_RESULT_SUCCESS] = result.success
            it[LAST_RESULT_FAILED] = result.failedSources.joinToString(",") { src -> src.name }
            it[LAST_RESULT_AT] = result.completedAt
            it[LAST_RESULT_MANUAL] = result.manual
        }
    }

    /** 标记某次结果已被用户确认（dismiss），不再重复弹窗。 */
    suspend fun setLastResultShownAt(epochMs: Long) {
        context.syncDataStore.edit { it[LAST_RESULT_SHOWN_AT] = epochMs }
    }

    private companion object {
        val LAST_SYNC = longPreferencesKey("last_sync_ms")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup_enabled")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val SYNC_INTERVAL = intPreferencesKey("sync_interval_days")
        val SYNC_HOUR = intPreferencesKey("sync_hour")
        val BACKUP_INTERVAL = intPreferencesKey("backup_interval_days")
        val BACKUP_HOUR = intPreferencesKey("backup_hour")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_RESULT_SUCCESS = booleanPreferencesKey("last_result_success")
        val LAST_RESULT_FAILED = stringPreferencesKey("last_result_failed")
        val LAST_RESULT_AT = longPreferencesKey("last_result_at")
        val LAST_RESULT_MANUAL = booleanPreferencesKey("last_result_manual")
        val LAST_RESULT_SHOWN_AT = longPreferencesKey("last_result_shown_at")
    }
}

