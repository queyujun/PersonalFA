package com.yingjing.pfa.domain.alert

/** 一条国际财经快讯（每日同步时抓取，即用即弃不落库）。 */
data class NewsItem(
    /** 全局唯一 id（parser 层带源前缀：wscn_ / em_，作 alert dedupKey 防跨源撞号）。 */
    val id: String,
    /** 展示标题（源 title 为空时回退正文截断）。 */
    val title: String,
    /** 纯文本正文（关键词打分 + 提醒 body 用）。 */
    val contentText: String,
    /** 发布时间（epoch ms）。 */
    val timeEpochMs: Long,
    /** 源标注重要（华尔街见闻 score>=2；东财恒 false）。 */
    val important: Boolean,
)
