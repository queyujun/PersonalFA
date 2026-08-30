package com.yingjing.pfa.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {

    @Test
    fun formatWholeRoundsHalfUpAndKeepsThousandsSeparator() {
        // 四舍五入：1,234.56 → 1,235；1,234.49 → 1,234
        assertEquals("¥ 1,235", MoneyFormat.formatWhole(1_234.56, "¥"))
        assertEquals("¥ 1,234", MoneyFormat.formatWhole(1_234.49, "¥"))
    }

    @Test
    fun formatWholeStripsDecimalsForWholeAmount() {
        assertEquals("¥ 12,300", MoneyFormat.formatWhole(12_300.0, "¥"))
    }

    @Test
    fun formatWholeHandlesNegativeWithSpaceAfterSymbol() {
        // -3,000.7 四舍五入取整 → -3,001
        assertEquals("¥ -3,001", MoneyFormat.formatWhole(-3_000.7, "¥"))
    }

    @Test
    fun formatWholeAppliesCurrencySymbol() {
        assertEquals("US$ 1,000", MoneyFormat.formatWhole(1_000.0, "US$"))
        assertEquals("HK$ 500", MoneyFormat.formatWhole(500.49, "HK$"))
    }

    @Test
    fun formatFixed2AlwaysShowsTwoDecimals() {
        // 整数补 .00；一位小数补零到两位
        assertEquals("¥ 1,234.00", MoneyFormat.formatFixed2(1_234.0, "¥"))
        assertEquals("¥ 1,234.50", MoneyFormat.formatFixed2(1_234.5, "¥"))
        // 已两位小数不变
        assertEquals("¥ 1,234.56", MoneyFormat.formatFixed2(1_234.56, "¥"))
    }

    @Test
    fun formatFixed2KeepsThousandsSeparatorAndSymbol() {
        assertEquals("US$ 1,000,000.00", MoneyFormat.formatFixed2(1_000_000.0, "US$"))
        assertEquals("¥ -3,001.00", MoneyFormat.formatFixed2(-3_001.0, "¥"))
    }

    @Test
    fun wanFixed2AlwaysShowsTwoDecimals() {
        // 1,750,000 → 175.00 万；1,755,000 → 175.50 万
        assertEquals("175.00", MoneyFormat.wanFixed2(1_750_000.0))
        assertEquals("175.50", MoneyFormat.wanFixed2(1_755_000.0))
        // 整万也补 .00
        assertEquals("200.00", MoneyFormat.wanFixed2(2_000_000.0))
    }

    @Test
    fun wanFixed2KeepsThousandsSeparator() {
        // 175,000,000 → 17,500.00 万（千分位保留）
        assertEquals("17,500.00", MoneyFormat.wanFixed2(175_000_000.0))
    }
}
