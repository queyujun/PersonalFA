package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.model.FearIndexId
import com.yingjing.pfa.domain.model.PriceSeriesId
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** 用 MockWebServer 验证 [DefaultMarketSeriesRemote] 的请求参数、条件请求与主备切换。 */
class MarketSeriesRemoteTest {

    private lateinit var server: MockWebServer
    private val requests = mutableListOf<RecordedRequest>()
    private var routes: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(404) }

    private val today = LocalDate.of(2026, 11, 25)

    private lateinit var remote: DefaultMarketSeriesRemote

    @Before
    fun setup() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(requests) { requests += request }
                return routes(request)
            }
        }
        server.start()
        remote = DefaultMarketSeriesRemote(OkHttpClient()).apply {
            cboeBaseUrl = server.url("/cboe/").toString()
            fngUrl = server.url("/fng/").toString()
            tencentUrl = server.url("/tx").toString()
            fredUrl = server.url("/fred").toString()
            okxUrl = server.url("/okx").toString()
            clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun path(r: RecordedRequest) = r.requestUrl!!.encodedPath

    @Test
    fun `cboe full download returns replaceAll with last modified`() = runBlocking {
        routes = {
            MockResponse().setBody("DATE,VVIX\n11/24/2026,114.07\n").setHeader("Last-Modified", "Wed, 25 Nov 2026 02:11:05 GMT")
        }
        val result = remote.fetchFear(FearIndexId.VVIX, since = null, ifModifiedSince = null)
        assertTrue(result is SeriesFetch.Updated)
        result as SeriesFetch.Updated
        assertTrue(result.replaceAll)
        assertEquals("Wed, 25 Nov 2026 02:11:05 GMT", result.lastModified)
        assertEquals("/cboe/VVIX_History.csv", path(requests.single()))
        assertNull(requests.single().getHeader("If-Modified-Since"))
    }

    @Test
    fun `cboe conditional request maps 304 to NotModified`() = runBlocking {
        routes = { MockResponse().setResponseCode(304) }
        val result = remote.fetchFear(FearIndexId.VIX, LocalDate.of(2026, 11, 20), "Wed, 25 Nov 2026 02:11:05 GMT")
        assertEquals(SeriesFetch.NotModified, result)
        assertEquals("Wed, 25 Nov 2026 02:11:05 GMT", requests.single().getHeader("If-Modified-Since"))
    }

    @Test
    fun `cboe failure falls back to fred with overlap start date`() = runBlocking {
        routes = { r ->
            when {
                path(r).startsWith("/cboe/") -> MockResponse().setResponseCode(403).setBody("<html>denied</html>")
                path(r) == "/fred" -> MockResponse().setBody("observation_date,VIXCLS\n2026-11-24,26.36\n")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = remote.fetchFear(FearIndexId.VIX, LocalDate.of(2026, 11, 20), null) as SeriesFetch.Updated
        assertEquals(26.36, result.points.single().value, 1e-9)
        assertTrue("FRED 增量不得整体替换", !result.replaceAll)
        val fred = requests.last().requestUrl!!
        assertEquals("VIXCLS", fred.queryParameter("id"))
        assertEquals("2026-11-13", fred.queryParameter("cosd"))
    }

    @Test
    fun `vvix has no fred fallback`() = runBlocking {
        routes = { MockResponse().setResponseCode(500) }
        assertEquals(SeriesFetch.Failed, remote.fetchFear(FearIndexId.VVIX, null, null))
        assertEquals(1, requests.size)
    }

    @Test
    fun `fear greed first load requests full history then incremental limit`() = runBlocking {
        routes = { MockResponse().setBody("""{"data":[{"value":"13","timestamp":"1795564800"}]}""") }
        remote.fetchFear(FearIndexId.CRYPTO_FNG, since = null, ifModifiedSince = null)
        assertEquals("0", requests[0].requestUrl!!.queryParameter("limit"))
        remote.fetchFear(FearIndexId.CRYPTO_FNG, since = LocalDate.of(2026, 11, 20), ifModifiedSince = null)
        assertEquals("12", requests[1].requestUrl!!.queryParameter("limit")) // 5 天缺口 + 7 天回看
    }

    @Test
    fun `tencent request encodes start date and capped count`() = runBlocking {
        routes = {
            MockResponse().setBody("""{"code":0,"data":{"us.INX":{"day":[["2026-11-24","1","7201.09","1","1","1"]]}}}""")
        }
        val result = remote.fetchPrice(PriceSeriesId.SPX, LocalDate.of(2019, 11, 25)) as SeriesFetch.Updated
        assertEquals(7201.09, result.points.single().value, 1e-9)
        assertEquals("us.INX,day,2019-11-25,,2000,qfq", requests.single().requestUrl!!.queryParameter("param"))
    }

    @Test
    fun `btc uses fred then falls back to okx`() = runBlocking {
        routes = { r ->
            when (path(r)) {
                "/fred" -> MockResponse().setResponseCode(503)
                "/okx" -> MockResponse().setBody("""{"code":"0","data":[["1795564800000","1","1","1","59116.3","1"]]}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = remote.fetchPrice(PriceSeriesId.BTC, LocalDate.of(2026, 11, 1)) as SeriesFetch.Updated
        assertEquals(59116.3, result.points.single().value, 1e-9)
        assertEquals(listOf("/fred", "/okx"), requests.map { path(it) })
        assertEquals("CBBTCUSD", requests[0].requestUrl!!.queryParameter("id"))
        assertEquals("BTC-USDT", requests[1].requestUrl!!.queryParameter("instId"))
    }

    @Test
    fun `empty parse result is reported as failure`() = runBlocking {
        routes = { MockResponse().setBody("""{"code":0,"data":{"us.NDX":{"day":[]}}}""") }
        assertEquals(SeriesFetch.Failed, remote.fetchPrice(PriceSeriesId.NDX, LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `oversized body is rejected`() = runBlocking {
        val huge = buildString {
            append("DATE,VVIX\n")
            while (length < 4 * 1024 * 1024 + 100) append("11/24/2026,114.07\n")
        }
        routes = { MockResponse().setBody(huge) }
        assertEquals(SeriesFetch.Failed, remote.fetchFear(FearIndexId.VVIX, null, null))
    }

    @Test
    fun `network failure is reported as failure not exception`() = runBlocking {
        server.shutdown()
        assertEquals(SeriesFetch.Failed, remote.fetchPrice(PriceSeriesId.GLD, LocalDate.of(2026, 1, 1)))
    }
}
