package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.model.FxRates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.Charset
import javax.inject.Inject

/** 新浪外汇（USD/CNY、HKD/CNY）。 */
class SinaFxRemote @Inject constructor(
    private val client: OkHttpClient,
) : FxRemote {

    override suspend fun fetch(): FxRates {
        val request = Request.Builder()
            .url("https://hq.sinajs.cn/list=fx_susdcny,fx_shkdcny")
            .header("Referer", "https://finance.sina.com.cn")
            .header("User-Agent", "Mozilla/5.0")
            .build()
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use FxRates()
                    val bytes = response.body?.bytes() ?: return@use FxRates()
                    SinaFxParser.toFxRates(String(bytes, Charset.forName("GBK")))
                }
            }.getOrDefault(FxRates())
        }
    }
}
