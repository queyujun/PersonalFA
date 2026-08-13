package com.yingjing.pfa.data.repository

import com.yingjing.pfa.data.remote.CryptoQuoteRemote
import com.yingjing.pfa.data.remote.SinaSymbolMapper
import com.yingjing.pfa.data.remote.StockQuoteRemote
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.repository.QuoteRepository
import javax.inject.Inject

class QuoteRepositoryImpl @Inject constructor(
    private val stockRemote: StockQuoteRemote,
    private val cryptoRemote: CryptoQuoteRemote,
) : QuoteRepository {

    override suspend fun fetchPrices(holdings: List<Holding>): Map<Long, Double> {
        val result = mutableMapOf<Long, Double>()

        // 股票 / ETF（非加密的行情型）：映射到新浪代码后批量抓取
        val marketHoldings = holdings.filter { it.type != AssetType.CRYPTO && it.type.marketPriced }
        val codeToHoldings = marketHoldings
            .mapNotNull { holding -> SinaSymbolMapper.sinaCode(holding)?.let { it to holding } }
            .groupBy({ it.first }, { it.second })
        if (codeToHoldings.isNotEmpty()) {
            val prices = stockRemote.fetch(codeToHoldings.keys.toList())
            codeToHoldings.forEach { (code, list) ->
                prices[code]?.let { price -> list.forEach { result[it.id] = price } }
            }
        }

        // 加密货币：按 CoinGecko id 批量抓取，取该持仓币种价格
        val cryptoHoldings = holdings.filter { it.type == AssetType.CRYPTO && !it.symbol.isNullOrBlank() }
        val ids = cryptoHoldings.mapNotNull { it.symbol?.lowercase() }.distinct()
        if (ids.isNotEmpty()) {
            val prices = cryptoRemote.fetch(ids)
            cryptoHoldings.forEach { holding ->
                val byCurrency = prices[holding.symbol!!.lowercase()] ?: return@forEach
                byCurrency[holding.currency.code.lowercase()]?.let { result[holding.id] = it }
            }
        }
        return result
    }
}
