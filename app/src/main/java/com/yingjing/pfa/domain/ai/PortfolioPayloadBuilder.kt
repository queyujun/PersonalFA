package com.yingjing.pfa.domain.ai

import com.yingjing.pfa.domain.model.AssetCategory
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.model.HoldingValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong

/**
 * 发送给 AI 的组合数据负载。**隐私白名单模型**：只包含允许外发的字段，
 * 任何未在此声明的敏感信息（用户身份、备注 note、时间戳等）从结构上就不可能进入 payload。
 */
@Serializable
data class AiPortfolioPayload(
    val baseCurrency: String,
    val totalAssets: Long,
    val totalLiabilities: Long,
    val netWorth: Long,
    /** 分类汇总（名称用英文枚举名，避免本地化字符串影响模型稳定性）。 */
    val categories: List<AiCategoryAmount>,
    /** 净值走势（按日期升序，单位 baseCurrency）；null = 无历史快照。 */
    val netWorthSeries: List<Long>? = null,
    /** 明细持仓；detailMode=false 时为 null（只发汇总，进一步降外发量）。 */
    val holdings: List<AiHolding>? = null,
)

@Serializable
data class AiCategoryAmount(
    val category: String,
    val amount: Long,
    /** 占总资产的百分比（0-100，一位小数）；负债类为占负债比。 */
    val percent: Double,
)

@Serializable
data class AiHolding(
    val category: String,
    val type: String,
    val name: String,
    val currency: String,
    val quantity: Double? = null,
    val costPrice: Double? = null,
    val currentPrice: Double? = null,
    /** 统一换算到 baseCurrency 后的当前价值（取整）。 */
    val value: Long,
    /** 盈亏比例（%），按成本口径；不可得为 null。 */
    val plPct: Double? = null,
    // 存款
    val depositAnnualRatePercent: Double? = null,
    val depositRemainingMonths: Int? = null,
    // 房产
    val city: String? = null,
    val areaSqm: Double? = null,
    // 公司股权
    val sharePercent: Double? = null,
    // 负债
    val monthlyPayment: Double? = null,
)

/**
 * 构造 AI 负载：估值 → 换算到展示币种 → 白名单字段 → 紧凑 JSON。
 *
 * 字符预算（默认 20k）：超过时自动降级 detailMode（剔除明细，只发分类汇总），
 * 保证 prompt 不超限。纯函数，便于单元测试与脱敏断言。
 */
object PortfolioPayloadBuilder {

    fun build(
        holdings: List<Holding>,
        rates: FxRates,
        displayCurrency: Currency,
        nowMs: Long,
        netWorthSeries: List<Double>? = null,
        includeDetails: Boolean = true,
        charBudget: Int = DEFAULT_CHAR_BUDGET,
    ): AiPortfolioPayload {
        val detail = buildPayload(holdings, rates, displayCurrency, nowMs, netWorthSeries, includeDetails, charBudget)
        if (!detail.holdings.isNullOrEmpty()) return detail

        // 明细为空（原本就无明细 / 已降级）仍可能超预算吗？——汇总模式体积极小，几乎不可能；
        // 若真超预算，截断序列（最老的部分对分析价值最低）。
        if (toJson(detail).length <= charBudget) return detail
        return detail.copy(netWorthSeries = detail.netWorthSeries?.takeLast(SERIES_MIN_KEEP))
    }

