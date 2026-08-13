package com.yingjing.pfa.domain.model

/** 净值走势上的一个点（某自然日的净值）。 */
data class NetWorthPoint(
    val epochDay: Long,
    val netWorth: Double,
)
