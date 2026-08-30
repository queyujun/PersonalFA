package com.yingjing.pfa.data.repository

import com.yingjing.pfa.data.remote.CryptoQuoteRemote
import com.yingjing.pfa.data.remote.FundQuoteRemote
import com.yingjing.pfa.data.remote.SinaSymbolMapper
import com.yingjing.pfa.data.remote.StockQuoteRemote
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.repository.FxRepository
import com.yingjing.pfa.domain.repository.QuoteRepository
import javax.inject.Inject

class QuoteRepositoryImpl @Inject constructor(
    private val stockRemote: StockQuoteRemote,
    private val cryptoRemote: CryptoQuoteRemote,
    private val fxRepository: FxRepository,
    private val fundRemote: FundQuoteRemote,
) : QuoteRepository {

    override suspend fun fetchPrices(holdings: List<Holding>): Map<Long, Double> {
        val result = mutableMapOf<Long, Double>()

        // 股票 / ETF（非加密、非实物金的行情型）：映射到新浪代码后批量抓取
        val marketHoldings = holdings.filter {
            it.type != AssetType.CRYPTO && it.type != AssetType.PHYSICAL_GOLD && it.type.marketPriced
        }
        val codeToHoldings = marketHoldings
            .mapNotNull { holding -> SinaSymbolMapper.sinaCode(holding)?.let { it to holding } }
            .groupBy({ it.first }, { it.second })

        // 实物金：统一取新浪伦敦金现货 hf_XAU（USD/盎司），
        // 抓取后按持仓币种把「每盎司 USD」换算为「每克该币种」价。
        val physicalGold = holdings.filter { it.type == AssetType.PHYSICAL_GOLD }
        val codes = codeToHoldings.keys.toMutableList()
            .apply { if (physicalGold.isNotEmpty()) add(SPOT_GOLD) }
        if (codes.isNotEmpty()) {
            val prices = stockRemote.fetch(codes)
            codeToHoldings.forEach { (code, list) ->
                prices[code]?.let { price -> list.forEach { result[it.id] = price } }
            }
            val spotUsdPerOunce = prices[SPOT_GOLD]
            if (spotUsdPerOunce != null && spotUsdPerOunce > 0 && physicalGold.isNotEmpty()) {
                val rates = fxRepository.current()
                physicalGold.forEach { holding ->
                    val usdPerGram = spotUsdPerOunce / OUNCE_TO_GRAM
                    result[holding.id] = rates.convert(usdPerGram, Currency.USD, holding.currency)
                }
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

        // 场外基金（中国大陆，autoFetchNav=true）：按基金代码抓取单位净值写回。
        // 仅 autoFetchNav=true 的持仓走在线抓取；「其他」子分类（null/false）保持手录，不在此处理。
        // 净值以基金自身币种计（中国大陆基金通常 CNY），与持仓币种一致，直接写回无需换算。
        val fundHoldings = holdings.filter {
            it.type == AssetType.OTC_FUND && it.autoFetchNav == true && !it.symbol.isNullOrBlank()
        }
        val fundCodes = fundHoldings.mapNotNull { it.symbol }.distinct()
        if (fundCodes.isNotEmpty()) {
            val navs = fundRemote.fetch(fundCodes)
            fundHoldings.forEach { holding ->
                navs[holding.symbol]?.let { result[holding.id] = it }
            }
        }
        return result
    }

    private companion object {
        // 新浪伦敦金现货代码（USD/盎司）
        const val SPOT_GOLD = "hf_XAU"
        // 金衡盎司 → 克
        const val OUNCE_TO_GRAM = 31.1035
    }
}
