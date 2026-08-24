package com.yingjing.pfa.ui.screens.portfolio

import com.yingjing.pfa.domain.model.AssetCategory
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.Holding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortfolioSectionsBuilderTest {

    private val now = 1_700_000_000_000L

    private fun cash(cur: Currency, amount: Double, name: String) = Holding(
        userId = 1, type = AssetType.ACCOUNT_CASH, name = name, currency = cur, manualValue = amount,
    )

    private fun aShare(name: String, qty: Double, price: Double) = Holding(
        userId = 1, type = AssetType.A_SHARE, name = name, currency = Currency.CNY,
        symbol = "600519", quantity = qty, costPrice = price, currentPrice = price,
    )

    @Test
    fun stockSplitsIntoSixMarketSubGroupsInDeclaredOrder() {
        val holdings = listOf(
            cash(Currency.USD, 100.0, "美现"),
            aShare("茅台", 10.0, 100.0),
            cash(Currency.CNY, 200.0, "A现"),
            Holding(userId = 1, type = AssetType.HK_STOCK, name = "腾讯", currency = Currency.HKD, symbol = "00700", quantity = 5.0, costPrice = 300.0, currentPrice = 300.0),
            cash(Currency.HKD, 50.0, "港现"),
            Holding(userId = 1, type = AssetType.US_STOCK, name = "苹果", currency = Currency.USD, symbol = "AAPL", quantity = 2.0, costPrice = 150.0, currentPrice = 150.0),
        )
        val state = PortfolioSectionsBuilder.build(holdings, FxRates(), Currency.CNY, now)
        val stock = state.sections.first { it.category == AssetCategory.STOCK }
        assertEquals(
            listOf("美股", "美股现金", "A股", "A股现金", "港股", "港股现金"),
            stock.subGroups.map { it.title },
        )
    }

    @Test
    fun sortsByValueDescendingWithinGroup() {
        val small = aShare("小", 1.0, 100.0) // 市值 100
        val big = aShare("大", 10.0, 100.0) // 市值 1000
        val state = PortfolioSectionsBuilder.build(listOf(small, big), FxRates(), Currency.CNY, now)
        val aGroup = state.sections.first { it.category == AssetCategory.STOCK }
            .subGroups.first { it.title == "A股" }
        assertEquals(listOf("大", "小"), aGroup.rows.map { it.name })
    }

    @Test
    fun depositGroupSortsAcrossCurrenciesByConvertedValue() {
        val rates = FxRates(usdToCny = 7.0)
        // 100 USD = 700 CNY > 500 CNY，跨币种换算后美元存更大
        val usdDep = Holding(userId = 1, type = AssetType.DEPOSIT, name = "美元存", currency = Currency.USD, manualValue = 100.0, annualRatePercent = 0.0, startDateEpochMs = now)
        val cnyDep = Holding(userId = 1, type = AssetType.DEPOSIT, name = "人民币存", currency = Currency.CNY, manualValue = 500.0, annualRatePercent = 0.0, startDateEpochMs = now)
        val state = PortfolioSectionsBuilder.build(listOf(cnyDep, usdDep), rates, Currency.CNY, now)
        val dep = state.sections.first { it.category == AssetCategory.DEPOSIT }.subGroups.first()
        assertEquals(listOf("美元存", "人民币存"), dep.rows.map { it.name })
    }

    @Test
    fun nonStockCategoryHasSingleUntitledGroup() {
        val small = Holding(userId = 1, type = AssetType.REAL_ESTATE, name = "小房", currency = Currency.CNY, manualValue = 100.0)
        val big = Holding(userId = 1, type = AssetType.REAL_ESTATE, name = "大房", currency = Currency.CNY, manualValue = 999.0)
        val state = PortfolioSectionsBuilder.build(listOf(small, big), FxRates(), Currency.CNY, now)
        val re = state.sections.first { it.category == AssetCategory.REAL_ESTATE }
        assertEquals(1, re.subGroups.size)
        assertNull(re.subGroups.first().title)
        assertEquals(listOf("大房", "小房"), re.subGroups.first().rows.map { it.name })
    }
}
