package com.yingjing.pfa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketSeriesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<MarketSeriesPointEntity>)

    @Query("DELETE FROM market_series_points WHERE seriesCode = :code")
    suspend fun deleteSeries(code: String)

    /** 全量替换某序列（CBOE 每次返回全量，删后写保证与源一致）。单事务，读者不会看到半截数据。 */
    @Transaction
    suspend fun replaceSeries(code: String, rows: List<MarketSeriesPointEntity>) {
        deleteSeries(code)
        upsertAll(rows)
    }

    /** 批量写入多个序列（一次刷新的全部结果合成一个事务，只触发一次 Flow 重算）。 */
    @Transaction
    suspend fun applyBatch(replaced: Map<String, List<MarketSeriesPointEntity>>, upserted: List<MarketSeriesPointEntity>) {
        replaced.forEach { (code, rows) ->
            deleteSeries(code)
            upsertAll(rows)
        }
        if (upserted.isNotEmpty()) upsertAll(upserted)
    }

    @Query("SELECT MAX(epochDay) FROM market_series_points WHERE seriesCode = :code")
    suspend fun latestDay(code: String): Long?

    @Query("SELECT * FROM market_series_points WHERE seriesCode = :code ORDER BY epochDay ASC")
    suspend fun series(code: String): List<MarketSeriesPointEntity>

    @Query("SELECT * FROM market_series_points WHERE seriesCode IN (:codes) ORDER BY seriesCode ASC, epochDay ASC")
    fun observeSeries(codes: List<String>): Flow<List<MarketSeriesPointEntity>>

    /** 只取 [fromDay]（含）之后的数据：总览只需近 1 年，避免每次读出数万行全历史。 */
    @Query(
        "SELECT * FROM market_series_points WHERE seriesCode IN (:codes) AND epochDay >= :fromDay " +
            "ORDER BY seriesCode ASC, epochDay ASC",
    )
    fun observeSeriesSince(codes: List<String>, fromDay: Long): Flow<List<MarketSeriesPointEntity>>
}
