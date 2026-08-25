package com.yingjing.pfa.data.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore by preferencesDataStore(name = "sync_state")

/** 记录上次同步时间与自动备份开关。 */
@Singleton
class SyncStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val lastSync: Flow<Long?> = context.syncDataStore.data.map { it[LAST_SYNC] }

    val autoBackupEnabled: Flow<Boolean> = context.syncDataStore.data.map { it[AUTO_BACKUP] ?: false }

    /** 指纹/面容快速登录开关；默认启用（仅在设备支持时生效）。 */
    val biometricEnabled: Flow<Boolean> = context.syncDataStore.data.map { it[BIOMETRIC] ?: true }

    suspend fun setLastSync(epochMs: Long) {
        context.syncDataStore.edit { it[LAST_SYNC] = epochMs }
    }

    suspend fun setAutoBackup(enabled: Boolean) {
        context.syncDataStore.edit { it[AUTO_BACKUP] = enabled }
    }

    suspend fun setBiometric(enabled: Boolean) {
        context.syncDataStore.edit { it[BIOMETRIC] = enabled }
    }

    private companion object {
        val LAST_SYNC = longPreferencesKey("last_sync_ms")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup_enabled")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
    }
}
