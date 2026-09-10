package com.yingjing.pfa.domain.repository

import com.yingjing.pfa.domain.model.Alert
import kotlinx.coroutines.flow.Flow

/** 提醒仓库。 */
interface AlertRepository {
    fun observe(userId: Long): Flow<List<Alert>>
    fun observeUnreadCount(userId: Long): Flow<Int>

    /** 去重插入；返回是否为新插入（用于决定是否发通知）。 */
    suspend fun insertIfNew(alert: Alert): Boolean
    suspend fun markRead(id: Long)
    suspend fun markAllRead(userId: Long)

    /** 删除单条（滑动删除）。 */
    suspend fun delete(userId: Long, id: Long)

    /** 批量删除（多选）。 */
    suspend fun delete(userId: Long, ids: List<Long>)

    /** 全部删除（当前用户）。 */
    suspend fun deleteAll(userId: Long)
}
