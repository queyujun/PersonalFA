package com.yingjing.pfa.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SinaQuoteParserTest {

    // 真实响应样本（2026-08 实测）
    private val body = """
        var hq_str_sh600519="贵州茅台,1338.000,1343.000,1354.500,1359.600,1337.000,1354.380,1354.580,2847090,3849970975.000";
        var hq_str_rt_hk00700="TENCENT,腾讯控股,446.400,461.600,452.200,441.000,441.400,-20.200,-4.376";
        var hq_str_gb_aapl="苹果,302.2500,-0.87,2026-08-13 09:30:15,-2.6600,305.1000";
        var hq_str_sh518880="黄金ETF华安,9.126,9.103,9.024,9.144,9.016";
    """.trimIndent()

    @Test
    fun parsesAShare_price_atIndex3() {
        assertEquals(1354.5, SinaQuoteParser.parse(body)["sh600519"]!!, 0.001)
    }

    @Test
    fun parsesHkStock_price_atIndex6() {
        assertEquals(441.4, SinaQuoteParser.parse(body)["rt_hk00700"]!!, 0.001)
    }

    @Test
    fun parsesUsStock_price_atIndex1() {
        assertEquals(302.25, SinaQuoteParser.parse(body)["gb_aapl"]!!, 0.001)
    }

    @Test
    fun parsesEtf_asAShare() {
        assertEquals(9.024, SinaQuoteParser.parse(body)["sh518880"]!!, 0.001)
    }

    @Test
    fun skipsEmptyPayload() {
        val result = SinaQuoteParser.parse("""var hq_str_sh600000="";""")
        assertTrue(result.isEmpty())
    }

    @Test
    fun skipsMalformed() {
        assertFalse(SinaQuoteParser.parse("garbage").containsKey("x"))
    }
}
