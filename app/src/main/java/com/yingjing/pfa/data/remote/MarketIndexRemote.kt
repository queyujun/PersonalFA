package com.yingjing.pfa.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.Charset
import javax.inject.Inject

/** 市场指数行情来源（代码 → 当日涨跌%）。 */
fun interface MarketIndexRemote {
    suspend fun fetch(): Map<String, Double>
}

/** 新浪指数实现。 */
class SinaMarketIndexRemote @Inject constructor(
    private val client: OkHttpClient,
) : MarketIndexRemote {

    override suspend fun fetch(): Map<String, Double> {
        val request = Request.Builder()
            .url("https://hq.sinajs.cn/list=" + MarketIndexes.CODES.joinToString(","))
            .header("Referer", "https://finance.sina.com.cn")
            .header("User-Agent", "Mozilla/5.0")
            .build()
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyMap<String, Double>()
                    val bytes = response.body?.bytes() ?: return@use emptyMap<String, Double>()
                    MarketIndexParser.parse(String(bytes, Charset.forName("GBK")))
                }
            }.getOrDefault(emptyMap())
        }
    }
}
