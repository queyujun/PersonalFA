package com.yingjing.pfa.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.Charset
import javax.inject.Inject

/** 新浪股票 / ETF 行情（GBK 文本，需 Referer 头）。 */
class SinaStockRemote @Inject constructor(
    private val client: OkHttpClient,
) : StockQuoteRemote {

    /** 可覆盖的基础 URL（测试用）。 */
    var baseUrl: String = "https://hq.sinajs.cn/list="

    override suspend fun fetch(codes: List<String>): Map<String, Double> {
        if (codes.isEmpty()) return emptyMap()
        val request = Request.Builder()
            .url(baseUrl + codes.joinToString(","))
            .header("Referer", "https://finance.sina.com.cn")
            .header("User-Agent", USER_AGENT)
            .build()
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyMap<String, Double>()
                    val bytes = response.body?.bytes() ?: return@use emptyMap<String, Double>()
                    SinaQuoteParser.parse(String(bytes, GBK))
                }
            }.getOrDefault(emptyMap())
        }
    }

    private companion object {
        val GBK: Charset = Charset.forName("GBK")
        const val USER_AGENT = "Mozilla/5.0"
    }
}
