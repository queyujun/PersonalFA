package com.yingjing.pfa.domain.alert

/**
 * 快讯关键词打分（纯函数，可单元测试）。
 *
 * 严重词 +3 / 关注词 +1 / 源标注重要 +2，≥6 SERIOUS、≥3 WARNING、<3 不提醒。
 * 双保险防轰炸：只看发布时间在 24h 窗口内的条目；每批最多取 [MAX_PER_SYNC] 条。
 */
object NewsScorer {

    /** 只提醒最近 24h 内发布的快讯（首次同步不补历史轰炸）。 */
    const val WINDOW_MS: Long = 24 * 3_600_000L

    /** 每次同步最多提醒条数（按分数降序取头部）。 */
    const val MAX_PER_SYNC = 3

    /** 分数达到该值 → SERIOUS。 */
    const val SERIOUS_SCORE = 6

    /** 分数达到该值 → WARNING；低于则不提醒。 */
    const val WARN_SCORE = 3

    /** 严重词：宏观政策转折 / 系统性风险 / 地缘冲突（+3）。 */
    private val SEVERE_WORDS = listOf(
        "美联储", "Fed", "降息", "加息", "议息", "缩表",
        "欧洲央行", "日本央行", "英国央行",
        "非农", "CPI", "PCE", "PMI",
        "衰退", "暴雷", "破产", "违约", "接管",
        "熔断", "崩盘", "危机", "紧急", "闪崩",
        "制裁", "关税", "tariff",
        "战争", "袭击", "停火", "遇刺", "弹劾",
        "暴跌", "暴涨",
    )

    /** 关注词：大类资产与宏观常备主题（+1）。 */
    private val ATTENTION_WORDS = listOf(
        "美股", "纳指", "标普", "道指",
        "黄金", "金价", "原油", "油价",
        "美元指数", "美债", "收益率",
        "通胀", "就业", "GDP", "财报",
        "芯片", "半导体", "AI", "地缘",
    )

    /** 源标注重要的额外加权。 */
    private const val IMPORTANT_BONUS = 2

    /** 计算一条快讯的分数（title + 正文合并匹配，命中一次即计，不叠加同词）。 */
    fun score(item: NewsItem): Int {
        // title 通常是正文的头部子串，合并后 contains 即可，无需去重。
        val text = item.title + " " + item.contentText
        var s = 0
        SEVERE_WORDS.forEach { if (text.contains(it)) s += 3 }
        ATTENTION_WORDS.forEach { if (text.contains(it)) s += 1 }
        if (item.important) s += IMPORTANT_BONUS
        return s
    }

    /**
     * 从一批快讯中选出值得提醒的条目：
     * 24h 窗口过滤 → 分数 ≥ [WARN_SCORE] → 分数降序（同分时间新在前）→ 截取 [MAX_PER_SYNC]。
     */
    fun select(news: List<NewsItem>, nowMs: Long): List<NewsItem> {
        return news.asSequence()
            .filter { nowMs - it.timeEpochMs in 0 until WINDOW_MS }
            .map { it to score(it) }
            .filter { (_, s) -> s >= WARN_SCORE }
            .sortedWith(compareByDescending<Pair<NewsItem, Int>> { it.second }.thenByDescending { it.first.timeEpochMs })
            .take(MAX_PER_SYNC)
            .map { it.first }
            .toList()
    }
}
