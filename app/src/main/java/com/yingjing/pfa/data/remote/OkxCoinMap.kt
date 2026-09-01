package com.yingjing.pfa.data.remote

/**
 * CoinGecko coin id → OKX 现货 instId 映射。
 *
 * OKX instId 形如 `BTC-USDT`。CoinGecko id 与交易所 ticker 并不一致（如 bitcoin→BTC、
 * binancecoin→BNB），故内置表覆盖常见不规律命名；表外币种走 `id.uppercase()-USDT`
 * 兜底（对 pepe→PEPE、doge→DOGE 等规律命名成立）。稳定币 USDT 本身不映射（上层特判）。
 */
object OkxCoinMap {

    private val map = mapOf(
        "bitcoin" to "BTC-USDT",
        "ethereum" to "ETH-USDT",
        "binancecoin" to "BNB-USDT",
        "solana" to "SOL-USDT",
        "ripple" to "XRP-USDT",
        "cardano" to "ADA-USDT",
        "dogecoin" to "DOGE-USDT",
        "polkadot" to "DOT-USDT",
        "chainlink" to "LINK-USDT",
        "litecoin" to "LTC-USDT",
        "avalanche-2" to "AVAX-USDT",
        "shiba-inu" to "SHIB-USDT",
        "uniswap" to "UNI-USDT",
        "matic-network" to "MATIC-USDT",
        "tron" to "TRX-USDT",
        "bitcoin-cash" to "BCH-USDT",
        "stellar" to "XLM-USDT",
        "near" to "NEAR-USDT",
        "filecoin" to "FIL-USDT",
        "aptos" to "APT-USDT",
        "arbitrum" to "ARB-USDT",
        "optimism" to "OP-USDT",
        "vechain" to "VET-USDT",
        "internet-computer" to "ICP-USDT",
        "the-graph" to "GRT-USDT",
        "aave" to "AAVE-USDT",
        "cosmos" to "ATOM-USDT",
    )

    /** 返回 OKX instId；稳定币 USDT/Tether 返回 null（上层按 1:1 处理）。 */
    fun instId(coinId: String): String? {
        val key = coinId.lowercase()
        if (key == "usdt" || key == "tether") return null
        map[key]?.let { return it }
        return "${key.uppercase()}-USDT"
    }
}
