package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.model.SeriesPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 市场情绪页的日线解析器集合（纯函数，便于单测）。
 *
 * 统一约定：
 * - 输出按 [SeriesPoint.epochDay] **升序**、同日**去重**（保留输入中最后出现的一条）。
 * - 坏行 / 空值 / 非有限数（NaN、∞）/ 非正值（价格与指数不可能 ≤0）整行跳过，不抛异常。
 * - 结构性解析失败返回空列表，由仓库层判为该源失败。
 */
object MarketSeriesParsers {

    private val CBOE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
    private const val SECONDS_PER_DAY = 86_400L
    private const val MILLIS_PER_DAY = 86_400_000L

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * CBOE 历史 CSV（`.../daily_prices/{X}_History.csv`）。两种列格式都取**最后一列**作收盘值：
     * - `DATE,OPEN,HIGH,LOW,CLOSE`（VIX / VXN）
     * - `DATE,VVIX`（VVIX / GVZ / OVX 单列）
     */
    fun parseCboeCsv(body: String): List<SeriesPoint> =
        parseCsv(body) { cols ->
            val day = parseDate(cols.first(), CBOE_DATE) ?: return@parseCsv null
            val value = cols.last().toDoubleOrNull() ?: return@parseCsv null
            SeriesPoint(day, value)
        }

    /** FRED CSV（`fredgraph.csv?id=X&cosd=yyyy-MM-dd`）：`observation_date,X`，缺值行为空或 `.`。 */
    fun parseFredCsv(body: String): List<SeriesPoint> =
        parseCsv(body) { cols ->
            val day = parseDate(cols[0], DateTimeFormatter.ISO_LOCAL_DATE) ?: return@parseCsv null
            val value = cols[1].toDoubleOrNull() ?: return@parseCsv null
            SeriesPoint(day, value)
        }

    /**
     * alternative.me `/fng/?limit=N&format=json`：
     * `{"data":[{"value":"13","timestamp":"1795564800",...}],"metadata":{"error":null}}`。
     * timestamp 为 UTC 零点秒数；value 为 0–100 字符串。数据按时间**倒序**返回。
     */
    fun parseFearGreedJson(body: String): List<SeriesPoint> {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
        val data = root["data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val ts = obj.stringOrNull("timestamp")?.toLongOrNull() ?: return@mapNotNull null
            val value = obj.stringOrNull("value")?.toDoubleOrNull() ?: return@mapNotNull null
            // 0 是合法的恐惧贪婪读数（极少见），因此不走「必须 >0」的通用校验。
            if (!value.isFinite() || value < 0.0 || value > 100.0) return@mapNotNull null
            SeriesPoint(Math.floorDiv(ts, SECONDS_PER_DAY), value)
        }.normalized()
    }

    /**
     * 腾讯日 K 线 `fqkline/get?param={code},day,{start},,{count},qfq`：
     * `{"code":0,"data":{"{code}":{"day":[["2026-11-24","开","收","高","低","量"],...]}}}`。
     * 某些标的返回 `qfqday` 而非 `day`；**收盘价在下标 2**。未知代码时 day 为空数组。
     */
    fun parseTencentKline(body: String, code: String): List<SeriesPoint> {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
        val data = root["data"] as? JsonObject ?: return emptyList()
        val node = data[code] as? JsonObject ?: return emptyList()
        val rows = (node["qfqday"] as? JsonArray) ?: (node["day"] as? JsonArray) ?: return emptyList()
        return rows.mapNotNull { row ->
            val cols = row as? JsonArray ?: return@mapNotNull null
            if (cols.size < 3) return@mapNotNull null
            val dateText = (cols[0] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val day = parseDate(dateText, DateTimeFormatter.ISO_LOCAL_DATE) ?: return@mapNotNull null
            val close = (cols[2] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
            if (!isValidValue(close)) return@mapNotNull null
            SeriesPoint(day, close)
        }.normalized()
    }

    /**
     * OKX `/market/candles?instId=BTC-USDT&bar=1Dutc`：
     * `{"code":"0","data":[["1795564800000","开","高","低","收",...],...]}`，ts 为 UTC 零点毫秒，
     * **收盘价在下标 4**，按时间倒序。code 非 "0" → 空列表。
     */
    fun parseOkxCandles(body: String): List<SeriesPoint> {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
        if (root.stringOrNull("code") != "0") return emptyList()
        val data = root["data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { row ->
            val cols = row as? JsonArray ?: return@mapNotNull null
            if (cols.size < 5) return@mapNotNull null
            val ms = (cols[0] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
            val close = (cols[4] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
            if (!isValidValue(close)) return@mapNotNull null
            SeriesPoint(Math.floorDiv(ms, MILLIS_PER_DAY), close)
        }.normalized()
    }

    private fun parseCsv(body: String, row: (List<String>) -> SeriesPoint?): List<SeriesPoint> =
        body.lineSequence()
            .drop(1) // 表头
            .mapNotNull { line ->
                val cols = line.trim().split(',').map { it.trim() }
                if (cols.size < 2) null else row(cols)
            }
            .filter { isValidValue(it.value) }
            .toList()
            .normalized()

    private fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    /** 解析失败返回 null，不抛。 */
    private fun parseDate(text: String, formatter: DateTimeFormatter): Long? =
        try {
            LocalDate.parse(text, formatter).toEpochDay()
        } catch (_: DateTimeParseException) {
            null
        }

    /** 价格与波动率指数必须为有限正数。 */
    internal fun isValidValue(v: Double): Boolean = v.isFinite() && v > 0.0

    /** 升序 + 同日去重（保留最后一条）。 */
    internal fun List<SeriesPoint>.normalized(): List<SeriesPoint> =
        associateBy { it.epochDay }.values.sortedBy { it.epochDay }
}
