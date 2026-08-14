package com.yingjing.pfa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HoldingDao {
    @Insert
    suspend fun insert(holding: HoldingEntity): Long

    @Update
    suspend fun update(holding: HoldingEntity)

    @Query("DELETE FROM holdings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM holdings WHERE id = :id")
    suspend fun findById(id: Long): HoldingEntity?

    @Query("SELECT * FROM holdings WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeByUser(userId: Long): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holdings WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getByUser(userId: Long): List<HoldingEntity>

    @Query("SELECT * FROM holdings")
    suspend fun getAllForBackup(): List<HoldingEntity>

    @Insert
    suspend fun insertAll(holdings: List<HoldingEntity>)

    @Query("DELETE FROM holdings")
    suspend fun deleteAll()
}
