package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.model.FxRates

/** 解析新浪外汇响应 → FxRates（最新价为逗号分隔第 8 字段）。 */
object SinaFxParser {

    private val LINE = Regex("""hq_str_(fx_s[a-z]+)="([^"]*)"""")
    private const val LATEST_INDEX = 8

    fun parse(body: String): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        LINE.findAll(body).forEach { match ->
            val code = match.groupValues[1]
            val rate = match.groupValues[2].split(",").getOrNull(LATEST_INDEX)?.toDoubleOrNull()
            if (rate != null && rate > 0) result[code] = rate
        }
        return result
    }

    fun toFxRates(body: String): FxRates {
        val map = parse(body)
        return FxRates(
            usdToCny = map["fx_susdcny"] ?: 1.0,
            hkdToCny = map["fx_shkdcny"] ?: 1.0,
        )
    }
}
