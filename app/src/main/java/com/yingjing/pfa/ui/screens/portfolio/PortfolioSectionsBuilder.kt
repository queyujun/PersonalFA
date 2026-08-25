package com.yingjing.pfa.ui.screens.portfolio

import com.yingjing.pfa.domain.model.AssetCategory
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.model.HoldingValue
import com.yingjing.pfa.ui.format.MoneyFormat
import kotlin.math.abs

/** 资产页一行持仓（展示用）。金额文本为持仓自身币种。 */
data class HoldingRow(
    val id: Long,
    val name: String,
    val subtitle: String,
    val valueText: String,
    val profitText: String?,
    val profitPositive: Boolean,
)

/** 类目下的子分组：股票用市场子类目（有 title）；其它类目 title=null，单组。totalText 为该子组合计（展示币种）。 */
data class PortfolioSubGroup(val title: String?, val totalText: String, val rows: List<HoldingRow>)

/** 一个一级资产类目区块（含合计与子分组）。 */
data class PortfolioSection(
    val category: AssetCategory,
    val title: String,
    val totalText: String,
    val subGroups: List<PortfolioSubGroup>,
)

data class PortfolioUiState(val sections: List<PortfolioSection> = emptyList()) {
    val isEmpty: Boolean get() = sections.isEmpty()
}

/**
 * 把持仓列表组织为资产页分组结构（纯函数，可单测）：
 * - 一级按 [CATEGORY_ORDER] 归类；
 * - 股票类目内再分 6 个市场子类目（账户现金按币种拆为 美股/A股/港股 现金）；
 * - 每个子组内按「换算到展示币种」的市值绝对值降序（同值按名称）；
 * - 类目合计按展示币种汇总。
 */
object PortfolioSectionsBuilder {

    private val CATEGORY_ORDER = listOf(
        AssetCategory.REAL_ESTATE, AssetCategory.STOCK, AssetCategory.DEPOSIT,
        AssetCategory.GOLD, AssetCategory.BOND, AssetCategory.EQUITY,
        AssetCategory.CRYPTO, AssetCategory.LIABILITY,
    )

    /** 股票市场子类目，声明顺序即展示顺序。 */
    private enum class StockSub(val title: String) {
        US_STOCK("美股"),
        US_CASH("美股现金"),
        A_SHARE("A股"),
        A_CASH("A股现金"),
        HK_STOCK("港股"),
        HK_CASH("港股现金"),
    }

    fun build(
        holdings: List<Holding>,
        rates: FxRates,
        displayCurrency: Currency,
        nowMs: Long,
    ): PortfolioUiState {
        val byCategory = holdings.groupBy { it.category }
        val sections = CATEGORY_ORDER.mapNotNull { category ->
            val items = byCategory[category] ?: return@mapNotNull null
            val subGroups = if (category == AssetCategory.STOCK) {
                stockSubGroups(items, rates, displayCurrency, nowMs)
            } else {
                listOf(subGroup(null, items, rates, displayCurrency, nowMs))
            }
            val total = items.sumOf { converted(it, rates, displayCurrency, nowMs) }
            PortfolioSection(
                category = category,
                title = category.displayName,
                totalText = MoneyFormat.format(total, displayCurrency),
                subGroups = subGroups,
            )
        }
        return PortfolioUiState(sections)
    }

    private fun stockSubGroups(
        items: List<Holding>,
        rates: FxRates,
        cur: Currency,
        nowMs: Long,
    ): List<PortfolioSubGroup> {
        val bySub = items.groupBy { stockSubOf(it) }
        return StockSub.entries.mapNotNull { sub ->
            val rows = bySub[sub] ?: return@mapNotNull null
            subGroup(sub.title, rows, rates, cur, nowMs)
        }
    }

    private fun subGroup(
        title: String?,
        items: List<Holding>,
        rates: FxRates,
        cur: Currency,
        nowMs: Long,
    ): PortfolioSubGroup {
        val rows = items
            .sortedWith(
                compareByDescending<Holding> { abs(converted(it, rates, cur, nowMs)) }
                    .thenBy { it.name },
            )
            .map { toRow(it, nowMs) }
        val total = items.sumOf { converted(it, rates, cur, nowMs) }
        return PortfolioSubGroup(title, MoneyFormat.format(total, cur), rows)
    }

    private fun stockSubOf(h: Holding): StockSub = when (h.type) {
        AssetType.US_STOCK -> StockSub.US_STOCK
        AssetType.A_SHARE -> StockSub.A_SHARE
        AssetType.HK_STOCK -> StockSub.HK_STOCK
        AssetType.ACCOUNT_CASH -> when (h.currency) {
            Currency.USD -> StockSub.US_CASH
            Currency.HKD -> StockSub.HK_CASH
            Currency.CNY -> StockSub.A_CASH
        }
        else -> StockSub.A_SHARE // 股票类目仅含上述类型，兜底不会触发
    }

    private fun converted(h: Holding, rates: FxRates, cur: Currency, nowMs: Long): Double =
        rates.convert(HoldingValue.currentValue(h, nowMs), h.currency, cur)

    private fun toRow(holding: Holding, nowMs: Long): HoldingRow {
        val value = HoldingValue.currentValue(holding, nowMs)
        val profit = HoldingValue.profit(holding, nowMs)
        return HoldingRow(
            id = holding.id,
            name = holding.name,
            subtitle = subtitle(holding),
            valueText = MoneyFormat.format(value, holding.currency),
            profitText = profit?.let { MoneyFormat.formatSigned(it, holding.currency) },
            profitPositive = (profit ?: 0.0) >= 0.0,
        )
    }

    private fun subtitle(h: Holding): String = when (h.type) {
        AssetType.A_SHARE -> "A股 ${h.symbol} · ${qty(h.quantity)}股"
        AssetType.HK_STOCK -> "港股 ${h.symbol} · ${qty(h.quantity)}股"
        AssetType.US_STOCK -> "美股 ${h.symbol} · ${qty(h.quantity)}股"
        AssetType.GOLD_ETF -> "黄金ETF ${h.symbol} · ${qty(h.quantity)}份"
        AssetType.BOND_ETF -> "国债ETF ${h.symbol} · ${qty(h.quantity)}份"
        AssetType.CRYPTO -> "${h.symbol} · ${qty(h.quantity)}"
        AssetType.ACCOUNT_CASH -> "${h.currency.label}现金"
        AssetType.DEPOSIT -> "年化 ${h.annualRatePercent ?: 0.0}% · 每日计息"
        AssetType.REAL_ESTATE ->
            listOfNotNull(h.city, h.areaSqm?.let { "${qty(it)}㎡" }).joinToString(" · ").ifBlank { "房产" }
        AssetType.EQUITY -> "未上市 · 手动估值"
        AssetType.LIABILITY -> liabilitySubtitle(h)
    }

    private fun liabilitySubtitle(h: Holding): String {
        val base = h.liabilityType ?: "负债"
        val monthly = h.monthlyPayment
        if (monthly == null || monthly <= 0.0) return base
        val day = h.repaymentDay?.let { "每月${it}号" } ?: "每月最后一天"
        return "$base · 还本${qty(monthly)}/月 · $day"
    }

    private fun qty(v: Double?): String {
        v ?: return "0"
        return if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
    }
}
