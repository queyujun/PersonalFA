package com.yingjing.pfa.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OkxParserTest {

    private val okBody = """{"code":"0","data":[{"instId":"BTC-USDT","last":"78246.1","open24h":"77870"}],"msg":""}"""

    @Test
    fun parsesLastPriceFromTicker() {
        val result = OkxParser.parseLast(okBody)
        assertEquals("BTC-USDT", result?.first)
        assertEquals(78246.1, result?.second!!, 0.001)
    }

    @Test
    fun returnsNullWhenCodeNotZero() {
        // code != "0" → 业务失败，返回 null
        val body = """{"code":"50011","data":[],"msg":"invalid instId"}"""
        assertNull(OkxParser.parseLast(body))
    }

    @Test
    fun returnsNullWhenNoData() {
        assertNull(OkxParser.parseLast("""{"code":"0","data":[],"msg":""}"""))
    }

    @Test
    fun returnsNullWhenLastMissingOrInvalid() {
        // last 字段非数字 → null
        val body = """{"code":"0","data":[{"instId":"BTC-USDT","last":""}]}"""
        assertNull(OkxParser.parseLast(body))
    }

    @Test
    fun returnsNullOnMalformedJson() {
        assertNull(OkxParser.parseLast("not a json"))
    }
}
