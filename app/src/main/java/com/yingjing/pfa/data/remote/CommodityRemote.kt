package com.yingjing.pfa.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.Charset
import javax.inject.Inject

/** 国际贵金属行情来源（代码 → 当日涨跌%）。 */
fun interface CommodityRemote {
    suspend fun fetch(): Map<String, Double>
}

/** 新浪外盘现货实现（伦敦金 / 伦敦银）。 */
class SinaCommodityRemote @Inject constructor(
    private val client: OkHttpClient,
) : CommodityRemote {

    override suspend fun fetch(): Map<String, Double> {
        val request = Request.Builder()
            .url("https://hq.sinajs.cn/list=" + GlobalIndicators.METAL_CODES.joinToString(","))
            .header("Referer", "https://finance.sina.com.cn")
            .header("User-Agent", "Mozilla/5.0")
            .build()
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyMap<String, Double>()
                    val bytes = response.body?.bytes() ?: return@use emptyMap<String, Double>()
                    CommodityParser.parse(String(bytes, Charset.forName("GBK")))
                }
            }.getOrDefault(emptyMap())
        }
    }
}
