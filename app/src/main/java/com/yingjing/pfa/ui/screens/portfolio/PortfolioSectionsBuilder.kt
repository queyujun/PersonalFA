package com.yingjing.pfa.ui.screens.portfolio

import androidx.annotation.StringRes
import com.yingjing.pfa.R
import com.yingjing.pfa.core.i18n.StringResolver
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

/**
 * 类目下的子分组：股票用市场子类目（有 [title]）；其它类目 title=null，单组。
 * totalText 为该子组合计（展示币种）。
 * [subKey] 为 locale 无关的稳定折叠键（股票子组 = [StockSub].name，其它 = null），
 * 供折叠状态持久化——语言切换不改变它，故折叠态不被重置。
 */
data class PortfolioSubGroup(
    val title: String?,
    val subKey: String?,
    val totalText: String,
    val rows: List<HoldingRow>,
)

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
 *
 * 所有面向用户的文本经 [resolver] 按当前 locale 解析，本函数不硬编码中文。
 */
object PortfolioSectionsBuilder {

    private val CATEGORY_ORDER = listOf(
        AssetCategory.REAL_ESTATE, AssetCategory.STOCK, AssetCategory.DEPOSIT,
        AssetCategory.GOLD, AssetCategory.BOND, AssetCategory.EQUITY,
        AssetCategory.CRYPTO, AssetCategory.OTC_FUND, AssetCategory.MISC,
        AssetCategory.LIABILITY,
    )

    /** 股票市场子类目，声明顺序即展示顺序。subKey = 枚举名（locale 无关，作折叠键）。 */
    private enum class StockSub(@StringRes val titleRes: Int) {
        US_STOCK(R.string.stock_sub_us_stock),
        US_CASH(R.string.stock_sub_us_cash),
        A_SHARE(R.string.stock_sub_a_share),
        A_CASH(R.string.stock_sub_a_cash),
        HK_STOCK(R.string.stock_sub_hk_stock),
        HK_CASH(R.string.stock_sub_hk_cash),
    }

    /** 黄金子分组，声明顺序即展示顺序。subKey = 枚举名（locale 无关，作折叠键）。 */
    private enum class GoldSub(@StringRes val titleRes: Int) {
        GOLD_ETF(R.string.gold_sub_gold_etf),
        PHYSICAL_GOLD(R.string.gold_sub_physical_gold),
    }

