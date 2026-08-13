package com.yingjing.pfa.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class SinaFxParserTest {

    private val body = """
        var hq_str_fx_susdcny="14:41:03,6.7456,6.7458,6.7501,36.0000,6.7441,6.7475,6.7439,6.7470,在岸人民币,-0.0459";
        var hq_str_fx_shkdcny="14:40:57,0.8598,0.8598,0.8595,5.9852,0.8595,0.8599,0.8593,0.8598821961,港元兑人民币,0.0430";
    """.trimIndent()

    @Test
    fun parsesLatestRate_atIndex8() {
        val map = SinaFxParser.parse(body)
        assertEquals(6.7470, map["fx_susdcny"]!!, 0.0001)
        assertEquals(0.8598821961, map["fx_shkdcny"]!!, 0.0000001)
    }

    @Test
    fun toFxRates_mapsBothPairs() {
        val rates = SinaFxParser.toFxRates(body)
        assertEquals(6.7470, rates.usdToCny, 0.0001)
        assertEquals(0.8598821961, rates.hkdToCny, 0.0000001)
    }
}
