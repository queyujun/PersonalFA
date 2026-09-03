package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.Subscription
import com.yingjing.pfa.domain.model.SubscriptionCategory

/** 订阅支出汇总结果（均已换算到展示币种；仅统计 [Subscription.active]=true 的订阅）。 */
data class SubscriptionSummary(
    val displayCurrency: Currency,
    /** 月均支出。 */
    val monthly: Double,
    /** 年化支出（月均 × 12）。 */
    val yearly: Double,
    /** 分类月均明细（仅含金额 > 0 的分类，按金额降序）。 */
    val byCategory: List<CategoryMonthly>,
)

data class CategoryMonthly(val category: SubscriptionCategory, val monthly: Double)

/**
 * 汇总订阅支出：每条订阅按周期折算月均 → 用汇率换算到展示币种 → 合计与分类明细。
 * 纯函数，便于单元测试。
 */
object SubscriptionCost {

    operator fun invoke(
        subscriptions: List<Subscription>,
        rates: FxRates,
        displayCurrency: Currency,
    ): SubscriptionSummary {
        val categoryTotals = linkedMapOf<SubscriptionCategory, Double>()
        var monthly = 0.0
        subscriptions.filter { it.active }.forEach { sub ->
            val converted = rates.convert(sub.monthlyAmount, sub.currency, displayCurrency)
            monthly += converted
            categoryTotals.merge(sub.category, converted, Double::plus)
        }
        val byCategory = categoryTotals.asSequence()
            .map { CategoryMonthly(it.key, it.value) }
            .filter { it.monthly > 0.0 }
            .sortedByDescending { it.monthly }
            .toList()
        return SubscriptionSummary(
            displayCurrency = displayCurrency,
            monthly = monthly,
            yearly = monthly * 12.0,
            byCategory = byCategory,
        )
    }
}
