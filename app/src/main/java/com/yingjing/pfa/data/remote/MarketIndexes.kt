package com.yingjing.pfa.data.remote

import androidx.annotation.StringRes
import com.yingjing.pfa.R
import com.yingjing.pfa.core.i18n.StringResolver

/** 关注的主要市场指数（新浪代码 → 展示名资源）。 */
object MarketIndexes {
    /** 指数代码 → 本地化展示名资源 ID（顺序保持稳定）。 */
    val DISPLAY_RES: Map<String, Int> = linkedMapOf(
        "sh000300" to R.string.idx_csi300,
        "rt_hkHSI" to R.string.idx_hsi,
        "gb_\$inx" to R.string.idx_spx,
    )
    val CODES: List<String> get() = DISPLAY_RES.keys.toList()

    /** 按当前 locale 解析指数展示名（供市场波动提醒使用）。 */
    fun displayNames(@Suppress("UNUSED_PARAMETER") resolver: StringResolver): Map<String, String> =
        DISPLAY_RES.mapValues { (_, @StringRes resId) -> resolver.get(resId) }
}
