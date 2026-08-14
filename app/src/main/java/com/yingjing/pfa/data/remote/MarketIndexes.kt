package com.yingjing.pfa.data.remote

/** 关注的主要市场指数（新浪代码 → 展示名）。 */
object MarketIndexes {
    val NAMES: Map<String, String> = linkedMapOf(
        "sh000300" to "沪深300",
        "rt_hkHSI" to "恒生指数",
        "gb_\$inx" to "标普500",
    )
    val CODES: List<String> get() = NAMES.keys.toList()
}
