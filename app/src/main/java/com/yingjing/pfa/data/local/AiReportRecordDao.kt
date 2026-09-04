package com.yingjing.pfa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** AI 结果记录（资产报告 / 持仓分析）数据访问。 */
@Dao
interface AiReportRecordDao {
    @Insert
    suspend fun insert(record: AiReportRecordEntity): Long

    @Query("SELECT * FROM ai_report_records WHERE userId = :userId AND kind = :kind ORDER BY createdAt DESC")
    fun observeByUserAndKind(userId: Long, kind: String): Flow<List<AiReportRecordEntity>>

    @Query("SELECT * FROM ai_report_records WHERE id = :id")
    suspend fun getById(id: Long): AiReportRecordEntity?

    @Query("DELETE FROM ai_report_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM ai_report_records")
    suspend fun getAllForBackup(): List<AiReportRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<AiReportRecordEntity>)

    @Query("DELETE FROM ai_report_records")
    suspend fun deleteAll()
}