    private fun buildPayload(
        holdings: List<Holding>,
        rates: FxRates,
        displayCurrency: Currency,
        nowMs: Long,
        netWorthSeries: List<Double>?,
        includeDetails: Boolean,
        charBudget: Int,
    ): AiPortfolioPayload {
        var totalAssets = 0.0
        var totalLiabilities = 0.0
        val categoryTotals = linkedMapOf<AssetCategory, Double>()

        data class Detail(val payload: AiHolding, val category: AssetCategory, val converted: Double)

        val details = mutableListOf<Detail>()

        holdings.forEach { holding ->
            val native = HoldingValue.currentValue(holding, nowMs)
            val converted = rates.convert(native, holding.currency, displayCurrency)
            if (holding.category.isLiability) {
                totalLiabilities += -converted
                categoryTotals.merge(AssetCategory.LIABILITY, converted, Double::plus)
            } else {
                totalAssets += converted
                categoryTotals.merge(holding.category, converted, Double::plus)
            }
            if (includeDetails) details += Detail(toAiHolding(holding, displayCurrency, rates, nowMs, converted), holding.category, converted)
        }

        val categories = categoryTotals.map { (category, amount) ->
            val base = if (category.isLiability) totalLiabilities else totalAssets
            AiCategoryAmount(
                category = category.name,
                amount = amount.roundToLong(),
                percent = if (base > 0) (amount / base * 100 * 10).roundToLong() / 10.0 else 0.0,
            )
        }.sortedByDescending { it.amount }

        // 明细模式但明细整体超预算 → 降级为汇总模式（重新构造，不发任何明细）
        var payloadHoldings: List<AiHolding>? = null
        if (includeDetails && details.isNotEmpty()) {
            payloadHoldings = details.sortedByDescending { it.converted }.map { it.payload }
        }

        val series = netWorthSeries?.map { it.roundToLong() }?.takeIf { it.size >= 2 }

        val payload = AiPortfolioPayload(
            baseCurrency = displayCurrency.name,
            totalAssets = totalAssets.roundToLong(),
            totalLiabilities = totalLiabilities.roundToLong(),
            netWorth = (totalAssets - totalLiabilities).roundToLong(),
            categories = categories,
            netWorthSeries = series,
            holdings = payloadHoldings,
        )

        return if (toJson(payload).length > charBudget && payloadHoldings != null) {            // 降级：剔除明细，重算汇总（汇总本身必须始终保留）
            AiPortfolioPayload(
                baseCurrency = payload.baseCurrency,
                totalAssets = payload.totalAssets,
                totalLiabilities = payload.totalLiabilities,
                netWorth = payload.netWorth,
                categories = payload.categories,
                netWorthSeries = payload.netWorthSeries,
                holdings = null,
            )
        } else {
            payload
        }
    }

    /** 白名单字段映射：只取允许外发的字段，note/身份/时间戳等结构上无法进入。 */
    private fun toAiHolding(
        holding: Holding,
        displayCurrency: Currency,
        rates: FxRates,
        nowMs: Long,
        converted: Double,
    ): AiHolding {
        val cost = holding.costPrice
        val price = holding.currentPrice ?: holding.costPrice
        val plPct = if (cost != null && cost != 0.0 && price != null && holding.quantity != null) {
            ((price - cost) / cost * 100 * 10).roundToLong() / 10.0
        } else {
            null
        }
        val remainingMonths = holding.maturityDateEpochMs?.takeIf { it > nowMs }?.let {
            ((it - nowMs).toDouble() / (30.44 * 24 * 3600 * 1000)).roundToLong().toInt()
        }
        return AiHolding(
            category = holding.category.name,
            type = holding.type.name,
            name = holding.name,
            currency = holding.currency.name,
            quantity = holding.quantity,
            costPrice = cost,
            currentPrice = holding.currentPrice,
            value = converted.roundToLong(),
            plPct = plPct,
            depositAnnualRatePercent = holding.annualRatePercent,
            depositRemainingMonths = remainingMonths,
            city = holding.city,
            areaSqm = holding.areaSqm,
            sharePercent = holding.sharePercent,
            monthlyPayment = holding.monthlyPayment,
        )
    }

    fun toJson(payload: AiPortfolioPayload): String = json.encodeToString(
        AiPortfolioPayload.serializer(),
        payload,
    )

    // 紧凑输出（无空格缩进）
    private val json = Json { explicitNulls = false }

    private const val DEFAULT_CHAR_BUDGET = 20_000
    private const val SERIES_MIN_KEEP = 30
}
