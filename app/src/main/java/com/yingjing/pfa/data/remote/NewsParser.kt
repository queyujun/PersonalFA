package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.alert.NewsItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 解析国际财经快讯 JSON（华尔街见闻直播 / 东方财富 7×24）。 */
object NewsParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** 华尔街见闻 display_time 为 Unix 秒。 */
    private const val SEC_TO_MS = 1000L

    /** 东财 showTime 格式（北京时间）。 */
    private val EM_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 东财 showTime 按北京时间解释。 */
    private val EM_ZONE = ZoneId.of("Asia/Shanghai")

    /** 标题回退：正文截断长度。 */
    private const val TITLE_FALLBACK_LEN = 40

    /** 解析华尔街见闻直播列表响应。 */
    fun parseWallstcn(body: String): List<NewsItem> {
        val response = runCatching { json.decodeFromString<WscnResponse>(body) }.getOrNull()
        val items = response?.data?.items ?: return emptyList()
        return items.mapNotNull { item ->
            val id = item.id ?: return@mapNotNull null
            val content = item.contentText?.trim().orEmpty()
            if (content.isEmpty()) return@mapNotNull null
            val displayTime = item.displayTime ?: return@mapNotNull null
            NewsItem(
                id = "wscn_$id",
                title = item.title?.takeIf { it.isNotBlank() } ?: content.take(TITLE_FALLBACK_LEN),
                contentText = content,
                timeEpochMs = displayTime * SEC_TO_MS,
                important = (item.score ?: 0) >= 2,
            )
        }
    }

    /** 解析东方财富 7×24 快讯列表响应。 */
    fun parseEastmoney(body: String): List<NewsItem> {
        val response = runCatching { json.decodeFromString<EmResponse>(body) }.getOrNull()
        val items = response?.data?.fastNewsList ?: return emptyList()
        return items.mapNotNull { item ->
            val code = item.code ?: return@mapNotNull null
            val summary = item.summary?.trim().orEmpty()
            if (summary.isEmpty()) return@mapNotNull null
            val timeMs = parseEastmoneyTime(item.showTime, item.realSort) ?: return@mapNotNull null
            NewsItem(
                id = "em_$code",
                title = item.title?.takeIf { it.isNotBlank() } ?: summary.take(TITLE_FALLBACK_LEN),
                contentText = summary,
                timeEpochMs = timeMs,
                important = false, // titleColor 语义未验证，保守不标
            )
        }
    }

    /** 东财时间：优先 showTime（yyyy-MM-dd HH:mm:ss 北京时间），缺失回退 realSort（epoch ms）。 */
    private fun parseEastmoneyTime(showTime: String?, realSort: Long?): Long? {
        if (!showTime.isNullOrBlank()) {
            val parsed = runCatching {
                LocalDateTime.parse(showTime.trim(), EM_TIME_FORMAT).atZone(EM_ZONE).toInstant().toEpochMilli()
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return realSort?.takeIf { it > 0 }
    }

    @Serializable
    private data class WscnResponse(
        val code: Int? = null,
        val data: WscnData? = null,
    )

    @Serializable
    private data class WscnData(val items: List<WscnItem> = emptyList())

    @Serializable
    private data class WscnItem(
        val id: Long? = null,
        val title: String? = null,
        @SerialName("content_text") val contentText: String? = null,
        @SerialName("display_time") val displayTime: Long? = null,
        val score: Int? = null,
    )

    @Serializable
    private data class EmResponse(
        val code: String? = null,
        val data: EmData? = null,
    )

    @Serializable
    private data class EmData(val fastNewsList: List<EmItem> = emptyList())

    @Serializable
    private data class EmItem(
        val title: String? = null,
        val summary: String? = null,
        val showTime: String? = null,
        val realSort: Long? = null,
        val code: String? = null,
    )
}
