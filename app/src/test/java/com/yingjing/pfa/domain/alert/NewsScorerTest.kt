package com.yingjing.pfa.domain.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 快讯关键词打分与筛选（纯函数）。 */
class NewsScorerTest {

    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    private fun item(
        id: String,
        title: String,
        content: String = "",
        ageMs: Long = hour,
        important: Boolean = false,
    ) = NewsItem(id, title, content, now - ageMs, important)

    @Test
    fun score_twoSevereWords_hitsSeriousThreshold() {
        // 「美联储」+「降息」两个严重词 = 6 分 ≥ SERIOUS_SCORE。
        assertEquals(6, NewsScorer.score(item("1", "美联储降息", "美联储宣布降息。")))
    }

    @Test
    fun score_severePlusImportantBonus() {
        // 「CPI」严重词 3 + important 2 = 5 分。
        assertEquals(5, NewsScorer.score(item("2", "美国CPI", "公布CPI数据。", important = true)))
    }

    @Test
    fun score_attentionOnly_belowWarnThreshold() {
        // 单个关注词 = 1 分 < WARN_SCORE。
        assertEquals(1, NewsScorer.score(item("3", "美股收盘", "美股收盘涨跌互现。")))
    }

    @Test
    fun score_sameWordInTitleAndBody_countedOnce() {
        // 「美联储」在 title 与正文都出现，contains 只计一次 = 3 分。
        assertEquals(3, NewsScorer.score(item("4", "美联储", "美联储按兵不动。")))
    }

    @Test
    fun select_outsideWindow_excluded() {
        val old = item("old", "美联储降息", "美联储降息。", ageMs = 25 * hour) // 25h > 24h 窗口
        assertTrue(NewsScorer.select(listOf(old), now).isEmpty())
    }

    @Test
    fun select_futureTimestamp_excluded() {
        // 时间在未来（时钟偏移防护）：nowMs - timeEpochMs < 0 不在 [0, WINDOW)。
        val future = NewsItem("f", "美联储降息", "x", now + hour, false)
        assertTrue(NewsScorer.select(listOf(future), now).isEmpty())
    }

    @Test
    fun select_lowScore_excluded() {
        val low = item("low", "美股收盘", "美股收盘。") // 1 分
        assertTrue(NewsScorer.select(listOf(low), now).isEmpty())
    }

    @Test
    fun select_cappedAtMaxPerSync_byScoreDesc() {
        // 5 条高分：3 条 6 分 + 2 条 3 分 → 只取分数最高的前 3 条。
        val high1 = item("h1", "美联储降息", "美联储降息。")
        val high2 = item("h2", "美联储加息", "美联储加息。")
        val high3 = item("h3", "欧洲央行缩表", "欧洲央行缩表。")
        val mid1 = item("m1", "CPI 公布", "CPI。")
        val mid2 = item("m2", "非农就业", "非农。")
        val selected = NewsScorer.select(listOf(mid1, high1, mid2, high2, high3), now)
        assertEquals(3, selected.size)
        assertEquals(listOf("h1", "h2", "h3"), selected.map { it.id })
    }

    @Test
    fun select_sameScore_newerFirst() {
        // 两条同分（各 3 分：仅 CPI）：发布时间新的排前。
        val older = item("older", "CPI 公布", "CPI。", ageMs = 5 * hour)
        val newer = item("newer", "CPI 数据", "CPI。", ageMs = 1 * hour)
        val selected = NewsScorer.select(listOf(older, newer), now)
        assertEquals(listOf("newer", "older"), selected.map { it.id })
    }
}
