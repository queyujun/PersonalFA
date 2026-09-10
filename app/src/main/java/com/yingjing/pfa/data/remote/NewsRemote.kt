package com.yingjing.pfa.data.remote

import android.util.Log
import com.yingjing.pfa.domain.alert.NewsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** 国际财经快讯数据源。 */
fun interface NewsRemote {
    suspend fun fetch(): List<NewsItem>
}

/** 华尔街见闻直播（国际频道）实现。 */
class WallstcnNewsRemote @Inject constructor(
    private val client: OkHttpClient,
) : NewsRemote {

    var endpoint: String = "https://api-one-wscn.awtmt.com/apiv1/content/lives"

    override suspend fun fetch(): List<NewsItem> {
        val url = "$endpoint?channel=global-channel&limit=30"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<NewsItem>()
                    val text = response.body?.string() ?: return@use emptyList<NewsItem>()
                    NewsParser.parseWallstcn(text)
                }
            }.getOrDefault(emptyList())
        }
    }
}

/** 东方财富 7×24 快讯实现（req_trace 参数缺失会被服务端拒绝，必须带上）。 */
class EastmoneyNewsRemote @Inject constructor(
    private val client: OkHttpClient,
) : NewsRemote {

    var endpoint: String = "https://np-listapi.eastmoney.com/comm/web/getFastNewsList"

    override suspend fun fetch(): List<NewsItem> {
        val url = "$endpoint?client=web&biz=web_724&fastColumn=102&sortEnd=&pageSize=20&req_trace=1"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", "https://www.eastmoney.com/")
            .build()
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<NewsItem>()
                    val text = response.body?.string() ?: return@use emptyList<NewsItem>()
                    NewsParser.parseEastmoney(text)
                }
            }.getOrDefault(emptyList())
        }
    }
}

/**
 * 快讯主备容灾：先华尔街见闻主源，失败或空时回退东方财富备源（仿 [FallbackCryptoRemote]）。
 * 双源皆空时返回空列表——由 SyncManager 计入 NEWS 失败源上报 UI。
 */
@Singleton
class FallbackNewsRemote @Inject constructor(
    private val primary: WallstcnNewsRemote,
    private val fallback: EastmoneyNewsRemote,
) : NewsRemote {

    override suspend fun fetch(): List<NewsItem> {
        val primaryResult = runCatching { primary.fetch() }
            .onFailure { Log.w(TAG, "Wallstcn news primary failed, will try Eastmoney fallback", it) }
            .getOrDefault(emptyList())
        if (primaryResult.isNotEmpty()) return primaryResult
        return runCatching { fallback.fetch() }
            .onFailure { Log.w(TAG, "Eastmoney news fallback failed", it) }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val TAG = "FallbackNewsRemote"
    }
}
