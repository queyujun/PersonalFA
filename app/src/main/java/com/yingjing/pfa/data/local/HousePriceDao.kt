package com.yingjing.pfa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HousePriceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<HousePriceIndexEntity>)

    @Query("SELECT * FROM house_price_index WHERE city = :city ORDER BY month ASC")
    suspend fun historyByCity(city: String): List<HousePriceIndexEntity>

    @Query("DELETE FROM house_price_index WHERE city = :city")
    suspend fun deleteByCity(city: String)
}
