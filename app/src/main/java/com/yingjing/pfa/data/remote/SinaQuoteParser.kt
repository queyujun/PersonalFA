package com.yingjing.pfa.data.remote

/**
 * 解析新浪行情响应文本 → 新浪代码 → 现价。
 *
 * 现价字段位（逗号分隔）随市场不同：
 * - A股 / ETF（sh/sz）：索引 3
 * - 港股（rt_hk）：索引 6
 * - 美股（gb_）：索引 1
 *
 * 空 payload（停牌 / 无效代码）跳过。
 */
object SinaQuoteParser {

    private val LINE = Regex("""hq_str_([a-zA-Z0-9_]+)="([^"]*)"""")

    fun parse(body: String): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        LINE.findAll(body).forEach { match ->
            val code = match.groupValues[1]
            val payload = match.groupValues[2]
            if (payload.isBlank()) return@forEach
            val price = priceOf(code, payload.split(",")) ?: return@forEach
            if (price > 0) result[code] = price
        }
        return result
    }

    private fun priceOf(code: String, fields: List<String>): Double? {
        val index = when {
            code.startsWith("gb_") -> 1
            code.startsWith("rt_hk") -> 6
            code.startsWith("sh") || code.startsWith("sz") -> 3
            else -> return null
        }
        return fields.getOrNull(index)?.toDoubleOrNull()
    }
}
