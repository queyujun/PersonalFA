package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.model.FearIndexId
import com.yingjing.pfa.domain.model.PriceSeriesId
import com.yingjing.pfa.domain.model.SeriesPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * 一次抓取的结果。
 *
 * - [Updated]：拿到了数据。[replaceAll] = true 表示返回的是该序列的**完整历史**（CBOE 全量 CSV），
 *   仓库应整体替换；false 为增量，仓库按日 upsert。[lastModified] 仅 CBOE 提供，供下次条件请求。
 * - [NotModified]：条件请求命中 304，本地缓存已是最新。
 * - [Failed]：网络 / HTTP / 解析失败或返回空数据（不抛异常，交由仓库层记为失败源）。
 */
sealed interface SeriesFetch {
    data class Updated(
        val points: List<SeriesPoint>,
        val replaceAll: Boolean = false,
        val lastModified: String? = null,
    ) : SeriesFetch

    data object NotModified : SeriesFetch
    data object Failed : SeriesFetch
}

/**
 * 市场情绪页数据源：恐慌指数与叠加价格。只发公开 GET，不携带任何用户数据。
 * 网络 / HTTP / 解析失败返回 [SeriesFetch.Failed]；协程取消照常传播。
 */
interface MarketSeriesRemote {
    /**
     * 恐慌指数。[since] 为本地已有数据的增量起点（null = 本地无数据，取全量）；
     * [ifModifiedSince] 为上次 CBOE 响应的 Last-Modified（条件请求）。
     */
    suspend fun fetchFear(id: FearIndexId, since: LocalDate?, ifModifiedSince: String?): SeriesFetch

    /** 叠加价格，取 [since]（含）之后的日线。 */
    suspend fun fetchPrice(id: PriceSeriesId, since: LocalDate): SeriesFetch
}

/**
 * 实现（主源失败时自动走备源，两源均为公开免费接口）：
 *
 * | 序列 | 主源 | 备源 |
 * |---|---|---|
 * | VIX / VXN / GVZ / OVX | CBOE 历史 CSV（全量，支持 304） | FRED `VIXCLS` 等（cosd 增量） |
 * | VVIX | CBOE 历史 CSV | — |
 * | 加密恐惧贪婪 | alternative.me（limit 增量） | — |
 * | 标普 500 / 纳指 100 / GLD / USO | 腾讯美股日 K（起始日增量） | — |
 * | 比特币 | FRED `CBBTCUSD`（cosd 增量） | OKX 日 K（最近 300 天） |
 *
 * OkHttp 在未显式设置 Accept-Encoding 时自动协商 gzip 并透明解压。
 */
