package com.yingjing.pfa.data.repository

import com.yingjing.pfa.data.remote.CryptoQuoteRemote
import com.yingjing.pfa.data.remote.FundQuoteRemote
import com.yingjing.pfa.data.remote.StockQuoteRemote
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.repository.FxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class QuoteRepositoryImplTest {

    private class FakeStockRemote(private val prices: Map<String, Double>) : StockQuoteRemote {
        var requested: List<String> = emptyList()
        override suspend fun fetch(codes: List<String>): Map<String, Double> {
            requested = codes
            return prices.filterKeys { it in codes }
        }
    }

    private class FakeCryptoRemote(private val prices: Map<String, Map<String, Double>>) : CryptoQuoteRemote {
        override suspend fun fetch(ids: List<String>) = prices.filterKeys { it in ids }
    }

    private class FakeFundRemote(private val navs: Map<String, Double>) : FundQuoteRemote {
        var requested: List<String> = emptyList()
        override suspend fun fetch(codes: List<String>): Map<String, Double> {
            requested = codes
            return navs.filterKeys { it in codes }
        }
    }

    private class FakeFxRepository(private val rates: FxRates) : FxRepository {
        override fun observeRates(): Flow<FxRates> = flowOf(rates)
        override suspend fun current(): FxRates = rates
        override suspend fun refresh(): FxRates = rates
        override suspend fun save(rates: FxRates) {}
    }

    private fun holding(id: Long, type: AssetType, symbol: String, currency: Currency = Currency.CNY) =
        Holding(id = id, userId = 1, type = type, name = "x", currency = currency, symbol = symbol, quantity = 1.0)

    // 实物金：无代码（特殊映射到 hf_XAU），quantity = 克数，costPrice = 每克成本。
    private fun physicalGold(id: Long, grams: Double, currency: Currency = Currency.CNY) =
        Holding(id = id, userId = 1, type = AssetType.PHYSICAL_GOLD, name = "金条", currency = currency, quantity = grams)

    // 场外基金：autoFetchNav=true 走在线抓取，symbol = 基金代码。
    private fun otcFund(id: Long, symbol: String, autoFetchNav: Boolean?, currency: Currency = Currency.CNY) =
        Holding(id = id, userId = 1, type = AssetType.OTC_FUND, name = "基金", currency = currency, symbol = symbol, quantity = 1.0, autoFetchNav = autoFetchNav)

    @Test
    fun mapsStockPrices_byHoldingId() = runTest {
        val stock = FakeStockRemote(mapOf("sh600519" to 1354.5))
        val crypto = FakeCryptoRemote(emptyMap())
        val fund = FakeFundRemote(emptyMap())
        val repo = QuoteRepositoryImpl(stock, crypto, FakeFxRepository(FxRates()), fund)

        val result = repo.fetchPrices(listOf(holding(10, AssetType.A_SHARE, "600519")))
        assertEquals(1354.5, result[10]!!, 0.001)
        assertEquals(listOf("sh600519"), stock.requested)
    }

    @Test
    fun mapsCryptoPrice_byHoldingCurrency() = runTest {
        val stock = FakeStockRemote(emptyMap())
        val crypto = FakeCryptoRemote(mapOf("bitcoin" to mapOf("cny" to 429733.0, "usd" to 63709.0)))
        val fund = FakeFundRemote(emptyMap())
        val repo = QuoteRepositoryImpl(stock, crypto, FakeFxRepository(FxRates()), fund)

        val cnyResult = repo.fetchPrices(listOf(holding(20, AssetType.CRYPTO, "bitcoin", Currency.CNY)))
        assertEquals(429733.0, cnyResult[20]!!, 0.001)

        val usdResult = repo.fetchPrices(listOf(holding(21, AssetType.CRYPTO, "bitcoin", Currency.USD)))
        assertEquals(63709.0, usdResult[21]!!, 0.001)
    }

    @Test
    fun mixedHoldings_bothSourcesQueried() = runTest {
        val stock = FakeStockRemote(mapOf("sh518880" to 9.024))
        val crypto = FakeCryptoRemote(mapOf("ethereum" to mapOf("cny" to 12753.75)))
        val fund = FakeFundRemote(emptyMap())
        val repo = QuoteRepositoryImpl(stock, crypto, FakeFxRepository(FxRates()), fund)

        val result = repo.fetchPrices(
            listOf(
                holding(1, AssetType.GOLD_ETF, "518880"),
                holding(2, AssetType.CRYPTO, "ethereum"),
            ),
        )
        assertEquals(9.024, result[1]!!, 0.001)
        assertEquals(12753.75, result[2]!!, 0.001)
    }

    @Test
    fun manualTypes_ignored() = runTest {
        val fund = FakeFundRemote(emptyMap())
        val repo = QuoteRepositoryImpl(FakeStockRemote(emptyMap()), FakeCryptoRemote(emptyMap()), FakeFxRepository(FxRates()), fund)
        val house = Holding(id = 5, userId = 1, type = AssetType.REAL_ESTATE, name = "房", currency = Currency.CNY, manualValue = 1.0)
        assertEquals(0, repo.fetchPrices(listOf(house)).size)
    }

    @Test
    fun physicalGold_convertsSpotPriceToPerGramInHoldingCurrency() = runTest {
        // 现货金 hf_XAU = 2000 USD/盎司；1 盎司 = 31.1035 克 → USD/克 = 64.3015...
        // CNY 持仓：USD/克 × usdToCny(7.0) = 450.11...；USD 持仓：USD/克 直接 = 64.30...
        val stock = FakeStockRemote(mapOf("hf_XAU" to 2000.0))
        val crypto = FakeCryptoRemote(emptyMap())
        val fund = FakeFundRemote(emptyMap())
        val rates = FxRates(usdToCny = 7.0)
        val repo = QuoteRepositoryImpl(stock, crypto, FakeFxRepository(rates), fund)

        val result = repo.fetchPrices(
            listOf(
                physicalGold(30, 10.0, Currency.CNY), // 10 克，CNY
                physicalGold(31, 5.0, Currency.USD),  // 5 克，USD
            ),
        )
        val usdPerGram = 2000.0 / 31.1035
        // 注意：fetchPrices 返回的是「单位价格」（每克价），而非市值（市值由 holding×quantity 在估值层计算）
        assertEquals(usdPerGram * 7.0, result[30]!!, 0.001)
        assertEquals(usdPerGram, result[31]!!, 0.001)
        // hf_XAU 应被抓取
        assertEquals(listOf("hf_XAU"), stock.requested)
    }

    @Test
    fun physicalGold_skipped_whenNoSpotPrice() = runTest {
        // 现货金抓取失败 → 实物金不写入价格
        val stock = FakeStockRemote(emptyMap())
        val crypto = FakeCryptoRemote(emptyMap())
        val fund = FakeFundRemote(emptyMap())
        val repo = QuoteRepositoryImpl(stock, crypto, FakeFxRepository(FxRates(usdToCny = 7.0)), fund)

        val result = repo.fetchPrices(listOf(physicalGold(40, 10.0)))
        assertEquals(false, result.containsKey(40))
    }

    // —— 场外基金净值：仅 autoFetchNav=true 的「中国大陆」持仓走在线抓取 —— //

    @Test
    fun otcFund_autoFetchNav_fetchesNav_byHoldingSymbol() = runTest {
        val stock = FakeStockRemote(emptyMap())
        val crypto = FakeCryptoRemote(emptyMap())
        val fund = FakeFundRemote(mapOf("005827" to 1.68))
        val repo = QuoteRepositoryImpl(stock, crypto, FakeFxRepository(FxRates()), fund)

        val result = repo.fetchPrices(listOf(otcFund(50, "005827", autoFetchNav = true)))
        assertEquals(1.68, result[50]!!, 0.001)
        assertEquals(listOf("005827"), fund.requested)
    }

    @Test
    fun otcFund_autoFetchNav_skipped_whenNavMissing() = runTest {
        // 抓取返回空 → 不写入价格
        val fund = FakeFundRemote(emptyMap())
        val repo = QuoteRepositoryImpl(FakeStockRemote(emptyMap()), FakeCryptoRemote(emptyMap()), FakeFxRepository(FxRates()), fund)

        val result = repo.fetchPrices(listOf(otcFund(51, "005827", autoFetchNav = true)))
        assertFalse(result.containsKey(51))
    }

    @Test
    fun otcFund_manualRegion_notFetched() = runTest {
        // 「其他」子分类（autoFetchNav=null 或 false）保持手录，不触发在线抓取
        val fund = FakeFundRemote(mapOf("005827" to 1.68))
        val repo = QuoteRepositoryImpl(FakeStockRemote(emptyMap()), FakeCryptoRemote(emptyMap()), FakeFxRepository(FxRates()), fund)

        val resultNull = repo.fetchPrices(listOf(otcFund(52, "005827", autoFetchNav = null)))
        val resultFalse = repo.fetchPrices(listOf(otcFund(53, "005827", autoFetchNav = false)))
        assertFalse(resultNull.containsKey(52))
        assertFalse(resultFalse.containsKey(53))
        assertEquals(emptyList<String>(), fund.requested)
    }
}

