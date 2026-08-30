package com.yingjing.pfa.ui.format

import com.yingjing.pfa.core.i18n.StringResolver
import com.yingjing.pfa.domain.model.Currency
import java.text.DecimalFormat
import kotlin.math.abs

/**
 * 金额格式化：带货币符号与千分位。
 *
 * 货币符号解析有两种入口，按调用方上下文选用：
 * - **纯函数 / VM / Worker**：用 `resolver` 重载，符号经 [StringResolver] 按当前 locale 解析。
 * - **Composable**：用 `symbol` 重载，调用方用 `stringResource(currency.symbolRes)` 取符号后传入。
 *
 * 两种入口最终都委托到 [formatSymbol]，数字格式化逻辑集中在一处。
 */
object MoneyFormat {

    fun format(amount: Double, currency: Currency, resolver: StringResolver): String =
        formatSymbol(amount, resolver.get(currency.symbolRes))

    fun format(amount: Double, symbol: String): String = formatSymbol(amount, symbol)

    private fun formatSymbol(amount: Double, symbol: String): String {
        val formatter = DecimalFormat("#,##0.##")
        val sign = if (amount < 0) "-" else ""
        return "$symbol $sign${formatter.format(abs(amount))}"
    }

    /** 固定两位小数的金额（强制显示 .00，用于需统一小数位纵向对齐的展示，如总资产/总负债并排）。 */
    fun formatFixed2(amount: Double, symbol: String): String = formatFixed2Symbol(amount, symbol)

    private fun formatFixed2Symbol(amount: Double, symbol: String): String {
        val formatter = DecimalFormat("#,##0.00")
        val sign = if (amount < 0) "-" else ""
        return "$symbol $sign${formatter.format(abs(amount))}"
    }

    /** 不带小数的金额（用于资产页分类合计，保持简洁；按四舍五入取整）。 */
    fun formatWhole(amount: Double, currency: Currency, resolver: StringResolver): String =
        formatWholeSymbol(amount, resolver.get(currency.symbolRes))

    fun formatWhole(amount: Double, symbol: String): String = formatWholeSymbol(amount, symbol)

    private fun formatWholeSymbol(amount: Double, symbol: String): String {
        val formatter = DecimalFormat("#,##0")
        val sign = if (amount < 0) "-" else ""
        return "$symbol $sign${formatter.format(abs(amount))}"
    }

    /** 带正负号（用于收益）。 */
    fun formatSigned(amount: Double, currency: Currency, resolver: StringResolver): String =
        formatSignedSymbol(amount, resolver.get(currency.symbolRes))

    fun formatSigned(amount: Double, symbol: String): String = formatSignedSymbol(amount, symbol)

    private fun formatSignedSymbol(amount: Double, symbol: String): String {
        val formatter = DecimalFormat("#,##0.##")
        val sign = if (amount > 0) "+" else if (amount < 0) "-" else ""
        return "$sign$symbol${formatter.format(abs(amount))}"
    }

    /** 以「万」为单位（如 1,750,000 → 175）。返回不含币种符号的数字文本。 */
    fun wan(amount: Double): String = DecimalFormat("#,##0.#").format(amount / 10_000.0)

    /** 以「万」为单位且固定两位小数（如 1,750,000 → 175.00），用于需统一小数位对齐的资产分布图例。 */
    fun wanFixed2(amount: Double): String = DecimalFormat("#,##0.00").format(amount / 10_000.0)
}
