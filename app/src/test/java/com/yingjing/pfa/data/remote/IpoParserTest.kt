package com.yingjing.pfa.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IpoParserTest {

    // 简化的真实 JSON 结构（RPTA_APP_IPOAPPLY）
    private val body = """
        {"version":"x","result":{"pages":10,"data":[
          {"SECURITY_CODE":"601123","APPLY_CODE":"780123","APPLY_DATE":"2026-08-21 00:00:00","SECURITY_NAME":"马矿股份","INDUSTRY_NAME":null},
          {"SECURITY_CODE":"301999","APPLY_CODE":"301999","APPLY_DATE":"2026-08-14 00:00:00","SECURITY_NAME":"瑞康股份"}
        ]},"success":true}
    """.trimIndent()

    @Test
    fun parsesNameCodeAndDate() {
        val items = IpoParser.parse(body)
        assertEquals(2, items.size)
        assertEquals("马矿股份", items[0].name)
        assertEquals("780123", items[0].applyCode)
        assertEquals("2026-08-21", items[0].applyDate) // 截掉时间部分
    }

    @Test
    fun emptyOrNullResult_returnsEmpty() {
        assertTrue(IpoParser.parse("""{"result":null,"success":false}""").isEmpty())
        assertTrue(IpoParser.parse("garbage").isEmpty())
    }
}
