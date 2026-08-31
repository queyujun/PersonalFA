package com.yingjing.pfa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategorySnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<CategorySnapshotEntity>)

    @Query("SELECT * FROM category_snapshots WHERE userId = :userId ORDER BY dayEpochDay ASC")
    fun observeByUser(userId: Long): Flow<List<CategorySnapshotEntity>>

    @Query("SELECT * FROM category_snapshots")
    suspend fun getAllForBackup(): List<CategorySnapshotEntity>

    @Insert
    suspend fun insertAll(rows: List<CategorySnapshotEntity>)

    @Query("DELETE FROM category_snapshots")
    suspend fun deleteAll()

    /** 删除某用户指定日期（不含当天）之前的所有分类快照。 */
    @Query("DELETE FROM category_snapshots WHERE userId = :userId AND dayEpochDay < :dayEpochDay")
    suspend fun deleteOlderThan(userId: Long, dayEpochDay: Long)

    /** 删除某用户指定日期当天的全部分类快照行（重写当日分类快照前先清理）。 */
    @Query("DELETE FROM category_snapshots WHERE userId = :userId AND dayEpochDay = :dayEpochDay")
    suspend fun deleteByUserAndDay(userId: Long, dayEpochDay: Long)
}
