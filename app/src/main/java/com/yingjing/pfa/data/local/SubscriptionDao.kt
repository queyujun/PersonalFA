package com.yingjing.pfa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM subscriptions WHERE userId = :userId ORDER BY nextRenewalEpochMs ASC")
    fun observeByUser(userId: Long): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE userId = :userId")
    suspend fun getByUser(userId: Long): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getById(id: Long): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subscription: SubscriptionEntity): Long

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM subscriptions")
    suspend fun getAllForBackup(): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subscriptions: List<SubscriptionEntity>)

    @Query("DELETE FROM subscriptions")
    suspend fun deleteAll()
}
