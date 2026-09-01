package com.yingjing.pfa.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 加密货币主备容灾：先调 CoinGecko 主源（原生多币种 CNY/HKD/USD），失败或返回空时
 * fallback 到 OKX 备源（国内可达，USDT 计价 + FX 换算成三币种）。
 *
 * CoinGecko 国内常因网络不可达返回空 map（[CoinGeckoRemote] 内部 runCatching 兜底），
 * 此时若不兜底，[com.yingjing.pfa.data.repository.QuoteRepositoryImpl] 会把 CRYPTO 计入
 * 失败源。本类使主源失败对上层透明——备源补齐后 [CryptoQuoteRemote] 仍返回有效多币种价。
 */
@Singleton
class FallbackCryptoRemote @Inject constructor(
    private val primary: CoinGeckoRemote,
    private val fallback: OkxRemote,
) : CryptoQuoteRemote {

    override suspend fun fetch(ids: List<String>): Map<String, Map<String, Double>> {
        if (ids.isEmpty()) return emptyMap()
        val primaryResult = runCatching { primary.fetch(ids) }
            .onFailure { Log.w(TAG, "CoinGecko primary failed, will try OKX fallback", it) }
            .getOrDefault(emptyMap())
        if (primaryResult.isNotEmpty()) return primaryResult
        // 主源空（国内不可达或无该币种）→ 备源
        return runCatching { fallback.fetch(ids) }
            .onFailure { Log.w(TAG, "OKX fallback failed", it) }
            .getOrDefault(emptyMap())
    }

    private companion object {
        const val TAG = "FallbackCryptoRemote"
    }
}
