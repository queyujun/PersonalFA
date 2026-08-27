package com.yingjing.pfa.data.remote

/**
 * 单条房价指数数据点（来自东方财富 RPT_ECONOMY_HOUSE_PRICE，国家统计局 70 城口径）。
 *
 * - [month]：报告月份 "yyyy-MM"（取自 REPORT_DATE 的日期部分）。
 * - [newSequential]：新建商品住宅价格环比指数（100=持平，>100 环比涨）。
 * - [newSame]：新建同比。
 * - [secondSequential]：二手住宅价格环比指数（**估算采用**）。
 * - [secondSame]：二手同比。
 */
data class HousePricePoint(
    val city: String,
    val month: String,
    val newSequential: Double?,
    val newSame: Double?,
    val secondSequential: Double?,
    val secondSame: Double?,
)
