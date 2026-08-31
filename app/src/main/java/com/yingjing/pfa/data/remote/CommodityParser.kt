package com.yingjing.pfa.data.remote

/**
 * 解析新浪外盘现货（hf_ 系列，如伦敦金 hf_XAU / 伦敦银 hf_XAG）→ 代码 → 当日涨跌%。
 *
 * hf_ 行 payload 为逗号分隔：`名称,开,高,低,昨收,买,卖,最新价,…`
 * 涨跌% = (最新价[7] − 昨收[4]) / 昨收 × 100。
 *
 * 空 payload（停牌 / 无效代码）或昨收≤0 跳过。
 */
object CommodityParser {

    private val LINE = Regex("hq_str_(.+?)=\"([^\"]*)\"")

    fun parse(body: String): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        LINE.findAll(body).forEach { match ->
            val code = match.groupValues[1]
            val payload = match.groupValues[2]
            if (payload.isBlank()) return@forEach
            changePercent(payload.split(","))?.let { result[code] = it }
        }
        return result
    }

    private fun changePercent(f: List<String>): Double? {
        val prev = f.getOrNull(4)?.toDoubleOrNull() ?: return null
        val cur = f.getOrNull(7)?.toDoubleOrNull() ?: return null
        return if (prev > 0.0) (cur - prev) / prev * 100.0 else null
    }
}
