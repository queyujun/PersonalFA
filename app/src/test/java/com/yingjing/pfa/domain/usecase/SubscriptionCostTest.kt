package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.model.BillingCycle
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.Subscription
import com.yingjing.pfa.domain.model.SubscriptionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionCostTest {

    private val rates = FxRates(usdToCny = 7.0, hkdToCny = 0.9)

    private fun sub(
        id: Long,
        amount: Double,
        cycle: BillingCycle = BillingCycle.MONTHLY,
        currency: Currency = Currency.CNY,
        category: SubscriptionCategory = SubscriptionCategory.VIDEO,
        active: Boolean = true,
    ) = Subscription(
        id = id, userId = 1, name = "sub$id", category = category,
        currency = currency, amount = amount, cycle = cycle,
        firstBillEpochMs = 0, nextRenewalEpochMs = 0,
        reminderDaysBefore = 3, active = active,
    )

    @Test
    fun convertsCurrencies_toDisplayCurrency() {
        // CNY 30 + USD 10×7 = 100 CNY/月
        val summary = SubscriptionCost(
            listOf(sub(1, 30.0), sub(2, 10.0, currency = Currency.USD)),
            rates, Currency.CNY,
        )
        assertEquals(100.0, summary.monthly, 1e-9)
        assertEquals(1200.0, summary.yearly, 1e-9)
    }

    @Test
    fun excludesInactiveSubscriptions() {
        val summary = SubscriptionCost(
            listOf(sub(1, 30.0), sub(2, 50.0, active = false)),
            rates, Currency.CNY,
        )
        assertEquals(30.0, summary.monthly, 1e-9)
    }

    @Test
    fun cyclesFoldToMonthly() {
        // 周 15×52/12 + 月 30 + 季 90/3 + 年 348/12 = 65+30+30+29 = 154
        val summary = SubscriptionCost(
            listOf(
                sub(1, 15.0, cycle = BillingCycle.WEEKLY),
                sub(2, 30.0, cycle = BillingCycle.MONTHLY),
                sub(3, 90.0, cycle = BillingCycle.QUARTERLY),
                sub(4, 348.0, cycle = BillingCycle.YEARLY),
            ),
            rates, Currency.CNY,
        )
        assertEquals(154.0, summary.monthly, 1e-9)
    }

    @Test
    fun byCategory_sortedDescending_zeroExcluded() {
        val summary = SubscriptionCost(
            listOf(
                sub(1, 30.0, category = SubscriptionCategory.VIDEO),
                sub(2, 10.0, currency = Currency.USD, category = SubscriptionCategory.AI),
                sub(3, 8.0, category = SubscriptionCategory.MUSIC),
                sub(4, 0.0, category = SubscriptionCategory.OTHER),
                sub(5, 20.0, category = SubscriptionCategory.AI),
            ),
            rates, Currency.CNY,
        )
        // AI = 10×7 + 20 = 90 > VIDEO 30 > MUSIC 8；OTHER 为 0 被过滤
        val categories = summary.byCategory.map { it.category }
        assertEquals(listOf(SubscriptionCategory.AI, SubscriptionCategory.VIDEO, SubscriptionCategory.MUSIC), categories)
        assertEquals(90.0, summary.byCategory[0].monthly, 1e-9)
        assertTrue(summary.byCategory.none { it.category == SubscriptionCategory.OTHER })
    }

    @Test
    fun emptySubscriptions_zeroSummary() {
        val summary = SubscriptionCost(emptyList(), rates, Currency.CNY)
        assertEquals(0.0, summary.monthly, 1e-9)
        assertEquals(0.0, summary.yearly, 1e-9)
        assertTrue(summary.byCategory.isEmpty())
    }

    @Test
    fun displayCurrencySameAsSubscription_noConversion() {
        val summary = SubscriptionCost(listOf(sub(1, 88.0, currency = Currency.HKD)), rates, Currency.HKD)
        assertEquals(88.0, summary.monthly, 1e-9)
    }
}
