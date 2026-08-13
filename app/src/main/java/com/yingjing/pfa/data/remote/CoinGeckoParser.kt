package com.yingjing.pfa.data.remote

import kotlinx.serialization.json.Json

/** 解析 CoinGecko `/simple/price` 响应 → id → (小写币种码 → 价格)。 */
object CoinGeckoParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): Map<String, Map<String, Double>> =
        json.decodeFromString(body)
}
