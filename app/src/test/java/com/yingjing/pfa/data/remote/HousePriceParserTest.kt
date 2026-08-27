package com.yingjing.pfa.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HousePriceParserTest {

    @Test
    fun parse_extractsPoints_withNullFields() {
        val body = """
            {"result":{"data":[
              {"REPORT_DATE":"2026-07-01 00:00:00","CITY":"北京",
               "FIRST_COMHOUSE_SEQUENTIAL":99.7,"FIRST_COMHOUSE_SAME":97.7,
               "SECOND_HOUSE_SEQUENTIAL":100.0,"SECOND_HOUSE_SAME":95.5},
              {"REPORT_DATE":"2026-06-01 00:00:00","CITY":"北京",
               "FIRST_COMHOUSE_SEQUENTIAL":null,"FIRST_COMHOUSE_SAME":null,
               "SECOND_HOUSE_SEQUENTIAL":99.9,"SECOND_HOUSE_SAME":null}
            ]}}
        """.trimIndent()
        val points = HousePriceParser.parse(body)
        assertEquals(2, points.size)
        val jul = points.first { it.month == "2026-07" }
        assertEquals("北京", jul.city)
        assertEquals(100.0, jul.secondSequential!!, 0.001)
        assertEquals(95.5, jul.secondSame!!, 0.001)
        val jun = points.first { it.month == "2026-06" }
        assertEquals(99.9, jun.secondSequential!!, 0.001)
        assertEquals(null, jun.newSequential)
        assertEquals(null, jun.secondSame)
    }

    @Test
    fun parse_multiCity() {
        val body = """
            {"result":{"data":[
              {"REPORT_DATE":"2026-07-01 00:00:00","CITY":"北京","SECOND_HOUSE_SEQUENTIAL":100.1},
              {"REPORT_DATE":"2026-07-01 00:00:00","CITY":"上海","SECOND_HOUSE_SEQUENTIAL":99.8}
            ]}}
        """.trimIndent()
        val cities = HousePriceParser.parse(body).map { it.city }.toSet()
        assertEquals(setOf("北京", "上海"), cities)
    }

    @Test
    fun parse_emptyResult_returnsEmpty() {
        assertEquals(emptyList<HousePricePoint>(), HousePriceParser.parse("""{"result":{"data":[]}}"""))
    }

    @Test
    fun parse_malformedBody_returnsEmpty() {
        assertTrue(HousePriceParser.parse("not json").isEmpty())
        assertTrue(HousePriceParser.parse("""{"result":null}""").isEmpty())
    }
}
