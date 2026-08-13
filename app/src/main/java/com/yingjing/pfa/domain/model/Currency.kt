package com.yingjing.pfa.domain.model

/** 计价货币。 */
enum class Currency(val code: String, val symbol: String, val label: String) {
    CNY("CNY", "¥", "人民币"),
    HKD("HKD", "HK$", "港币"),
    USD("USD", "US$", "美元");

    companion object {
        fun fromCode(code: String): Currency = entries.firstOrNull { it.code == code } ?: CNY
    }
}
