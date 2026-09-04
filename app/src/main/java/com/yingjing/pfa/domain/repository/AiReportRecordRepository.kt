package com.yingjing.pfa.domain.repository

import com.yingjing.pfa.domain.model.AiReportRecord
import kotlinx.coroutines.flow.Flow

/** AI 生成结果（资产报告 / 持仓分析）历史仓库。 */
interface AiReportRecordRepository {
    /** 某用户某类记录，按生成时间从新到旧。 */
    fun observe(userId: Long, kind: String): Flow<List<AiReportRecord>>

    /** 保存一条生成结果，返回记录 id。 */
    suspend fun save(
        userId: Long,
        kind: String,
        title: String,
        model: String?,
        markdown: String,
        createdAt: Long,
    ): Long

    suspend fun getById(id: Long): AiReportRecord?

    suspend fun delete(id: Long)
}
