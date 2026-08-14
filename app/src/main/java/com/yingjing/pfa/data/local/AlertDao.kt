package com.yingjing.pfa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    /** 去重插入（同一 userId+dedupKey 已存在则忽略）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(alert: AlertEntity): Long

    @Query("SELECT * FROM alerts WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeByUser(userId: Long): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts WHERE userId = :userId AND read = 0")
    fun observeUnreadCount(userId: Long): Flow<Int>

    @Query("UPDATE alerts SET read = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE alerts SET read = 1 WHERE userId = :userId")
    suspend fun markAllRead(userId: Long)

    @Query("SELECT * FROM alerts")
    suspend fun getAllForBackup(): List<AlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(alerts: List<AlertEntity>)

    @Query("DELETE FROM alerts")
    suspend fun deleteAll()
}
