package com.yingjing.pfa.domain.repository

import com.yingjing.pfa.domain.model.Subscription
import kotlinx.coroutines.flow.Flow

/** 订阅仓库：周期性订阅支出的增删改查。 */
interface SubscriptionRepository {
    fun observeSubscriptions(userId: Long): Flow<List<Subscription>>
    suspend fun getSubscriptionsSnapshot(userId: Long): List<Subscription>
    suspend fun getSubscription(id: Long): Subscription?
    suspend fun addSubscription(subscription: Subscription): Long
    suspend fun updateSubscription(subscription: Subscription)
    suspend fun deleteSubscription(id: Long)
}
