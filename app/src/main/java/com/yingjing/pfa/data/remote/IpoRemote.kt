package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.alert.IpoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

/** 新股申购日历数据源。 */
fun interface IpoRemote {
    suspend fun fetch(): List<IpoItem>
}

/** 东方财富新股申购实现（RPTA_APP_IPOAPPLY）。 */
class EastmoneyIpoRemote @Inject constructor(
    private val client: OkHttpClient,
) : IpoRemote {

    var endpoint: String = "https://datacenter-web.eastmoney.com/api/data/v1/get"

    override suspend fun fetch(): List<IpoItem> {
        val url = "$endpoint?sortColumns=APPLY_DATE&sortTypes=-1&pageSize=50&pageNumber=1" +
            "&reportName=RPTA_APP_IPOAPPLY&columns=ALL&source=WEB&client=WEB"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", "https://data.eastmoney.com/")
            .build()
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<IpoItem>()
                    val text = response.body?.string() ?: return@use emptyList<IpoItem>()
                    IpoParser.parse(text)
                }
            }.getOrDefault(emptyList())
        }
    }
}
