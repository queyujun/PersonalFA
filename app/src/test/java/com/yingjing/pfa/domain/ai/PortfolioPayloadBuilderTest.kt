package com.yingjing.pfa.domain.ai

import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Holding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 负载构造：多币种换算、分类汇总、脱敏（toJson 不含 username/note/userId）、
 * includeDetails 降级、超预算降级。
 */
class PortfolioPayloadBuilderTest {

    private val now = 1_700_000_000_000L
    private val rates = FxRates(usdToCny = 7.0, hkdToCny = 0.9)

    private fun stock(
        name: String,
        currency: Currency = Currency.CNY,
        qty: Double = 100.0,
        price: Double = 10.0,
        cost: Double = 8.0,
    ) = Holding(
        userId = 1, type = AssetType.A_SHARE, name = name, currency = currency,
        quantity = qty, costPrice = cost, currentPrice = price,
    )

    private val fullJson = Json { ignoreUnknownKeys = true }

    @Test
    fun build_convertsMultiCurrency_andSumsTotals() {
        val holdings = listOf(
            stock("茅台", Currency.CNY, qty = 100.0, price = 10.0),   // 1000 CNY
            stock("AAPL", Currency.USD, qty = 10.0, price = 10.0),   // 100 USD → 700 CNY
        )
        val payload = PortfolioPayloadBuilder.build(holdings, rates, Currency.CNY, now)
        assertEquals(1700L, payload.totalAssets)
        assertEquals(1700L, payload.netWorth)
        assertEquals("CNY", payload.baseCurrency)
        assertEquals(1700L, payload.categories.first { it.category == "STOCK" }.amount)
    }

    @Test
    fun build_liabilityCountedSeparately() {
        val holdings = listOf(
            stock("s", qty = 100.0, price = 100.0), // 10000
            Holding(userId = 1, type = AssetType.LIABILITY, name = "房贷", currency = Currency.CNY, manualValue = 3000.0),
        )
        val payload = PortfolioPayloadBuilder.build(holdings, rates, Currency.CNY, now)
        assertEquals(10000L, payload.totalAssets)
        assertEquals(3000L, payload.totalLiabilities)
        assertEquals(7000L, payload.netWorth)
        assertTrue(payload.categories.any { it.category == "LIABILITY" })
    }

    @Test
    fun build_detailsIncludeWhitelistFields() {
        val holdings = listOf(
            Holding(
                userId = 1, type = AssetType.DEPOSIT, name = "定存", currency = Currency.CNY,
                manualValue = 10000.0, annualRatePercent = 2.5,
                maturityDateEpochMs = now + 180L * 24 * 3600 * 1000,
            ),
        )
        val payload = PortfolioPayloadBuilder.build(holdings, rates, Currency.CNY, now, includeDetails = true)
        val detail = payload.holdings!!.single()
        assertEquals("定存", detail.name)
        assertEquals(2.5, detail.depositAnnualRatePercent!!, 0.001)
        assertEquals(6, detail.depositRemainingMonths!!)
        assertNull(detail.plPct)
    }

    @Test
    fun build_sanitized_jsonNeverContainsSensitiveFields() {
        val holdings = listOf(
            stock("备注敏感持仓"),
            Holding(
                userId = 1, type = AssetType.MISC, name = "m", currency = Currency.CNY,
                manualValue = 1.0,
                note = "这是不应外发的自由备注",
            ),
        )
        val json = PortfolioPayloadBuilder.toJson(
            PortfolioPayloadBuilder.build(holdings, rates, Currency.CNY, now, includeDetails = true),
        )
        assertFalse(json.contains("note"))
        assertFalse(json.contains("不应外发"))
        assertFalse(json.contains("userId"))
        assertFalse(json.contains("username"))
        assertFalse(json.contains("createdAt"))
    }

    @Test
    fun build_includeDetailsFalse_omitsHoldings() {
        val payload = PortfolioPayloadBuilder.build(listOf(stock("s")), rates, Currency.CNY, now, includeDetails = false)
        assertNull(payload.holdings)
        // 汇总仍保留
        assertEquals(1000L, payload.totalAssets)
    }

    @Test
    fun build_overBudget_degradesToSummaryOnly() {
        // 300 笔明细足以超过 20k 字符预算 → 自动降级为仅汇总
        val holdings = (1..300).map { stock("持仓名称比较长一些的第 $it 号资产") }
        val payload = PortfolioPayloadBuilder.build(
            holdings, rates, Currency.CNY, now, includeDetails = true, charBudget = 20_000,
        )
        assertNull(payload.holdings)
        assertTrue(PortfolioPayloadBuilder.toJson(payload).length <= 20_000)
        // 汇总数字仍然正确
        assertEquals(300 * 1000L, payload.totalAssets)
    }

    @Test
    fun build_seriesShort_keptWhenTwoOrMore() {
        val payload = PortfolioPayloadBuilder.build(
            listOf(stock("s")), rates, Currency.CNY, now,
            netWorthSeries = listOf(100.0, 110.0, 120.5),
        )
        assertEquals(listOf(100L, 110L, 121L), payload.netWorthSeries)
    }

    @Test
    fun build_seriesSingleDropped() {
        val payload = PortfolioPayloadBuilder.build(
            listOf(stock("s")), rates, Currency.CNY, now,
            netWorthSeries = listOf(100.0),
        )
        assertNull(payload.netWorthSeries)
    }

    @Test
    fun toJson_isValidJson_andParses() {
        val json = PortfolioPayloadBuilder.toJson(
            PortfolioPayloadBuilder.build(listOf(stock("茅台")), rates, Currency.CNY, now),
        )
        val root = fullJson.parseToJsonElement(json).jsonObject
        assertEquals("CNY", root["baseCurrency"]!!.jsonPrimitive.content)
        val holdingsArray = root["holdings"]!!.jsonArray
        assertEquals("茅台", holdingsArray.first().jsonObject["name"]!!.jsonPrimitive.content)
        // 紧凑输出：无空格
        assertFalse(json.contains(": "))
    }

    @Test
    fun build_emptyHoldings_zeroTotals() {
        val payload = PortfolioPayloadBuilder.build(emptyList(), rates, Currency.CNY, now)
        assertEquals(0L, payload.totalAssets)
        assertTrue(payload.categories.isEmpty())
        assertNull(payload.holdings)
    }
}
