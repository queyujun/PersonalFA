package com.yingjing.pfa.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore by preferencesDataStore(name = "sync_state")

/** 记录上次同步时间。 */
@Singleton
class SyncStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val lastSync: Flow<Long?> = context.syncDataStore.data.map { it[LAST_SYNC] }

    suspend fun setLastSync(epochMs: Long) {
        context.syncDataStore.edit { it[LAST_SYNC] = epochMs }
    }

    private companion object {
        val LAST_SYNC = longPreferencesKey("last_sync_ms")
    }
}
