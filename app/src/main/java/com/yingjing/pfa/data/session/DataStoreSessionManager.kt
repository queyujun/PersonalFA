package com.yingjing.pfa.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/** DataStore 实现的会话存储。 */
@Singleton
class DataStoreSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : SessionManager {

    override val currentUserId: Flow<Long?> =
        context.sessionDataStore.data.map { it[CURRENT_USER_ID]?.takeIf { id -> id >= 0 } }

    override val lastUserId: Flow<Long?> =
        context.sessionDataStore.data.map { it[LAST_USER_ID]?.takeIf { id -> id >= 0 } }

    override suspend fun setCurrentUser(userId: Long) {
        context.sessionDataStore.edit {
            it[CURRENT_USER_ID] = userId
            it[LAST_USER_ID] = userId
        }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { it.remove(CURRENT_USER_ID) }
    }

    private companion object {
        val CURRENT_USER_ID = longPreferencesKey("current_user_id")
        val LAST_USER_ID = longPreferencesKey("last_user_id")
    }
}
