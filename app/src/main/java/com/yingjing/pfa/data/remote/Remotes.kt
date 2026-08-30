package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.model.FxRates

/** 股票 / ETF 行情（新浪代码 → 现价）。 */
interface StockQuoteRemote {
    suspend fun fetch(codes: List<String>): Map<String, Double>
}

/** 加密货币行情（币种 id → (小写币种码 → 价格)）。 */
interface CryptoQuoteRemote {
    suspend fun fetch(ids: List<String>): Map<String, Map<String, Double>>
}

/** 汇率。 */
interface FxRemote {
    suspend fun fetch(): FxRates
}

/** 场外基金净值（基金代码 → 单位净值，以基金本身币种计，中国大陆基金通常为 CNY）。 */
interface FundQuoteRemote {
    suspend fun fetch(codes: List<String>): Map<String, Double>
}
