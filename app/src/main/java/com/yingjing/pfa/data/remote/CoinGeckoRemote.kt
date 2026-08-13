package com.yingjing.pfa.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

/** CoinGecko 加密货币行情（一次返回 cny/hkd/usd 三币种）。 */
class CoinGeckoRemote @Inject constructor(
    private val client: OkHttpClient,
) : CryptoQuoteRemote {

    /** 可覆盖的基础 URL（测试用）。 */
    var baseUrl: String = "https://api.coingecko.com/api/v3/"

    override suspend fun fetch(ids: List<String>): Map<String, Map<String, Double>> {
        if (ids.isEmpty()) return emptyMap()
        val request = Request.Builder()
            .url("${baseUrl}simple/price?ids=${ids.joinToString(",")}&vs_currencies=cny,hkd,usd")
            .header("Accept", "application/json")
            .build()
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyMap<String, Map<String, Double>>()
                    val text = response.body?.string() ?: return@use emptyMap<String, Map<String, Double>>()
                    CoinGeckoParser.parse(text)
                }
            }.getOrDefault(emptyMap())
        }
    }
}
