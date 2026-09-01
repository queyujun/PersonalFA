package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.model.FxRates
import com.yingjing.pfa.domain.repository.FxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 主备容灾：CoinGecko 主源返回空/失败 → fallback 到 OKX 备源；主源非空 → 不调备源。
 *
 * 两个 Remote 各用独立 MockWebServer，精确控制主/备的 HTTP 行为。
 */
class FallbackCryptoRemoteTest {

    private lateinit var primaryServer: MockWebServer
    private lateinit var fallbackServer: MockWebServer
    private val client = OkHttpClient()
    private val fxRepo = object : FxRepository {
        override fun observeRates(): Flow<FxRates> = flowOf(FxRates(usdToCny = 7.0, hkdToCny = 0.9))
        override suspend fun current(): FxRates = FxRates(usdToCny = 7.0, hkdToCny = 0.9)
        override suspend fun refresh(): FxRates = FxRates(usdToCny = 7.0, hkdToCny = 0.9)
        override suspend fun save(rates: FxRates) {}
    }

    @Before
    fun setup() {
        primaryServer = MockWebServer()
        fallbackServer = MockWebServer()
        primaryServer.start()
        fallbackServer.start()
    }

    @After
    fun tearDown() {
        primaryServer.shutdown()
        fallbackServer.shutdown()
    }

    private fun buildRemote(): FallbackCryptoRemote {
        val primary = CoinGeckoRemote(client).apply { baseUrl = primaryServer.url("/").toString() }
        val fallback = OkxRemote(client, fxRepo).apply { baseUrl = fallbackServer.url("/api/v5/").toString() }
        return FallbackCryptoRemote(primary, fallback)
    }

    @Test
    fun primaryNonEmpty_usesPrimary_noFallbackCall() = runBlocking {
        // CoinGecko 返回 bitcoin 多币种价 → 直接用主源，不调 OKX
        primaryServer.enqueue(MockResponse().setBody(
            """{"bitcoin":{"cny":429733,"hkd":499869,"usd":63709}}""",
        ))
        val remote = buildRemote()

        val result = remote.fetch(listOf("bitcoin"))

        assertEquals(63709.0, result["bitcoin"]!!["usd"]!!, 0.001)
        assertEquals(1, primaryServer.requestCount) // 只调了主源
        assertEquals(0, fallbackServer.requestCount) // 没调备源
    }

    @Test
    fun primaryEmpty_fallsBackToOkx() = runBlocking {
        // CoinGecko 国内不可达返回 404 → emptyMap → fallback OKX
        primaryServer.enqueue(MockResponse().setResponseCode(404))
        // OKX 返回 BTC-USDT 78246.1 USDT
        fallbackServer.enqueue(MockResponse().setBody(
            """{"code":"0","data":[{"instId":"BTC-USDT","last":"78246.1"}],"msg":""}""",
        ))
        val remote = buildRemote()

        val result = remote.fetch(listOf("bitcoin"))

        // 备源结果：usd=原价，cny/hkd 经 FX 换算
        assertEquals(78246.1, result["bitcoin"]!!["usd"]!!, 0.001)
        assertEquals(78246.1 * 7.0, result["bitcoin"]!!["cny"]!!, 0.001)
        assertEquals(1, primaryServer.requestCount)
        assertEquals(1, fallbackServer.requestCount)
    }

    @Test
    fun primaryThrows_fallsBackToOkx() = runBlocking {
        // 主源 body 非法 JSON → CoinGeckoRemote.runCatching 返回 emptyMap → fallback
        primaryServer.enqueue(MockResponse().setBody("not json"))
        fallbackServer.enqueue(MockResponse().setBody(
            """{"code":"0","data":[{"instId":"BTC-USDT","last":"78246.1"}],"msg":""}""",
        ))
        val remote = buildRemote()

        val result = remote.fetch(listOf("bitcoin"))

        assertEquals(78246.1, result["bitcoin"]!!["usd"]!!, 0.001)
        assertEquals(1, fallbackServer.requestCount)
    }

    @Test
    fun bothEmpty_returnsEmpty() = runBlocking {
        // 主源空 + 备源空 → 最终空
        primaryServer.enqueue(MockResponse().setResponseCode(404))
        fallbackServer.enqueue(MockResponse().setResponseCode(500))
        val remote = buildRemote()

        val result = remote.fetch(listOf("bitcoin"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun emptyIds_noRequest() = runBlocking {
        val remote = buildRemote()
        assertTrue(remote.fetch(emptyList()).isEmpty())
        assertEquals(0, primaryServer.requestCount)
        assertEquals(0, fallbackServer.requestCount)
    }
}
