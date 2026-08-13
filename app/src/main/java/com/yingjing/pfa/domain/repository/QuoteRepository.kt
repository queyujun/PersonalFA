package com.yingjing.pfa.domain.repository

import com.yingjing.pfa.domain.model.Holding

/** 行情仓库：为持仓抓取现价。 */
interface QuoteRepository {
    /** 返回 holdingId → 现价（以该持仓自身币种计）。 */
    suspend fun fetchPrices(holdings: List<Holding>): Map<Long, Double>
}
