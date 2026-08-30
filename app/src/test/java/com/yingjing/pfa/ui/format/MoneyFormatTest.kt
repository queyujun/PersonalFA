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
}
