package com.yingjing.pfa.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketIndexParserTest {

    // 真实响应样本（2026-08 实测）
    private val body = """
        var hq_str_sh000300="沪深300,4672.9752,4663.9513,4649.2323,4676.7124,4637.1282,0,0";
        var hq_str_rt_hkHSI="HSI,恒生指数,25219.150,25396.510,25311.860,25116.550,25155.199,-241.310,-0.950,0.000";
        var hq_str_gb_${'$'}inx="标普500指数,7798.9902,0.65,2026-08-14 04:35:25,50.4900";
    """.trimIndent()

    @Test
    fun aShareIndex_computedFromPrevCloseAndCurrent() {
        // (4649.2323 - 4663.9513) / 4663.9513 * 100 ≈ -0.3156%
        val pct = MarketIndexParser.parse(body)["sh000300"]!!
        assertEquals(-0.3156, pct, 0.01)
    }

    @Test
    fun hkIndex_changePercentAtIndex8() {
        assertEquals(-0.950, MarketIndexParser.parse(body)["rt_hkHSI"]!!, 0.001)
    }

    @Test
    fun usIndex_changePercentAtIndex2() {
        assertEquals(0.65, MarketIndexParser.parse(body)["gb_\$inx"]!!, 0.001)
    }

    @Test
    fun emptyPayload_skipped() {
        assertTrue(MarketIndexParser.parse("""var hq_str_sh000001="";""").isEmpty())
    }
}
