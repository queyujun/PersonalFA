package com.yingjing.pfa.data.repository

import com.yingjing.pfa.data.local.AiReportRecordDao
import com.yingjing.pfa.data.local.AiReportRecordEntity
import com.yingjing.pfa.domain.model.AiReportRecord
import com.yingjing.pfa.domain.repository.AiReportRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AiReportRecordRepositoryImpl @Inject constructor(
    private val dao: AiReportRecordDao,
) : AiReportRecordRepository {

    override fun observe(userId: Long, kind: String): Flow<List<AiReportRecord>> =
        dao.observeByUserAndKind(userId, kind).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun save(
        userId: Long,
        kind: String,
        title: String,
        model: String?,
        markdown: String,
        createdAt: Long,
    ): Long = dao.insert(
        AiReportRecordEntity(
            userId = userId,
            kind = kind,
            title = title,
            model = model,
            markdown = markdown,
            createdAt = createdAt,
        ),
    )

    override suspend fun getById(id: Long): AiReportRecord? = dao.getById(id)?.toDomain()

    override suspend fun delete(id: Long) = dao.deleteById(id)

    private fun AiReportRecordEntity.toDomain() = AiReportRecord(
        id = id,
        userId = userId,
        kind = kind,
        title = title,
        model = model,
        markdown = markdown,
        createdAt = createdAt,
    )
}
