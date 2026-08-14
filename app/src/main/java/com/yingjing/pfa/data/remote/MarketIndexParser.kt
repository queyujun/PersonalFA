package com.yingjing.pfa.data.remote

/**
 * 解析新浪指数行情 → 指数代码 → 当日涨跌%。
 * 各市场取值不同：
 * - A股指数（sh/sz）：由 (现价[3] − 昨收[2]) / 昨收 计算
 * - 港股指数（rt_hk）：涨跌%在第 8 字段
 * - 美股指数（gb_）：涨跌%在第 2 字段
 */
object MarketIndexParser {

    private val LINE = Regex("hq_str_(.+?)=\"([^\"]*)\"")

    fun parse(body: String): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        LINE.findAll(body).forEach { match ->
            val code = match.groupValues[1]
            val payload = match.groupValues[2]
            if (payload.isBlank()) return@forEach
            changePercent(code, payload.split(","))?.let { result[code] = it }
        }
        return result
    }

    private fun changePercent(code: String, f: List<String>): Double? = when {
        code.startsWith("gb_") -> f.getOrNull(2)?.toDoubleOrNull()
        code.startsWith("rt_hk") -> f.getOrNull(8)?.toDoubleOrNull()
        code.startsWith("sh") || code.startsWith("sz") -> {
            val prev = f.getOrNull(2)?.toDoubleOrNull()
            val cur = f.getOrNull(3)?.toDoubleOrNull()
            if (prev != null && cur != null && prev != 0.0) (cur - prev) / prev * 100.0 else null
        }
        else -> null
    }
}
