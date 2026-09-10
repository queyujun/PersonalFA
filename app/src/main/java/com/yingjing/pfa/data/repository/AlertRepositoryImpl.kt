package com.yingjing.pfa.data.repository

import com.yingjing.pfa.data.local.AlertDao
import com.yingjing.pfa.data.local.AlertEntity
import com.yingjing.pfa.domain.model.Alert
import com.yingjing.pfa.domain.model.AlertCategory
import com.yingjing.pfa.domain.model.AlertSeverity
import com.yingjing.pfa.domain.repository.AlertRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AlertRepositoryImpl @Inject constructor(
    private val dao: AlertDao,
) : AlertRepository {

    override fun observe(userId: Long): Flow<List<Alert>> =
        dao.observeByUser(userId).map { list -> list.map { it.toDomain() } }

    override fun observeUnreadCount(userId: Long): Flow<Int> = dao.observeUnreadCount(userId)

    override suspend fun insertIfNew(alert: Alert): Boolean = dao.insertIgnore(alert.toEntity()) != -1L

    override suspend fun markRead(id: Long) = dao.markRead(id)

    override suspend fun markAllRead(userId: Long) = dao.markAllRead(userId)

    override suspend fun delete(userId: Long, id: Long) = dao.deleteById(id)

    override suspend fun delete(userId: Long, ids: List<Long>) = dao.deleteByIds(userId, ids)

    override suspend fun deleteAll(userId: Long) = dao.deleteAllByUser(userId)
}

private fun AlertEntity.toDomain() = Alert(
    id = id,
    userId = userId,
    category = AlertCategory.valueOf(category),
    severity = AlertSeverity.valueOf(severity),
    title = title,
    body = body,
    refHoldingId = refHoldingId,
    dedupKey = dedupKey,
    createdAtEpochMs = createdAt,
    read = read,
)

private fun Alert.toEntity() = AlertEntity(
    id = id,
    userId = userId,
    category = category.name,
    severity = severity.name,
    title = title,
    body = body,
    refHoldingId = refHoldingId,
    dedupKey = dedupKey,
    createdAt = createdAtEpochMs,
    read = read,
)
