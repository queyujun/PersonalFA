package com.yingjing.pfa.domain.repository

import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.domain.model.QuoteFetchResult

/** 行情仓库：为持仓抓取现价。 */
interface QuoteRepository {
    /** 返回各数据源并行抓取后的细分结果（价格映射 + 失败的源）。 */
    suspend fun fetchPrices(holdings: List<Holding>): QuoteFetchResult
}
