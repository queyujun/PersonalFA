package com.yingjing.pfa.domain.model

import androidx.annotation.StringRes
import com.yingjing.pfa.R

/** 计价货币。 */
enum class Currency(
    val code: String,
    @StringRes val symbolRes: Int,
    @StringRes val labelRes: Int,
) {
    CNY("CNY", R.string.cur_symbol_cny, R.string.cur_label_cny),
    HKD("HKD", R.string.cur_symbol_hkd, R.string.cur_label_hkd),
    USD("USD", R.string.cur_symbol_usd, R.string.cur_label_usd);

    companion object {
        fun fromCode(code: String): Currency = entries.firstOrNull { it.code == code } ?: CNY
    }
}
