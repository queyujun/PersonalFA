package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.alert.NewsItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 快讯 JSON 解析（内嵌实测结构样本）。 */
class NewsParserTest {

    // ---- 华尔街见闻 ----

    @Test
    fun parseWallstcn_fullItem_mapsAllFields() {
        val body = """
            {"code":20000,"data":{"items":[
              {"id":3162302,"title":"","content_text":"美联储宣布降息25个基点，为今年首次降息。",
               "display_time":1757358000,"score":2}
            ]}}
        """.trimIndent()
        val items = NewsParser.parseWallstcn(body)
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("wscn_3162302", item.id)
        // title 为空 → 回退正文截断
        assertEquals("美联储宣布降息25个基点，为今年首次降息。", item.title)
        assertEquals("美联储宣布降息25个基点，为今年首次降息。", item.contentText)
        // display_time Unix 秒 → ms
        assertEquals(1_757_358_000_000L, item.timeEpochMs)
        // score >= 2 → important
        assertTrue(item.important)
    }

    @Test
    fun parseWallstcn_nonEmptyTitle_kept() {
        val body = """
            {"code":20000,"data":{"items":[
              {"id":3162303,"title":"美联储降息","content_text":"正文内容",
               "display_time":1757358000,"score":1}
            ]}}
        """.trimIndent()
        val items = NewsParser.parseWallstcn(body)
        assertEquals(1, items.size)
        assertEquals("美联储降息", items[0].title)
        assertFalse(items[0].important) // score 1 < 2
    }

    @Test
    fun parseWallstcn_invalidItems_skipped() {
        val body = """
            {"code":20000,"data":{"items":[
              {"id":null,"title":"无id","content_text":"x","display_time":1757358000,"score":1},
              {"id":1,"title":"无正文","content_text":"  ","display_time":1757358000,"score":1},
              {"id":2,"title":"无时间","content_text":"x","display_time":null,"score":1},
              {"id":3,"title":"正常","content_text":"x","display_time":1757358000,"score":1}
            ]}}
        """.trimIndent()
        val items = NewsParser.parseWallstcn(body)
        assertEquals(listOf("wscn_3"), items.map { it.id })
    }

    @Test
    fun parseWallstcn_malformedJson_returnsEmpty() {
        assertTrue(NewsParser.parseWallstcn("not json").isEmpty())
        assertTrue(NewsParser.parseWallstcn("{\"code\":500}").isEmpty())
    }

    @Test
    fun parseWallstcn_titleFallbackTruncatedTo40() {
        val longContent = "字".repeat(60)
        val body = """
            {"code":20000,"data":{"items":[
              {"id":3162304,"title":"","content_text":"$longContent",
               "display_time":1757358000,"score":1}
            ]}}
        """.trimIndent()
        val items = NewsParser.parseWallstcn(body)
        assertEquals(40, items[0].title.length)
        assertEquals(60, items[0].contentText.length)
    }

    // ---- 东方财富 ----

    @Test
    fun parseEastmoney_fullItem_mapsAllFields() {
        val body = """
            {"code":"1","data":{"fastNewsList":[
              {"title":"美联储宣布降息","summary":"美联储宣布降息25个基点。",
               "showTime":"2026-09-09 10:30:00","code":"202609091030123456","realSort":1757358600000}
            ]}}
        """.trimIndent()
        val items = NewsParser.parseEastmoney(body)
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("em_202609091030123456", item.id)
        assertEquals("美联储宣布降息", item.title)
        assertEquals("美联储宣布降息25个基点。", item.contentText)
        // showTime 按北京时间解析
        val expectedMs = java.time.LocalDateTime
            .parse("2026-09-09T10:30:00")
            .atZone(java.time.ZoneId.of("Asia/Shanghai"))
            .toInstant().toEpochMilli()
        assertEquals(expectedMs, item.timeEpochMs)
        // 东财恒不重要
        assertFalse(item.important)
    }

    @Test
    fun parseEastmoney_missingShowTime_fallsBackToRealSort() {
        val body = """
            {"code":"1","data":{"fastNewsList":[
              {"title":"无时间","summary":"正文","showTime":"","code":"c1","realSort":1757358600000}
            ]}}
        """.trimIndent()
        val items = NewsParser.parseEastmoney(body)
        assertEquals(1, items.size)
        assertEquals(1_757_358_600_000L, items[0].timeEpochMs)
    }

    @Test
    fun parseEastmoney_invalidItems_skipped() {
        val body = """
            {"code":"1","data":{"fastNewsList":[
              {"title":"无code","summary":"x","showTime":"2026-09-09 10:30:00","code":null},
              {"title":"无正文","summary":"  ","showTime":"2026-09-09 10:30:00","code":"c2"},
              {"title":"无时间","summary":"x","showTime":null,"code":"c3","realSort":null},
              {"title":"正常","summary":"x","showTime":"2026-09-09 10:30:00","code":"c4"}
            ]}}
        """.trimIndent()
        val items = NewsParser.parseEastmoney(body)
        assertEquals(listOf("em_c4"), items.map { it.id })
    }

    @Test
    fun parseEastmoney_malformedJson_returnsEmpty() {
        assertTrue(NewsParser.parseEastmoney("not json").isEmpty())
        assertTrue(NewsParser.parseEastmoney("{\"code\":\"0\"}").isEmpty())
    }
}
