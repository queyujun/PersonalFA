package com.yingjing.pfa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NetWorthSnapshotDao {
    /** 按天去重写入（同一天覆盖）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: NetWorthSnapshotEntity)

    @Query("SELECT * FROM net_worth_snapshots WHERE userId = :userId ORDER BY dayEpochDay ASC")
    fun observeByUser(userId: Long): Flow<List<NetWorthSnapshotEntity>>

    @Query("SELECT * FROM net_worth_snapshots WHERE userId = :userId ORDER BY dayEpochDay ASC")
    suspend fun getByUser(userId: Long): List<NetWorthSnapshotEntity>

    @Query("SELECT * FROM net_worth_snapshots")
    suspend fun getAllForBackup(): List<NetWorthSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<NetWorthSnapshotEntity>)

    @Query("DELETE FROM net_worth_snapshots")
    suspend fun deleteAll()
}
