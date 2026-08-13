package com.yingjing.pfa.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** 用 MockWebServer 验证真实数据源代码（HTTP + 解析）。 */
class RemoteDataSourceTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun coinGeckoRemote_fetchesAndParses() {
        server.enqueue(MockResponse().setBody("""{"bitcoin":{"cny":429733,"hkd":499869,"usd":63709}}"""))
        val remote = CoinGeckoRemote(client).apply { baseUrl = server.url("/").toString() }
        val result = runBlocking { remote.fetch(listOf("bitcoin")) }
        assertEquals(63709.0, result["bitcoin"]!!["usd"]!!, 0.001)
    }

    @Test
    fun sinaStockRemote_decodesGbk_andParses() {
        // 以 GBK 编码含中文名称的响应，验证解码路径
        val payload = """var hq_str_sh600519="贵州茅台,1338.000,1343.000,1354.500,1359.600";"""
        server.enqueue(
            MockResponse().setBody(
                okio.Buffer().write(payload.toByteArray(charset("GBK"))),
            ),
        )
        val remote = SinaStockRemote(client).apply { baseUrl = server.url("/list=").toString() }
        val result = runBlocking { remote.fetch(listOf("sh600519")) }
        assertEquals(1354.5, result["sh600519"]!!, 0.001)
    }

    @Test
    fun sinaStockRemote_emptyCodes_noRequest() {
        val remote = SinaStockRemote(client).apply { baseUrl = server.url("/list=").toString() }
        val result = runBlocking { remote.fetch(emptyList()) }
        assertEquals(0, result.size)
    }
}
