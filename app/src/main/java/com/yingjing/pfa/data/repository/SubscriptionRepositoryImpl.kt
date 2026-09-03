package com.yingjing.pfa.data.repository

import com.yingjing.pfa.data.local.SubscriptionDao
import com.yingjing.pfa.data.local.SubscriptionEntity
import com.yingjing.pfa.domain.model.BillingCycle
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Subscription
import com.yingjing.pfa.domain.model.SubscriptionCategory
import com.yingjing.pfa.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SubscriptionRepositoryImpl @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
) : SubscriptionRepository {

    override fun observeSubscriptions(userId: Long): Flow<List<Subscription>> =
        subscriptionDao.observeByUser(userId).map { list -> list.map { it.toDomain() } }

    override suspend fun getSubscriptionsSnapshot(userId: Long): List<Subscription> =
        subscriptionDao.getByUser(userId).map { it.toDomain() }

    override suspend fun getSubscription(id: Long): Subscription? =
        subscriptionDao.getById(id)?.toDomain()

    override suspend fun addSubscription(subscription: Subscription): Long {
        val now = System.currentTimeMillis()
        return subscriptionDao.upsert(
            subscription.copy(createdAtEpochMs = now, updatedAtEpochMs = now).toEntity(),
        )
    }

    override suspend fun updateSubscription(subscription: Subscription) {
        subscriptionDao.upsert(
            subscription.copy(updatedAtEpochMs = System.currentTimeMillis()).toEntity(),
        )
    }

    override suspend fun deleteSubscription(id: Long) = subscriptionDao.delete(id)
}

private fun SubscriptionEntity.toDomain() = Subscription(
    id = id,
    userId = userId,
    name = name,
    category = runCatching { SubscriptionCategory.valueOf(category) }
        .getOrDefault(SubscriptionCategory.OTHER),
    note = note,
    currency = Currency.fromCode(currency),
    amount = amount,
    cycle = runCatching { BillingCycle.valueOf(cycle) }.getOrDefault(BillingCycle.MONTHLY),
    firstBillEpochMs = firstBillEpochMs,
    nextRenewalEpochMs = nextRenewalEpochMs,
    reminderDaysBefore = reminderDaysBefore,
    paymentMethod = paymentMethod,
    active = active,
    createdAtEpochMs = createdAt,
    updatedAtEpochMs = updatedAt,
)

private fun Subscription.toEntity() = SubscriptionEntity(
    id = id,
    userId = userId,
    name = name,
    category = category.name,
    note = note,
    currency = currency.code,
    amount = amount,
    cycle = cycle.name,
    firstBillEpochMs = firstBillEpochMs,
    nextRenewalEpochMs = nextRenewalEpochMs,
    reminderDaysBefore = reminderDaysBefore,
    paymentMethod = paymentMethod,
    active = active,
    createdAt = createdAtEpochMs,
    updatedAt = updatedAtEpochMs,
)