class DefaultMarketSeriesRemote @Inject constructor(
    private val client: OkHttpClient,
) : MarketSeriesRemote {

    var cboeBaseUrl: String = "https://cdn.cboe.com/api/global/us_indices/daily_prices/"
    var fngUrl: String = "https://api.alternative.me/fng/"
    var tencentUrl: String = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"
    var fredUrl: String = "https://fred.stlouisfed.org/graph/fredgraph.csv"
    var okxUrl: String = "https://www.okx.com/api/v5/market/candles"

    /** 可替换时钟（测试固定「今天」）。 */
    var clock: Clock = Clock.systemDefaultZone()

    override suspend fun fetchFear(id: FearIndexId, since: LocalDate?, ifModifiedSince: String?): SeriesFetch {
        if (id == FearIndexId.CRYPTO_FNG) return fetchFearGreed(since)
        val primary = fetchCboe(cboeCode(id), ifModifiedSince)
        if (primary != SeriesFetch.Failed) return primary
        val fredId = fredFallbackCode(id) ?: return SeriesFetch.Failed
        return fetchFred(fredId, since?.minusDays(OVERLAP_DAYS))
    }

    override suspend fun fetchPrice(id: PriceSeriesId, since: LocalDate): SeriesFetch {
        if (id != PriceSeriesId.BTC) return fetchTencent(tencentCode(id), since)
        val primary = fetchFred(FRED_BTC, since)
        return if (primary != SeriesFetch.Failed) primary else fetchOkxBtc()
    }

    private suspend fun fetchCboe(code: String, ifModifiedSince: String?): SeriesFetch {
        val builder = Request.Builder().url("$cboeBaseUrl${code}_History.csv").header("Accept", "text/csv")
        ifModifiedSince?.let { builder.header("If-Modified-Since", it) }
        return execute(builder.build()) { response ->
            if (response.code == HTTP_NOT_MODIFIED) return@execute SeriesFetch.NotModified
            val body = response.readBodyCapped() ?: return@execute SeriesFetch.Failed
            MarketSeriesParsers.parseCboeCsv(body)
                .toFetch(replaceAll = true, lastModified = response.header("Last-Modified"))
        }
    }

    private suspend fun fetchFearGreed(since: LocalDate?): SeriesFetch {
        // alternative.me 只有 limit（最近 N 条）；0 = 全量。增量取缺口天数 + 余量。
        val limit = since?.let { (today().toEpochDay() - it.toEpochDay() + OVERLAP_DAYS).coerceIn(2, 5_000) } ?: 0
        val url = fngUrl.toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("format", "json")
            .build()
        return execute(Request.Builder().url(url).header("Accept", "application/json").build()) { response ->
            val body = response.readBodyCapped() ?: return@execute SeriesFetch.Failed
            MarketSeriesParsers.parseFearGreedJson(body).toFetch()
        }
    }

    private suspend fun fetchTencent(code: String, since: LocalDate): SeriesFetch {
        val count = (today().toEpochDay() - since.toEpochDay() + 10).coerceIn(10, TENCENT_MAX_ROWS)
        val url = tencentUrl.toHttpUrl().newBuilder()
            .addQueryParameter("param", "$code,day,$since,,$count,qfq")
            .build()
        return execute(Request.Builder().url(url).header("Accept", "application/json").build()) { response ->
            val body = response.readBodyCapped() ?: return@execute SeriesFetch.Failed
            MarketSeriesParsers.parseTencentKline(body, code).toFetch()
        }
    }

    /** [since] 为 null 时不带 cosd，FRED 返回全部历史。 */
    private suspend fun fetchFred(seriesId: String, since: LocalDate?): SeriesFetch {
        val builder = fredUrl.toHttpUrl().newBuilder().addQueryParameter("id", seriesId)
        since?.let { builder.addQueryParameter("cosd", it.toString()) }
        return execute(Request.Builder().url(builder.build()).header("Accept", "text/csv").build()) { response ->
            val body = response.readBodyCapped() ?: return@execute SeriesFetch.Failed
            MarketSeriesParsers.parseFredCsv(body).toFetch()
        }
    }

    private suspend fun fetchOkxBtc(): SeriesFetch {
        val url = okxUrl.toHttpUrl().newBuilder()
            .addQueryParameter("instId", "BTC-USDT")
            .addQueryParameter("bar", "1Dutc")
            .addQueryParameter("limit", "300")
            .build()
        return execute(Request.Builder().url(url).header("Accept", "application/json").build()) { response ->
            val body = response.readBodyCapped() ?: return@execute SeriesFetch.Failed
            MarketSeriesParsers.parseOkxCandles(body).toFetch()
        }
    }

    /** IO 线程执行；非 2xx（304 除外）→ Failed；IO 异常 → Failed。取消由协程机制传播。 */
    private suspend fun execute(request: Request, handle: (Response) -> SeriesFetch): SeriesFetch =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != HTTP_NOT_MODIFIED) SeriesFetch.Failed
                    else handle(response)
                }
            } catch (_: IOException) {
                SeriesFetch.Failed
            }
        }

    /** 读取响应体；超过 [MAX_BODY_BYTES] 视为异常响应（防御异常巨大的响应占满内存）。 */
    private fun Response.readBodyCapped(): String? {
        val source = body?.source() ?: return null
        source.request(MAX_BODY_BYTES + 1)
        if (source.buffer.size > MAX_BODY_BYTES) return null
        return source.buffer.readUtf8()
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private fun List<SeriesPoint>.toFetch(replaceAll: Boolean = false, lastModified: String? = null): SeriesFetch =
        if (isEmpty()) SeriesFetch.Failed else SeriesFetch.Updated(this, replaceAll, lastModified)

    companion object {
        private const val HTTP_NOT_MODIFIED = 304

        /** 增量请求回看天数：覆盖周末/节假日并吸收源端对近几日的修订。 */
        private const val OVERLAP_DAYS = 7L

        /** 单个响应体上限 4MB（VIX 全量未压缩约 0.5MB）。 */
        private const val MAX_BODY_BYTES = 4L * 1024 * 1024

        /** 腾讯日 K 单次最多约 2000 条（实测 2500 返回空）。 */
        private const val TENCENT_MAX_ROWS = 2_000L

        private const val FRED_BTC = "CBBTCUSD"

        internal fun cboeCode(id: FearIndexId): String = when (id) {
            FearIndexId.VIX -> "VIX"
            FearIndexId.VXN -> "VXN"
            FearIndexId.VVIX -> "VVIX"
            FearIndexId.GVZ -> "GVZ"
            FearIndexId.OVX -> "OVX"
            FearIndexId.CRYPTO_FNG -> error("CRYPTO_FNG is not a CBOE series")
        }

        /** CBOE 不可达时的 FRED 备源（VVIX 无 FRED 序列）。 */
        internal fun fredFallbackCode(id: FearIndexId): String? = when (id) {
            FearIndexId.VIX -> "VIXCLS"
            FearIndexId.VXN -> "VXNCLS"
            FearIndexId.GVZ -> "GVZCLS"
            FearIndexId.OVX -> "OVXCLS"
            FearIndexId.VVIX, FearIndexId.CRYPTO_FNG -> null
        }

        internal fun tencentCode(id: PriceSeriesId): String = when (id) {
            PriceSeriesId.SPX -> "us.INX"
            PriceSeriesId.NDX -> "us.NDX"
            PriceSeriesId.GLD -> "usGLD.AM"
            PriceSeriesId.USO -> "usUSO.AM"
            PriceSeriesId.BTC -> error("BTC is not a Tencent series")
        }
    }
}
