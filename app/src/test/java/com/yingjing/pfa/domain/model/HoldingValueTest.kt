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

    private fun otcFund(quantity: Double, cost: Double?, price: Double?) = Holding(
        userId = 1,
        type = AssetType.OTC_FUND,
        name = "易方达蓝筹",
        currency = Currency.CNY,
        symbol = "005827",
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

    @Test
    fun realEstate_withEstimate_usesEstimatedValue() {
        val house = Holding(
            userId = 1, type = AssetType.REAL_ESTATE, name = "滨江一号",
            currency = Currency.CNY, manualValue = 1_000_000.0,
            autoEstimate = true, estimatedValue = 1_050_000.0,
        )
        assertEquals(1_050_000.0, HoldingValue.currentValue(house, now), 0.001)
    }

    @Test
    fun realEstate_estimateOff_fallsBackToManualValue() {
        val house = Holding(
            userId = 1, type = AssetType.REAL_ESTATE, name = "滨江一号",
            currency = Currency.CNY, manualValue = 1_000_000.0,
            autoEstimate = false, estimatedValue = 1_050_000.0,
        )
        assertEquals(1_000_000.0, HoldingValue.currentValue(house, now), 0.001)
    }

    @Test
    fun realEstate_profit_isEstimatedMinusManual() {
        val house = Holding(
            userId = 1, type = AssetType.REAL_ESTATE, name = "滨江一号",
            currency = Currency.CNY, manualValue = 1_000_000.0,
            autoEstimate = true, estimatedValue = 1_050_000.0,
        )
        assertEquals(50_000.0, HoldingValue.profit(house, now)!!, 0.001)
    }

    @Test
    fun realEstate_profitNull_whenEstimateOff() {
        val house = Holding(
            userId = 1, type = AssetType.REAL_ESTATE, name = "滨江一号",
            currency = Currency.CNY, manualValue = 1_000_000.0,
        )
        assertNull(HoldingValue.profit(house, now))
    }

    @Test
    fun otc_fund_usesNavTimesQuantity() {
        // 1000 份 × 当前净值 1.68 = 1680
        assertEquals(1_680.0, HoldingValue.currentValue(otcFund(1_000.0, 1.50, 1.68), now), 0.001)
    }

    @Test
    fun otc_fund_fallsBackToCost_whenNoCurrentNav() {
        // 无当前净值时回退成本净值：1000 份 × 1.50 = 1500
        assertEquals(1_500.0, HoldingValue.currentValue(otcFund(1_000.0, 1.50, null), now), 0.001)
    }

    @Test
    fun otc_fund_profit_isCurrentMinusCost() {
        // (1.68 − 1.50) × 1000 = 180
        assertEquals(180.0, HoldingValue.profit(otcFund(1_000.0, 1.50, 1.68), now)!!, 0.001)
    }

    @Test
    fun otc_fund_profitNull_whenNoCost() {
        assertNull(HoldingValue.profit(otcFund(1_000.0, null, 1.68), now))
    }

    // —— 实物金：quantity=克数，currentPrice/costPrice=每克价，市值与盈亏同市场型公式 ——

    private fun physicalGold(grams: Double, costPerGram: Double?, currentPerGram: Double?) = Holding(
        userId = 1,
        type = AssetType.PHYSICAL_GOLD,
        name = "金条",
        currency = Currency.CNY,
        quantity = grams,
        costPrice = costPerGram,
        currentPrice = currentPerGram,
    )

    @Test
    fun physicalGold_usesCurrentPriceTimesGrams() {
        // 10 克 × 每克 450.0 = 4500
        assertEquals(4_500.0, HoldingValue.currentValue(physicalGold(10.0, 400.0, 450.0), now), 0.001)
    }

    @Test
    fun physicalGold_fallsBackToCost_whenNoCurrentPrice() {
        // 无现价回退成本价：10 克 × 400.0 = 4000
        assertEquals(4_000.0, HoldingValue.currentValue(physicalGold(10.0, 400.0, null), now), 0.001)
    }

    @Test
    fun physicalGold_profit_isCurrentMinusCost() {
        // (450 − 400) × 10 = 500
        assertEquals(500.0, HoldingValue.profit(physicalGold(10.0, 400.0, 450.0), now)!!, 0.001)
    }

    @Test
    fun physicalGold_profitNull_whenNoCost() {
        assertNull(HoldingValue.profit(physicalGold(10.0, null, 450.0), now))
    }

    // —— 其他(MISC)：手动估值，取 manualValue；无成本口径，收益返回 null ——

    private fun misc(value: Double, note: String? = null) = Holding(
        userId = 1,
        type = AssetType.MISC,
        name = "收藏品",
        currency = Currency.CNY,
        manualValue = value,
        note = note,
    )

    @Test
    fun misc_usesManualValue() {
        assertEquals(12_345.0, HoldingValue.currentValue(misc(12_345.0), now), 0.001)
    }

    @Test
    fun misc_profitNull() {
        assertNull(HoldingValue.profit(misc(12_345.0, "老物件"), now))
    }
}
