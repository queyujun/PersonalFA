package com.yingjing.pfa.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class CoinGeckoParserTest {

    private val body =
        """{"bitcoin":{"cny":429733,"hkd":499869,"usd":63709},"ethereum":{"cny":12753.75,"hkd":14835.28,"usd":1890.79}}"""

    @Test
    fun parsesMultiCurrencyPrices() {
        val result = CoinGeckoParser.parse(body)
        assertEquals(429733.0, result["bitcoin"]!!["cny"]!!, 0.001)
        assertEquals(63709.0, result["bitcoin"]!!["usd"]!!, 0.001)
        assertEquals(1890.79, result["ethereum"]!!["usd"]!!, 0.001)
    }
}
