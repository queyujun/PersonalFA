package com.yingjing.pfa.data.remote

import androidx.annotation.StringRes
import com.yingjing.pfa.R
import com.yingjing.pfa.core.i18n.StringResolver

/**
 * 关注的国际行情指标（代码 → 展示名资源）。
 *
 * - `usd_cny` / `hkd_cny`：汇率，涨跌% 由 SyncManager 用「上次同步值 vs 当前值」算出（跨周期）。
 * - `hf_XAU` / `hf_XAG`：伦敦金 / 伦敦银现货，涨跌% 由 [CommodityParser] 用新浪官方昨收 vs 最新价算出（当日）。
 */
object GlobalIndicators {
    /** 代码 → 本地化展示名资源 ID（顺序保持稳定）。 */
    val DISPLAY_RES: Map<String, Int> = linkedMapOf(
        "usd_cny" to R.string.gi_usd_cny,
        "hkd_cny" to R.string.gi_hkd_cny,
        "hf_XAU" to R.string.gi_gold,
        "hf_XAG" to R.string.gi_silver,
    )

    /** 汇率代码（涨跌% 由 SyncManager 计算，不走 CommodityRemote）。 */
    val FX_CODES: List<String> = listOf("usd_cny", "hkd_cny")

    /** 贵金属代码（涨跌% 由 CommodityRemote 抓取新浪 hf_ 行情算出）。 */
    val METAL_CODES: List<String> = listOf("hf_XAU", "hf_XAG")

    /** 按当前 locale 解析展示名（供国际行情提醒使用）。 */
    fun displayNames(@Suppress("UNUSED_PARAMETER") resolver: StringResolver): Map<String, String> =
        DISPLAY_RES.mapValues { (_, @StringRes resId) -> resolver.get(resId) }
}
