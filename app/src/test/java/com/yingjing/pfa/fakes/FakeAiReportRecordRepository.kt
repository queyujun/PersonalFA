package com.yingjing.pfa.fakes

import com.yingjing.pfa.domain.model.AiReportRecord
import com.yingjing.pfa.domain.repository.AiReportRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** 内存版 AI 结果记录仓库，用于 ViewModel 测试；observe 按 [AiReportRecord.userId] 隔离、生成时间从新到旧。 */
class FakeAiReportRecordRepository : AiReportRecordRepository {

    private val _records = MutableStateFlow<List<AiReportRecord>>(emptyList())

    /** 当前所有记录（测试断言用）。 */
    val records: List<AiReportRecord> get() = _records.value

    override fun observe(userId: Long, kind: String): Flow<List<AiReportRecord>> =
        _records.map { list ->
            list.filter { it.userId == userId && it.kind == kind }
                .sortedByDescending { it.createdAt }
        }

    override suspend fun save(
        userId: Long,
        kind: String,
        title: String,
        model: String?,
        markdown: String,
        createdAt: Long,
    ): Long {
        val id = (_records.value.maxOfOrNull { it.id } ?: 0L) + 1
        _records.value = _records.value + AiReportRecord(
            id = id,
            userId = userId,
            kind = kind,
            title = title,
            model = model,
            markdown = markdown,
            createdAt = createdAt,
        )
        return id
    }

    override suspend fun getById(id: Long): AiReportRecord? =
        _records.value.firstOrNull { it.id == id }

    override suspend fun delete(id: Long) {
        _records.value = _records.value.filterNot { it.id == id }
    }
}
