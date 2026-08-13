package com.yingjing.pfa.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HoldingValueTest {

    private val now = 1_700_000_000_000L

    private fun stock(quantity: Double, cost: Double?, price: Double?) = Holding(
        userId = 1,
        type = AssetType.A_SHARE,
        name = "贵州茅台",
        currency = Currency.CNY,
        symbol = "600519",
        quantity = quantity,
        costPrice = cost,
        currentPrice = price,
    )

    @Test
    fun market_usesCurrentPrice() {
        assertEquals(168_000.0, HoldingValue.currentValue(stock(100.0, 1650.0, 1680.0), now), 0.001)
    }

    @Test
    fun market_fallsBackToCost_whenNoCurrentPrice() {
        assertEquals(165_000.0, HoldingValue.currentValue(stock(100.0, 1650.0, null), now), 0.001)
    }

    @Test
    fun liability_isNegative() {
        val loan = Holding(
            userId = 1, type = AssetType.LIABILITY, name = "房贷",
            currency = Currency.CNY, manualValue = 180_000.0,
        )
        assertEquals(-180_000.0, HoldingValue.currentValue(loan, now), 0.001)
    }

    @Test
    fun manualTypes_useManualValue() {
        val house = Holding(
            userId = 1, type = AssetType.REAL_ESTATE, name = "滨江一号",
            currency = Currency.CNY, manualValue = 1_750_000.0,
        )
        assertEquals(1_750_000.0, HoldingValue.currentValue(house, now), 0.001)
    }

    @Test
    fun deposit_accruesSimpleInterest() {
        val oneYearAgo = now - 365L * 86_400_000L
        val deposit = Holding(
            userId = 1, type = AssetType.DEPOSIT, name = "三年定期",
            currency = Currency.CNY, manualValue = 100_000.0,
            annualRatePercent = 2.0, startDateEpochMs = oneYearAgo,
        )
        // 100000 + 100000 * 2% * 1年 = 102000
        assertEquals(102_000.0, HoldingValue.currentValue(deposit, now), 1.0)
    }

    @Test
    fun profit_forMarket_isCurrentMinusCost() {
        assertEquals(3_000.0, HoldingValue.profit(stock(100.0, 1650.0, 1680.0), now)!!, 0.001)
    }

    @Test
    fun profit_isNull_whenNoCost() {
        assertNull(HoldingValue.profit(stock(100.0, null, 1680.0), now))
    }
}
