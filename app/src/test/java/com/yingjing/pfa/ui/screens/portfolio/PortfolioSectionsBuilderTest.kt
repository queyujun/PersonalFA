package com.yingjing.pfa.ui.screens.portfolio

import com.yingjing.pfa.domain.model.AssetCategory
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.fakes.FakeStringResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioSectionsBuilderTest {

    private val now = 1_700_000_000_000L
    private val resolver = FakeStringResolver()

    // StockSub.name 为 locale 无关的稳定折叠键；声明顺序即展示顺序。
    private val stockSubKeys = listOf(
        "US_STOCK", "US_CASH", "A_SHARE", "A_CASH", "HK_STOCK", "HK_CASH",
    )

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
        val state = PortfolioSectionsBuilder.build(holdings, FxRates(), Currency.CNY, now, resolver)
        val stock = state.sections.first { it.category == AssetCategory.STOCK }
        assertEquals(stockSubKeys, stock.subGroups.map { it.subKey })
    }

    @Test
    fun sortsByValueDescendingWithinGroup() {
        val small = aShare("小", 1.0, 100.0) // 市值 100
        val big = aShare("大", 10.0, 100.0) // 市值 1000
        val state = PortfolioSectionsBuilder.build(listOf(small, big), FxRates(), Currency.CNY, now, resolver)
        val aGroup = state.sections.first { it.category == AssetCategory.STOCK }
            .subGroups.first { it.subKey == "A_SHARE" }
        assertEquals(listOf("大", "小"), aGroup.rows.map { it.name })
    }

    @Test
    fun depositGroupSortsAcrossCurrenciesByConvertedValue() {
        val rates = FxRates(usdToCny = 7.0)
        // 100 USD = 700 CNY > 500 CNY，跨币种换算后美元存更大
        val usdDep = Holding(userId = 1, type = AssetType.DEPOSIT, name = "美元存", currency = Currency.USD, manualValue = 100.0, annualRatePercent = 0.0, startDateEpochMs = now)
        val cnyDep = Holding(userId = 1, type = AssetType.DEPOSIT, name = "人民币存", currency = Currency.CNY, manualValue = 500.0, annualRatePercent = 0.0, startDateEpochMs = now)
        val state = PortfolioSectionsBuilder.build(listOf(cnyDep, usdDep), rates, Currency.CNY, now, resolver)
        val dep = state.sections.first { it.category == AssetCategory.DEPOSIT }.subGroups.first()
        assertEquals(listOf("美元存", "人民币存"), dep.rows.map { it.name })
    }

    @Test
    fun nonStockCategoryHasSingleUntitledGroup() {
        val small = Holding(userId = 1, type = AssetType.REAL_ESTATE, name = "小房", currency = Currency.CNY, manualValue = 100.0)
        val big = Holding(userId = 1, type = AssetType.REAL_ESTATE, name = "大房", currency = Currency.CNY, manualValue = 999.0)
        val state = PortfolioSectionsBuilder.build(listOf(small, big), FxRates(), Currency.CNY, now, resolver)
        val re = state.sections.first { it.category == AssetCategory.REAL_ESTATE }
        assertEquals(1, re.subGroups.size)
        assertNull(re.subGroups.first().title)
        assertEquals(listOf("大房", "小房"), re.subGroups.first().rows.map { it.name })
    }

    @Test
    fun otcFundFormsSingleUntitledGroupSortedByValueDescending() {
        // 1000 份 × 1.68 = 1680；100 份 × 1.50 = 150
        val big = Holding(userId = 1, type = AssetType.OTC_FUND, name = "大基金", currency = Currency.CNY, symbol = "005827", quantity = 1_000.0, costPrice = 1.50, currentPrice = 1.68)
        val small = Holding(userId = 1, type = AssetType.OTC_FUND, name = "小基金", currency = Currency.CNY, symbol = "110011", quantity = 100.0, costPrice = 1.50, currentPrice = 1.50)
        val state = PortfolioSectionsBuilder.build(listOf(small, big), FxRates(), Currency.CNY, now, resolver)
        val otc = state.sections.first { it.category == AssetCategory.OTC_FUND }
        // 场外基金走非股票分支：单组、无子标题
        assertEquals(1, otc.subGroups.size)
        assertNull(otc.subGroups.first().title)
        assertEquals(listOf("大基金", "小基金"), otc.subGroups.first().rows.map { it.name })
    }

    @Test
    fun otcFundCategoryAppearsAfterCryptoAndBeforeLiability() {
        val holdings = listOf(
            Holding(userId = 1, type = AssetType.CRYPTO, name = "BTC", currency = Currency.CNY, symbol = "bitcoin", quantity = 1.0, costPrice = 1.0, currentPrice = 1.0),
            Holding(userId = 1, type = AssetType.OTC_FUND, name = "基金", currency = Currency.CNY, symbol = "005827", quantity = 1.0, costPrice = 1.0, currentPrice = 1.0),
            Holding(userId = 1, type = AssetType.LIABILITY, name = "房贷", currency = Currency.CNY, manualValue = 1.0),
        )
        val state = PortfolioSectionsBuilder.build(holdings, FxRates(), Currency.CNY, now, resolver)
        val order = state.sections.map { it.category }
        val cryptoIdx = order.indexOf(AssetCategory.CRYPTO)
        val otcIdx = order.indexOf(AssetCategory.OTC_FUND)
        val liabIdx = order.indexOf(AssetCategory.LIABILITY)
        assertTrue("CRYPTO 应在 OTC_FUND 之前", cryptoIdx < otcIdx)
        assertTrue("OTC_FUND 应在 LIABILITY 之前", otcIdx < liabIdx)
    }

    // —— 黄金：类目内再分 黄金ETF / 实物金 两个子分组，声明顺序即展示顺序 ——

    private val goldSubKeys = listOf("GOLD_ETF", "PHYSICAL_GOLD")

    private fun goldEtf(name: String, qty: Double, price: Double) = Holding(
        userId = 1, type = AssetType.GOLD_ETF, name = name, currency = Currency.CNY,
        symbol = "518880", quantity = qty, costPrice = price, currentPrice = price,
    )

    private fun physicalGold(name: String, grams: Double, price: Double) = Holding(
        userId = 1, type = AssetType.PHYSICAL_GOLD, name = name, currency = Currency.CNY,
        quantity = grams, costPrice = price, currentPrice = price,
    )

    @Test
    fun goldSplitsIntoTwoSubGroupsInDeclaredOrder() {
        val holdings = listOf(
            physicalGold("金条", 10.0, 450.0), // 4500
            goldEtf("黄金ETF", 100.0, 9.0),   // 900
        )
        val state = PortfolioSectionsBuilder.build(holdings, FxRates(), Currency.CNY, now, resolver)
        val gold = state.sections.first { it.category == AssetCategory.GOLD }
        assertEquals(goldSubKeys, gold.subGroups.map { it.subKey })
    }

    @Test
    fun goldSubGroupsSortRowsByValueDescending() {
        val holdings = listOf(
            physicalGold("小金", 1.0, 450.0),   // 450
            physicalGold("大金", 10.0, 450.0),  // 4500
            goldEtf("小ETF", 1.0, 9.0),        // 9
            goldEtf("大ETF", 100.0, 9.0),      // 900
        )
        val state = PortfolioSectionsBuilder.build(holdings, FxRates(), Currency.CNY, now, resolver)
        val gold = state.sections.first { it.category == AssetCategory.GOLD }
        val etfGroup = gold.subGroups.first { it.subKey == "GOLD_ETF" }
        val physicalGroup = gold.subGroups.first { it.subKey == "PHYSICAL_GOLD" }
        assertEquals(listOf("大ETF", "小ETF"), etfGroup.rows.map { it.name })
        assertEquals(listOf("大金", "小金"), physicalGroup.rows.map { it.name })
    }

    // —— 其他(MISC) 类目：单组无子标题，且排在负债之前 ——

    @Test
    fun miscCategoryAppearsAfterOtcFundAndBeforeLiability() {
        val holdings = listOf(
            Holding(userId = 1, type = AssetType.OTC_FUND, name = "基金", currency = Currency.CNY, symbol = "005827", quantity = 1.0, costPrice = 1.0, currentPrice = 1.0),
            Holding(userId = 1, type = AssetType.MISC, name = "收藏品", currency = Currency.CNY, manualValue = 5_000.0, note = "老物件"),
            Holding(userId = 1, type = AssetType.LIABILITY, name = "房贷", currency = Currency.CNY, manualValue = 1.0),
        )
        val state = PortfolioSectionsBuilder.build(holdings, FxRates(), Currency.CNY, now, resolver)
        val order = state.sections.map { it.category }
        val otcIdx = order.indexOf(AssetCategory.OTC_FUND)
        val miscIdx = order.indexOf(AssetCategory.MISC)
        val liabIdx = order.indexOf(AssetCategory.LIABILITY)
        assertTrue("OTC_FUND 应在 MISC 之前", otcIdx < miscIdx)
        assertTrue("MISC 应在 LIABILITY 之前", miscIdx < liabIdx)
    }

    @Test
    fun miscSubgroupHasNoTitleAndShowsNoteInSubtitle() {
        val holdings = listOf(
            Holding(userId = 1, type = AssetType.MISC, name = "收藏品", currency = Currency.CNY, manualValue = 5_000.0, note = "老物件"),
        )
        val state = PortfolioSectionsBuilder.build(holdings, FxRates(), Currency.CNY, now, resolver)
        val misc = state.sections.first { it.category == AssetCategory.MISC }
        assertEquals(1, misc.subGroups.size)
        assertNull(misc.subGroups.first().title)
        // 副标题取 note（FakeStringResolver 无参返回 "res<id>"，有参返回空格连接参数 → note 原文）
        assertEquals("老物件", misc.subGroups.first().rows.first().subtitle)
    }
}
