package com.yingjing.pfa.ui.format

import com.yingjing.pfa.domain.model.Currency
import java.text.DecimalFormat
import kotlin.math.abs

/** 金额格式化：带货币符号与千分位。 */
object MoneyFormat {

    fun format(amount: Double, currency: Currency): String {
        val formatter = DecimalFormat("#,##0.##")
        val sign = if (amount < 0) "-" else ""
        return "${currency.symbol} $sign${formatter.format(abs(amount))}"
    }

    /** 带正负号（用于收益）。 */
    fun formatSigned(amount: Double, currency: Currency): String {
        val formatter = DecimalFormat("#,##0.##")
        val sign = if (amount > 0) "+" else if (amount < 0) "-" else ""
        return "$sign${currency.symbol}${formatter.format(abs(amount))}"
    }

    /** 以「万」为单位（如 1,750,000 → 175）。返回不含币种符号的数字文本。 */
    fun wan(amount: Double): String = DecimalFormat("#,##0.#").format(amount / 10_000.0)
}
