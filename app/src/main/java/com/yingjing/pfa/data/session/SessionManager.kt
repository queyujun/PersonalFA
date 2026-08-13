package com.yingjing.pfa.data.session

import kotlinx.coroutines.flow.Flow

/** 会话抽象（便于测试注入 Fake）。 */
interface SessionManager {
    /** 当前登录用户；未登录为 null。 */
    val currentUserId: Flow<Long?>

    /** 上次登录用户（用于生物识别快速登录）；退出登录后仍保留。 */
    val lastUserId: Flow<Long?>

    /** 设为当前用户（同时记录为上次用户）。 */
    suspend fun setCurrentUser(userId: Long)

    /** 退出登录（清除当前用户，保留 lastUserId）。 */
    suspend fun clear()
}
