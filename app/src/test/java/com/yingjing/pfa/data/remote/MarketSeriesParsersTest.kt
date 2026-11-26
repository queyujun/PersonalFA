package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.model.SeriesPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 解析器用的样例均取自 2026-11 实测响应格式（数值截取）。 */
class MarketSeriesParsersTest {

    private fun day(s: String) = LocalDate.parse(s).toEpochDay()

    @Test
    fun `cboe ohlc csv takes close column and sorts ascending`() {
        val csv = """
            DATE,OPEN,HIGH,LOW,CLOSE
            11/24/2026,26.520000,32.380000,25.980000,26.360000
            11/23/2026,31.380000,35.080000,15.390000,26.620000
        """.trimIndent()
        assertEquals(
            listOf(SeriesPoint(day("2026-11-23"), 26.62), SeriesPoint(day("2026-11-24"), 26.36)),
            MarketSeriesParsers.parseCboeCsv(csv),
        )
    }

    @Test
    fun `cboe single column csv parses value`() {
        val csv = "DATE,VVIX\r\n11/23/2026,117.360000\r\n11/24/2026,114.070000\r\n"
        val points = MarketSeriesParsers.parseCboeCsv(csv)
        assertEquals(2, points.size)
        assertEquals(114.07, points.last().value, 1e-9)
    }

    @Test
    fun `cboe csv skips malformed rows, non-positive and non-finite values`() {
        val csv = """
            DATE,VVIX
            not-a-date,10
            11/20/2026,
            11/21/2026,abc
            11/22/2026,0
            11/23/2026,-3
            11/24/2026,NaN
            11/25/2026,Infinity
            11/26/2026,20.5
            garbage
        """.trimIndent()
        assertEquals(listOf(SeriesPoint(day("2026-11-26"), 20.5)), MarketSeriesParsers.parseCboeCsv(csv))
    }

    @Test
    fun `duplicate dates keep the last occurrence`() {
        val csv = "DATE,VIX\n11/24/2026,20\n11/24/2026,21\n"
        assertEquals(listOf(SeriesPoint(day("2026-11-24"), 21.0)), MarketSeriesParsers.parseCboeCsv(csv))
    }

    @Test
    fun `empty or header-only body yields empty list`() {
        assertTrue(MarketSeriesParsers.parseCboeCsv("").isEmpty())
        assertTrue(MarketSeriesParsers.parseCboeCsv("DATE,OPEN,HIGH,LOW,CLOSE\n").isEmpty())
        assertTrue(MarketSeriesParsers.parseCboeCsv("<html>Access Denied</html>").isEmpty())
    }

    @Test
    fun `fred csv skips blank and dot missing values`() {
        val csv = """
            observation_date,VIXCLS
            2026-11-20,35.89
            2026-11-21,
            2026-11-22,.
            2026-11-23,26.62
        """.trimIndent()
        assertEquals(
            listOf(SeriesPoint(day("2026-11-20"), 35.89), SeriesPoint(day("2026-11-23"), 26.62)),
            MarketSeriesParsers.parseFredCsv(csv),
        )
    }

    @Test
    fun `fear greed json maps utc timestamps to days and sorts ascending`() {
        val json = """
            {"name":"Fear and Greed Index","data":[
              {"value":"13","value_classification":"Extreme Fear","timestamp":"1795564800","time_until_update":"55318"},
              {"value":"11","value_classification":"Extreme Fear","timestamp":"1795478400"}
            ],"metadata":{"error":null}}
        """.trimIndent()
        val points = MarketSeriesParsers.parseFearGreedJson(json)
        assertEquals(listOf(1795478400L / 86400, 1795564800L / 86400), points.map { it.epochDay })
        assertEquals(listOf(11.0, 13.0), points.map { it.value })
        assertEquals(LocalDate.of(2026, 11, 25).toEpochDay(), points.last().epochDay)
    }

    @Test
    fun `fear greed json accepts zero and rejects out of range`() {
        val json = """{"data":[
            {"value":"0","timestamp":"86400"},
            {"value":"101","timestamp":"172800"},
            {"value":"-1","timestamp":"259200"},
            {"value":"x","timestamp":"345600"},
            {"value":"50","timestamp":"bad"}
        ]}"""
        assertEquals(listOf(SeriesPoint(1, 0.0)), MarketSeriesParsers.parseFearGreedJson(json))
    }

    @Test
    fun `fear greed invalid json yields empty list`() {
        assertTrue(MarketSeriesParsers.parseFearGreedJson("not json").isEmpty())
        assertTrue(MarketSeriesParsers.parseFearGreedJson("""{"data":null}""").isEmpty())
        assertTrue(MarketSeriesParsers.parseFearGreedJson("[]").isEmpty())
    }

    @Test
    fun `tencent kline reads close at index 2 from day array`() {
        val json = """{"code":0,"msg":"","data":{"us.INX":{"day":[
            ["2026-11-23","7227.410","7237.170","7295.600","7152.660","4245654534.000"],
            ["2026-11-24","7172.330","7201.090","7223.950","7138.930","3323599826.000"]
        ],"qt":{},"prec":"7237.17"}}}"""
        assertEquals(
            listOf(SeriesPoint(day("2026-11-23"), 7237.17), SeriesPoint(day("2026-11-24"), 7201.09)),
            MarketSeriesParsers.parseTencentKline(json, "us.INX"),
        )
    }

    @Test
    fun `tencent kline prefers qfqday when present`() {
        val json = """{"code":0,"data":{"usGLD.AM":{"qfqday":[["2026-11-24","331.430","325.450","332.740","324.900","9525620.000"]]}}}"""
        assertEquals(325.45, MarketSeriesParsers.parseTencentKline(json, "usGLD.AM").single().value, 1e-9)
    }

    @Test
    fun `tencent kline unknown code or wrong key yields empty`() {
        val json = """{"code":0,"data":{"usNOPE.AM":{"day":[],"qt":{}}}}"""
        assertTrue(MarketSeriesParsers.parseTencentKline(json, "usNOPE.AM").isEmpty())
        assertTrue(MarketSeriesParsers.parseTencentKline(json, "us.INX").isEmpty())
        assertTrue(MarketSeriesParsers.parseTencentKline("""{"code":0,"msg":"param error","data":[]}""", "us.INX").isEmpty())
    }

    @Test
    fun `okx candles read close at index 4 and require code 0`() {
        val json = """{"code":"0","data":[
            ["1795564800000","59827.3","59831.5","58632.1","59116.3","2897.3"],
            ["1795478400000","61272.9","61683.9","59236.7","59827.3","7640.8"]
        ],"msg":""}"""
        val points = MarketSeriesParsers.parseOkxCandles(json)
        assertEquals(listOf(59827.3, 59116.3), points.map { it.value })
        assertEquals(LocalDate.of(2026, 11, 25).toEpochDay(), points.last().epochDay)
        assertTrue(MarketSeriesParsers.parseOkxCandles("""{"code":"50011","data":[],"msg":"rate limit"}""").isEmpty())
    }
}
