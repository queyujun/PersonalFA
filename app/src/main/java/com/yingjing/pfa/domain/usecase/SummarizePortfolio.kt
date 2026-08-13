package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.model.AssetCategory
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.model.HoldingValue

/** 组合汇总结果（均已换算到展示币种）。 */
data class PortfolioSummary(
    val displayCurrency: Currency,
    val totalAssets: Double,
    val totalLiabilities: Double,
    val netWorth: Double,
    val byCategory: List<CategoryAmount>,
)

data class CategoryAmount(val category: AssetCategory, val amount: Double)

/**
 * 汇总组合：将每笔持仓按其自身币种估值 → 用汇率换算到展示币种 → 分资产/负债与分类合计。
 * 纯函数，便于单元测试。
 */
object SummarizePortfolio {

    operator fun invoke(
        holdings: List<Holding>,
        rates: FxRates,
        displayCurrency: Currency,
        nowMs: Long,
    ): PortfolioSummary {
        var assets = 0.0
        var liabilities = 0.0
        val categoryTotals = linkedMapOf<AssetCategory, Double>()

        holdings.forEach { holding ->
            val nativeValue = HoldingValue.currentValue(holding, nowMs)
            val converted = rates.convert(nativeValue, holding.currency, displayCurrency)
            if (holding.category.isLiability) {
                // 负债 currentValue 已为负值
                liabilities += -converted
                categoryTotals.merge(AssetCategory.LIABILITY, converted, Double::plus)
            } else {
                assets += converted
                categoryTotals.merge(holding.category, converted, Double::plus)
            }
        }

        val byCategory = categoryTotals.map { CategoryAmount(it.key, it.value) }
        return PortfolioSummary(
            displayCurrency = displayCurrency,
            totalAssets = assets,
            totalLiabilities = liabilities,
            netWorth = assets - liabilities,
            byCategory = byCategory,
        )
    }
}
