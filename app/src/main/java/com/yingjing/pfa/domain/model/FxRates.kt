package com.yingjing.pfa.domain.model

/**
 * 汇率（以人民币 CNY 为基准：1 单位外币 = ? 人民币）。同时充当货币换算器。
 * 默认 1.0（未获取到汇率时退化为不换算，避免崩溃）。
 */
data class FxRates(
    val usdToCny: Double = 1.0,
    val hkdToCny: Double = 1.0,
) {
    fun toCny(amount: Double, from: Currency): Double = when (from) {
        Currency.CNY -> amount
        Currency.USD -> amount * usdToCny
        Currency.HKD -> amount * hkdToCny
    }

    fun fromCny(cnyAmount: Double, to: Currency): Double = when (to) {
        Currency.CNY -> cnyAmount
        Currency.USD -> if (usdToCny == 0.0) 0.0 else cnyAmount / usdToCny
        Currency.HKD -> if (hkdToCny == 0.0) 0.0 else cnyAmount / hkdToCny
    }

    fun convert(amount: Double, from: Currency, to: Currency): Double =
        if (from == to) amount else fromCny(toCny(amount, from), to)
}
