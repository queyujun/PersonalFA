package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.model.AssetCategory
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Holding
import org.junit.Assert.assertEquals
import org.junit.Test

class SummarizePortfolioTest {

    private val now = 1_700_000_000_000L
    private val rates = FxRates(usdToCny = 7.0, hkdToCny = 0.9)

    private fun stock(currency: Currency, qty: Double, price: Double) = Holding(
        userId = 1, type = if (currency == Currency.USD) AssetType.US_STOCK else AssetType.A_SHARE,
        name = "s", currency = currency, symbol = "x", quantity = qty, currentPrice = price,
    )

    @Test
    fun sumsAssets_inDisplayCurrency() {
        val holdings = listOf(
            stock(Currency.CNY, 100.0, 10.0), // 1000 CNY
            stock(Currency.USD, 10.0, 10.0),  // 100 USD -> 700 CNY
        )
        val summary = SummarizePortfolio(holdings, rates, Currency.CNY, now)
        assertEquals(1700.0, summary.totalAssets, 0.001)
        assertEquals(1700.0, summary.netWorth, 0.001)
    }

    @Test
    fun subtractsLiabilities() {
        val holdings = listOf(
            stock(Currency.CNY, 100.0, 100.0), // 10000
            Holding(userId = 1, type = AssetType.LIABILITY, name = "贷", currency = Currency.CNY, manualValue = 3000.0),
        )
        val summary = SummarizePortfolio(holdings, rates, Currency.CNY, now)
        assertEquals(10000.0, summary.totalAssets, 0.001)
        assertEquals(3000.0, summary.totalLiabilities, 0.001)
        assertEquals(7000.0, summary.netWorth, 0.001)
    }

    @Test
    fun convertsToUsdDisplay() {
        val holdings = listOf(stock(Currency.CNY, 1.0, 700.0)) // 700 CNY -> 100 USD
        val summary = SummarizePortfolio(holdings, rates, Currency.USD, now)
        assertEquals(100.0, summary.netWorth, 0.001)
    }

    @Test
    fun groupsByCategory() {
        val holdings = listOf(
            stock(Currency.CNY, 100.0, 10.0), // 股票 1000
            Holding(userId = 1, type = AssetType.REAL_ESTATE, name = "房", currency = Currency.CNY, manualValue = 5000.0),
        )
        val summary = SummarizePortfolio(holdings, rates, Currency.CNY, now)
        val stockAmt = summary.byCategory.first { it.category == AssetCategory.STOCK }.amount
        val reAmt = summary.byCategory.first { it.category == AssetCategory.REAL_ESTATE }.amount
        assertEquals(1000.0, stockAmt, 0.001)
        assertEquals(5000.0, reAmt, 0.001)
    }

    @Test
    fun emptyPortfolio_isZero() {
        val summary = SummarizePortfolio(emptyList(), rates, Currency.CNY, now)
        assertEquals(0.0, summary.netWorth, 0.001)
    }
}
