package com.yingjing.pfa.domain.alert

import com.yingjing.pfa.domain.model.AlertSeverity
import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Holding
import com.yingjing.pfa.fakes.FakeStringResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRulesTest {

    private val resolver = FakeStringResolver()
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun deposit(id: Long, maturityMs: Long) = Holding(
        id = id, userId = 1, type = AssetType.DEPOSIT, name = "三年定期", currency = Currency.CNY,
        manualValue = 100_000.0, annualRatePercent = 2.0, maturityDateEpochMs = maturityMs,
    )

    private fun stock(id: Long) = Holding(
        id = id, userId = 1, type = AssetType.A_SHARE, name = "贵州茅台", currency = Currency.CNY,
        symbol = "600519", quantity = 100.0,
    )

    @Test
    fun depositMaturity_within7Days_warning() {
        val alerts = AlertRules.depositMaturity(listOf(deposit(1, now + 5 * day)), now, resolver)
        assertEquals(1, alerts.size)
        assertEquals(AlertSeverity.WARNING, alerts[0].severity)
    }

    @Test
    fun depositMaturity_within30Days_info() {
        val alerts = AlertRules.depositMaturity(listOf(deposit(1, now + 20 * day)), now, resolver)
        assertEquals(1, alerts.size)
        assertEquals(AlertSeverity.INFO, alerts[0].severity)
    }

    @Test
    fun depositMaturity_far_noAlert() {
        assertTrue(AlertRules.depositMaturity(listOf(deposit(1, now + 60 * day)), now, resolver).isEmpty())
    }

    @Test
    fun depositMaturity_past_noAlert() {
        assertTrue(AlertRules.depositMaturity(listOf(deposit(1, now - 5 * day)), now, resolver).isEmpty())
    }

    @Test
    fun priceMove_aboveThreshold_alerts() {
        val alerts = AlertRules.priceMoves(listOf(PriceChange(stock(2), 100.0, 110.0)), now, resolver)
        assertEquals(1, alerts.size)
        assertTrue(alerts[0].title.contains("贵州茅台"))
    }

    @Test
    fun priceMove_belowThreshold_noAlert() {
        assertTrue(AlertRules.priceMoves(listOf(PriceChange(stock(2), 100.0, 103.0)), now, resolver).isEmpty())
    }

    @Test
    fun priceMove_zeroOldPrice_skipped() {
        assertTrue(AlertRules.priceMoves(listOf(PriceChange(stock(2), 0.0, 110.0)), now, resolver).isEmpty())
    }

    @Test
    fun priceMove_dedupKey_isPerDay() {
        val alerts = AlertRules.priceMoves(listOf(PriceChange(stock(2), 100.0, 90.0)), now, resolver)
        assertTrue(alerts[0].dedupKey.startsWith("price_move_2_"))
    }

    @Test
    fun marketMove_aboveThreshold_alerts() {
        val alerts = AlertRules.marketMoves(
            userId = 1,
            changes = mapOf("sh000300" to -3.5),
            names = mapOf("sh000300" to "沪深300"),
            nowMs = now,
            resolver = resolver,
        )
        assertEquals(1, alerts.size)
        assertTrue(alerts[0].title.contains("沪深300"))
        // pctText 透传：abs(-3.5) → "3.50"，证明涨跌幅度被正确计算并落入文案。
        assertTrue(alerts[0].title.contains("3.50"))
    }

    @Test
    fun marketMove_belowThreshold_noAlert() {
        val alerts = AlertRules.marketMoves(
            userId = 1,
            changes = mapOf("sh000300" to -1.2),
            names = mapOf("sh000300" to "沪深300"),
            nowMs = now,
            resolver = resolver,
        )
        assertTrue(alerts.isEmpty())
    }
}
