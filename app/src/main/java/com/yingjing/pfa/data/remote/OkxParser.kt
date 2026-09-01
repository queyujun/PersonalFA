package com.yingjing.pfa.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 解析 OKX `/market/ticker` 响应 → (instId, 最新成交价)。
 *
 * OKX 响应形如：
 * ```
 * {"code":"0","data":[{"instId":"BTC-USDT","last":"78246.1",...}],"msg":""}
 * ```
 * code 非 "0" 或无 data → null（调用方视为该币对不可用）。
 */
object OkxParser {

    @Serializable
    private data class Response(val code: String = "", val data: List<Ticker>? = null)

    @Serializable
    private data class Ticker(val instId: String = "", val last: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    /** 返回 (instId, 最新价)；code != "0" / 无 data / 非法价 → null。 */
    fun parseLast(body: String): Pair<String, Double>? {
        val resp = runCatching { json.decodeFromString<Response>(body) }.getOrNull() ?: return null
        if (resp.code != "0") return null
        val ticker = resp.data?.firstOrNull() ?: return null
        val price = ticker.last.toDoubleOrNull() ?: return null
        return ticker.instId to price
    }
}
