package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.repository.FxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

/**
 * OKX 现货行情备源：CoinGecko 国内不可达时的 fallback。
 *
 * OKX `/market/ticker` 仅支持单个 instId 查询，故按持仓币种并行抓取；返回 USDT 计价的
 * 最新成交价，再用 [FxRepository] 把 USDT(≈USD) 价换算成 CNY/HKD/USD 三币种，对齐
 * [CryptoQuoteRemote] 契约（coin id → (小写币种码 → 价格)），使 [com.yingjing.pfa.data.repository.QuoteRepositoryImpl] 无感。
 *
 * coin id → OKX instId 由 [OkxCoinMap] 完成；稳定币 USDT 跳过（按 1:1 由调用方处理）。
 */
class OkxRemote @Inject constructor(
    private val client: OkHttpClient,
    private val fxRepository: FxRepository,
) : CryptoQuoteRemote {

    /** 可覆盖的基础 URL（测试用）。 */
    var baseUrl: String = "https://www.okx.com/api/v5/"

    override suspend fun fetch(ids: List<String>): Map<String, Map<String, Double>> {
        if (ids.isEmpty()) return emptyMap()
        // coin id → OKX instId；稳定币/不支持 → null 过滤
        val idToInst = ids.mapNotNull { id -> OkxCoinMap.instId(id)?.let { id to it } }
        if (idToInst.isEmpty()) return emptyMap()
        val rates = runCatching { fxRepository.current() }.getOrDefault(com.yingjing.pfa.domain.model.FxRates())
        return withContext(Dispatchers.IO) {
            coroutineScope {
                idToInst.map { (coinId, instId) ->
                    async { coinId to fetchOne(instId, rates) }
                }.awaitAll()
                    .mapNotNull { (coinId, usdPrice) ->
                        usdPrice?.let { coinId to toMultiCurrency(it, rates) }
                    }.toMap()
            }
        }
    }

    /** 抓单个 instId 的最新价（USDT 计价）；失败/无数据返回 null。 */
    private suspend fun fetchOne(instId: String, rates: com.yingjing.pfa.domain.model.FxRates): Double? {
        val request = Request.Builder()
            .url("${baseUrl}market/ticker?instId=$instId")
            .header("Accept", "application/json")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val text = response.body?.string() ?: return@use null
                OkxParser.parseLast(text)?.second
            }
        }.getOrNull()
    }

    /** USDT(≈USD) 价 → {cny, hkd, usd}；FX 为默认 1.0 时三币种退化为同值（降级，不崩）。 */
    private fun toMultiCurrency(
        usdPrice: Double,
        rates: com.yingjing.pfa.domain.model.FxRates,
    ): Map<String, Double> = mapOf(
        "cny" to rates.convert(usdPrice, Currency.USD, Currency.CNY),
        "hkd" to rates.convert(usdPrice, Currency.USD, Currency.HKD),
        "usd" to usdPrice,
    )
}
