package com.yingjing.pfa.data.repository

import com.yingjing.pfa.data.remote.CryptoQuoteRemote
import com.yingjing.pfa.data.remote.StockQuoteRemote
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Holding
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private fun holding(id: Long, type: AssetType, symbol: String, currency: Currency = Currency.CNY) =
        Holding(id = id, userId = 1, type = type, name = "x", currency = currency, symbol = symbol, quantity = 1.0)

    @Test
    fun mapsStockPrices_byHoldingId() = runTest {
        val stock = FakeStockRemote(mapOf("sh600519" to 1354.5))
        val crypto = FakeCryptoRemote(emptyMap())
        val repo = QuoteRepositoryImpl(stock, crypto)

        val result = repo.fetchPrices(listOf(holding(10, AssetType.A_SHARE, "600519")))
        assertEquals(1354.5, result[10]!!, 0.001)
        assertEquals(listOf("sh600519"), stock.requested)
    }

    @Test
    fun mapsCryptoPrice_byHoldingCurrency() = runTest {
        val stock = FakeStockRemote(emptyMap())
        val crypto = FakeCryptoRemote(mapOf("bitcoin" to mapOf("cny" to 429733.0, "usd" to 63709.0)))
        val repo = QuoteRepositoryImpl(stock, crypto)

        val cnyResult = repo.fetchPrices(listOf(holding(20, AssetType.CRYPTO, "bitcoin", Currency.CNY)))
        assertEquals(429733.0, cnyResult[20]!!, 0.001)

        val usdResult = repo.fetchPrices(listOf(holding(21, AssetType.CRYPTO, "bitcoin", Currency.USD)))
        assertEquals(63709.0, usdResult[21]!!, 0.001)
    }

    @Test
    fun mixedHoldings_bothSourcesQueried() = runTest {
        val stock = FakeStockRemote(mapOf("sh518880" to 9.024))
        val crypto = FakeCryptoRemote(mapOf("ethereum" to mapOf("cny" to 12753.75)))
        val repo = QuoteRepositoryImpl(stock, crypto)

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
        val repo = QuoteRepositoryImpl(FakeStockRemote(emptyMap()), FakeCryptoRemote(emptyMap()))
        val house = Holding(id = 5, userId = 1, type = AssetType.REAL_ESTATE, name = "房", currency = Currency.CNY, manualValue = 1.0)
        assertEquals(0, repo.fetchPrices(listOf(house)).size)
    }
}