    fun build(
        holdings: List<Holding>,
        rates: FxRates,
        displayCurrency: Currency,
        nowMs: Long,
        resolver: StringResolver,
    ): PortfolioUiState {
        val byCategory = holdings.groupBy { it.category }
        val sections = CATEGORY_ORDER.mapNotNull { category ->
            val items = byCategory[category] ?: return@mapNotNull null
            val subGroups = when (category) {
                AssetCategory.STOCK -> stockSubGroups(items, rates, displayCurrency, nowMs, resolver)
                AssetCategory.GOLD -> goldSubGroups(items, rates, displayCurrency, nowMs, resolver)
                else -> listOf(subGroup(null, null, items, rates, displayCurrency, nowMs, resolver))
            }
            val total = items.sumOf { converted(it, rates, displayCurrency, nowMs) }
            PortfolioSection(
                category = category,
                title = resolver.get(category.displayRes),
                // 分类合计保持简洁：不显示小数（四舍五入取整）。
                totalText = MoneyFormat.formatWhole(total, displayCurrency, resolver),
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
        resolver: StringResolver,
    ): List<PortfolioSubGroup> {
        val bySub = items.groupBy { stockSubOf(it) }
        return StockSub.entries.mapNotNull { sub ->
            val rows = bySub[sub] ?: return@mapNotNull null
            subGroup(resolver.get(sub.titleRes), sub.name, rows, rates, cur, nowMs, resolver)
        }
    }

    private fun subGroup(
        title: String?,
        subKey: String?,
        items: List<Holding>,
        rates: FxRates,
        cur: Currency,
        nowMs: Long,
        resolver: StringResolver,
    ): PortfolioSubGroup {
        val rows = items
            .sortedWith(
                compareByDescending<Holding> { abs(converted(it, rates, cur, nowMs)) }
                    .thenBy { it.name },
            )
            .map { toRow(it, nowMs, resolver) }
        val total = items.sumOf { converted(it, rates, cur, nowMs) }
        return PortfolioSubGroup(title, subKey, MoneyFormat.formatWhole(total, cur, resolver), rows)
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

    private fun goldSubGroups(
        items: List<Holding>,
        rates: FxRates,
        cur: Currency,
        nowMs: Long,
        resolver: StringResolver,
    ): List<PortfolioSubGroup> {
        val bySub = items.groupBy { goldSubOf(it) }
        return GoldSub.entries.mapNotNull { sub ->
            val rows = bySub[sub] ?: return@mapNotNull null
            subGroup(resolver.get(sub.titleRes), sub.name, rows, rates, cur, nowMs, resolver)
        }
    }

    private fun goldSubOf(h: Holding): GoldSub = when (h.type) {
        AssetType.GOLD_ETF -> GoldSub.GOLD_ETF
        AssetType.PHYSICAL_GOLD -> GoldSub.PHYSICAL_GOLD
        else -> GoldSub.GOLD_ETF // 黄金类目仅含上述类型，兜底不会触发
    }

    private fun converted(h: Holding, rates: FxRates, cur: Currency, nowMs: Long): Double =
        rates.convert(HoldingValue.currentValue(h, nowMs), h.currency, cur)

    private fun toRow(holding: Holding, nowMs: Long, resolver: StringResolver): HoldingRow {
        val value = HoldingValue.currentValue(holding, nowMs)
        val profit = HoldingValue.profit(holding, nowMs)
        return HoldingRow(
            id = holding.id,
            name = holding.name,
            subtitle = subtitle(holding, resolver),
            valueText = MoneyFormat.format(value, holding.currency, resolver),
            profitText = profit?.let { MoneyFormat.formatSigned(it, holding.currency, resolver) },
            profitPositive = (profit ?: 0.0) >= 0.0,
        )
    }

    private fun subtitle(h: Holding, resolver: StringResolver): String = when (h.type) {
        AssetType.A_SHARE -> resolver.get(R.string.sub_a_share, h.symbol ?: "", qty(h.quantity))
        AssetType.HK_STOCK -> resolver.get(R.string.sub_hk_stock, h.symbol ?: "", qty(h.quantity))
        AssetType.US_STOCK -> resolver.get(R.string.sub_us_stock, h.symbol ?: "", qty(h.quantity))
        AssetType.GOLD_ETF -> resolver.get(R.string.sub_gold_etf, h.symbol ?: "", qty(h.quantity))
        AssetType.PHYSICAL_GOLD -> resolver.get(
            R.string.sub_physical_gold,
            qty(h.quantity),
            MoneyFormat.format(h.currentPrice ?: h.costPrice ?: 0.0, h.currency, resolver),
        )
        AssetType.BOND_ETF -> resolver.get(R.string.sub_bond_etf, h.symbol ?: "", qty(h.quantity))
        AssetType.OTC_FUND -> resolver.get(R.string.sub_otc_fund, h.symbol ?: "", qty(h.quantity))
        AssetType.CRYPTO -> resolver.get(R.string.sub_crypto, h.symbol ?: "", qty(h.quantity))
        AssetType.ACCOUNT_CASH -> resolver.get(R.string.sub_cash, resolver.get(h.currency.labelRes))
        AssetType.DEPOSIT -> resolver.get(R.string.sub_deposit, h.annualRatePercent ?: 0.0)
        AssetType.REAL_ESTATE ->
            listOfNotNull(h.city, h.areaSqm?.let { resolver.get(R.string.sub_real_estate_area, qty(it)) })
                .joinToString(" · ")
                .ifBlank { resolver.get(R.string.sub_real_estate_fallback) }
        AssetType.EQUITY -> resolver.get(R.string.sub_equity)
        AssetType.MISC -> h.note ?: ""
        AssetType.LIABILITY -> liabilitySubtitle(h, resolver)
    }

    private fun liabilitySubtitle(h: Holding, resolver: StringResolver): String {
        val base = h.liabilityType ?: resolver.get(R.string.sub_liability_default)
        val monthly = h.monthlyPayment
        if (monthly == null || monthly <= 0.0) return base
        val day = h.repaymentDay?.let { resolver.get(R.string.sub_repay_day, it) }
            ?: resolver.get(R.string.sub_repay_last_day)
        return resolver.get(R.string.sub_liability_template, base, qty(monthly), day)
    }

    private fun qty(v: Double?): String {
        v ?: return "0"
        return if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
    }
}
