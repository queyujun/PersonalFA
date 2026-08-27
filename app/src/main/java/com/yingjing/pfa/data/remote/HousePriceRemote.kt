package com.yingjing.pfa.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

/**
 * 国家统计局 70 城住宅价格指数数据源。批量拉取多城历史。
 */
fun interface HousePriceRemote {
    /** 拉取 [cities] 各城全部可用月份的指数历史；网络/解析失败返回空列表（不抛）。 */
    suspend fun fetch(cities: List<String>): List<HousePricePoint>
}

/**
 * 东方财富实现（reportName=RPT_ECONOMY_HOUSE_PRICE，二手住宅环比口径）。
 * filter 一次请求多城，pageSize=500 取全部历史。
 */
class EastmoneyHousePriceRemote @Inject constructor(
    private val client: OkHttpClient,
) : HousePriceRemote {

    var endpoint: String = "https://datacenter-web.eastmoney.com/api/data/v1/get"

    override suspend fun fetch(cities: List<String>): List<HousePricePoint> {
        if (cities.isEmpty()) return emptyList()
        val urlBuilder = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("reportName", "RPT_ECONOMY_HOUSE_PRICE")
            .addQueryParameter("columns", "ALL")
            .addQueryParameter("source", "WEB")
            .addQueryParameter("client", "WEB")
            .addQueryParameter("pageSize", "500")
            .addQueryParameter("pageNumber", "1")
            .addQueryParameter("sortColumns", "REPORT_DATE")
            .addQueryParameter("sortTypes", "1")
            .addQueryParameter("filter", filterFor(cities))
        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", "https://data.eastmoney.com/")
            .build()
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<HousePricePoint>()
                    val text = response.body?.string() ?: return@use emptyList<HousePricePoint>()
                    HousePriceParser.parse(text)
                }
            }.getOrDefault(emptyList())
        }
    }

    /** (CITY in ("北京","上海")) */
    private fun filterFor(cities: List<String>): String =
        "(CITY in (" + cities.joinToString(",") { "\"$it\"" } + "))"
}
