package com.yingjing.pfa.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

/**
 * 东方财富基金估值接口（fundgz）。
 *
 * 接口形如 `https://fundgz.1234567.com.cn/js/{基金代码}.js`，响应为 JSONP：
 * `jsonpgz({"fundcode":"...","dwjz":"...","gsz":"...","gztime":"..."});`
 *
 * 返回值「单位净值」以基金自身币种计（中国大陆基金通常为 CNY），与持仓币种一致，
 * 因此 [QuoteRepositoryImpl] 直接写回 currentPrice 无需换算。
 *
 * 注意：fundgz 域名同时提供 http/https；此处用 https 以适配默认明文禁用配置。
 */
class EastmoneyFundNavRemote @Inject constructor(
    private val client: OkHttpClient,
) : FundQuoteRemote {

    /** 可覆盖的基础 URL（测试用）。 */
    var baseUrl: String = "https://fundgz.1234567.com.cn/js/"

    override suspend fun fetch(codes: List<String>): Map<String, Double> {
        if (codes.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            codes.mapNotNull { code ->
                runCatching {
                    val request = Request.Builder()
                        .url(baseUrl + code + ".js")
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", "https://fund.eastmoney.com/")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val text = response.body?.string() ?: return@use null
                        FundNavParser.parse(text)[code]
                    }
                }.getOrNull()?.let { code to it }
            }.toMap()
        }
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0"
    }
}
