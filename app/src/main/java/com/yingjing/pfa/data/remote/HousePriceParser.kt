package com.yingjing.pfa.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 解析东方财富 RPT_ECONOMY_HOUSE_PRICE（国家统计局 70 城住宅价格指数）JSON → [List<HousePricePoint>]。
 * 字段可能为 null（部分城市个别月份无数据），解析时保留为 null。
 */
object HousePriceParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): List<HousePricePoint> {
        val response = runCatching { json.decodeFromString<HousePriceResponse>(body) }.getOrNull()
        val records = response?.result?.data ?: return emptyList()
        return records.mapNotNull { record ->
            val city = record.city ?: return@mapNotNull null
            val rawDate = record.reportDate ?: return@mapNotNull null
            // REPORT_DATE 形如 "2026-07-01 00:00:00"，取 "yyyy-MM"
            val month = rawDate.substringBefore(" ").take(7)
            if (month.length < 7) return@mapNotNull null
            HousePricePoint(
                city = city,
                month = month,
                newSequential = record.newSequential,
                newSame = record.newSame,
                secondSequential = record.secondSequential,
                secondSame = record.secondSame,
            )
        }
    }

    @Serializable
    private data class HousePriceResponse(val result: HousePriceResult? = null)

    @Serializable
    private data class HousePriceResult(val data: List<HousePriceRecord> = emptyList())

    @Serializable
    private data class HousePriceRecord(
        @SerialName("CITY") val city: String? = null,
        @SerialName("REPORT_DATE") val reportDate: String? = null,
        @SerialName("FIRST_COMHOUSE_SEQUENTIAL") val newSequential: Double? = null,
        @SerialName("FIRST_COMHOUSE_SAME") val newSame: Double? = null,
        @SerialName("SECOND_HOUSE_SEQUENTIAL") val secondSequential: Double? = null,
        @SerialName("SECOND_HOUSE_SAME") val secondSame: Double? = null,
    )
}
