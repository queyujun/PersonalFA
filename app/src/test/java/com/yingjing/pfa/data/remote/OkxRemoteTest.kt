package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.model.Currency
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

/** OKX 备源：验证 coin id→instId 映射 + USDT 价经 FX 换算成 CNY/HKD/USD 三币种。 */
class OkxRemoteTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    /** 固定汇率：1 USD = 7 CNY，1 HKD = 0.9 CNY（FxRates 语义为 1 外币 = ? CNY），便于断言换算。 */
    private val rates = FxRates(usdToCny = 7.0, hkdToCny = 0.9)
    private val fxRepo = object : FxRepository {
        override fun observeRates(): Flow<FxRates> = flowOf(rates)
        override suspend fun current(): FxRates = rates
        override suspend fun refresh(): FxRates = rates
        override suspend fun save(rates: FxRates) {}
    }

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
    fun fetch_mapsCoinIdAndConvertsToMultiCurrency() = runBlocking {
        // OKX 对 BTC-USDT 返回 78246.1 USDT
        server.enqueue(MockResponse().setBody(
            """{"code":"0","data":[{"instId":"BTC-USDT","last":"78246.1"}],"msg":""}""",
        ))
        val remote = OkxRemote(client, fxRepo).apply { baseUrl = server.url("/api/v5/").toString() }

        val result = remote.fetch(listOf("bitcoin"))

        // coin id 仍为 "bitcoin"（对齐主源契约），内层含三币种
        val byCurrency = result["bitcoin"]!!
        assertEquals(78246.1, byCurrency["usd"]!!, 0.001)            // 原价
        assertEquals(78246.1 * 7.0, byCurrency["cny"]!!, 0.001)     // 1USD=7CNY
        assertEquals(78246.1 * 7.0 / 0.9, byCurrency["hkd"]!!, 0.001) // 1HKD=0.9CNY → 1USD=7/0.9 HKD
    }

    @Test
    fun fetch_skipsCoinIdWithoutInstId() = runBlocking {
        // USDT 是稳定币 → OkxCoinMap 返回 null → 不发请求，不在结果里
        val remote = OkxRemote(client, fxRepo).apply { baseUrl = server.url("/api/v5/").toString() }
        val result = remote.fetch(listOf("tether"))
        assertTrue(result.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun fetch_emptyIds_makesNoRequest() = runBlocking {
        val remote = OkxRemote(client, fxRepo).apply { baseUrl = server.url("/api/v5/").toString() }
        assertTrue(remote.fetch(emptyList()).isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun fetch_coinIdOutsideMap_usesUppercaseFallback() = runBlocking {
        // 不在内置表的 pepe → PEPE-USDT
        server.enqueue(MockResponse().setBody(
            """{"code":"0","data":[{"instId":"PEPE-USDT","last":"0.0000085"}],"msg":""}""",
        ))
        val remote = OkxRemote(client, fxRepo).apply { baseUrl = server.url("/api/v5/").toString() }
        val result = remote.fetch(listOf("pepe"))
        assertEquals(0.0000085, result["pepe"]!!["usd"]!!, 1e-9)
        // 发往 OKX 的 path 含 PEPE-USDT
        assertTrue(server.takeRequest().path!!.contains("PEPE-USDT"))
    }

    @Test
    fun fetch_failedTicker_returnsEmptyMap() = runBlocking {
        // HTTP 500 → 该币种 fetchOne 返回 null → 结果空
        server.enqueue(MockResponse().setResponseCode(500))
        val remote = OkxRemote(client, fxRepo).apply { baseUrl = server.url("/api/v5/").toString() }
        assertTrue(remote.fetch(listOf("bitcoin")).isEmpty())
    }
}
