package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Holding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LiabilityRepaymentTest {

    private fun ms(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun liability(
        principal: Double,
        monthly: Double?,
        repaymentDay: Int?,
        createdY: Int,
        createdM: Int,
        createdD: Int,
        lastRepaidYearMonth: Int? = null,
    ) = Holding(
        userId = 1,
        type = AssetType.LIABILITY,
        name = "房贷",
        currency = Currency.CNY,
        manualValue = principal,
        monthlyPayment = monthly,
        repaymentDay = repaymentDay,
        lastRepaidYearMonth = lastRepaidYearMonth,
        createdAtEpochMs = ms(createdY, createdM, createdD),
    )

    @Test
    fun firstSettlement_singlePeriod() {
        val h = liability(10000.0, 1000.0, 15, 2026, 1, 10)
        val r = LiabilityRepayment.settle(h, ms(2026, 1, 20))!!
        assertEquals(9000.0, r.manualValue!!, 0.001)
        assertEquals(202601, r.lastRepaidYearMonth)
    }

    @Test
    fun catchesUpMissedMonths() {
        val h = liability(10000.0, 1000.0, 15, 2026, 1, 10)
        // 1/15、2/15、3/15、4/15 共 4 期
        val r = LiabilityRepayment.settle(h, ms(2026, 4, 20))!!
        assertEquals(6000.0, r.manualValue!!, 0.001)
        assertEquals(202604, r.lastRepaidYearMonth)
    }

    @Test
    fun clampsToZero_whenPrincipalRunsOut() {
        val h = liability(2500.0, 1000.0, 15, 2026, 1, 10)
        // 需扣 4000 > 2500，扣平到 0
        val r = LiabilityRepayment.settle(h, ms(2026, 4, 20))!!
        assertEquals(0.0, r.manualValue!!, 0.001)
    }

    @Test
    fun notYetDue_returnsNull() {
        val h = liability(10000.0, 1000.0, 15, 2026, 1, 10)
        assertNull(LiabilityRepayment.settle(h, ms(2026, 1, 12))) // 1/15 未到
    }

    @Test
    fun noDoubleCharge_withinSameMonth() {
        val h = liability(10000.0, 1000.0, 15, 2026, 1, 10, lastRepaidYearMonth = 202604)
        assertNull(LiabilityRepayment.settle(h, ms(2026, 4, 25))) // 已结算到 4 月、5 月未到
    }

    @Test
    fun defaultsToLastDayOfMonth_whenNoRepaymentDay() {
        val h = liability(5000.0, 1000.0, null, 2026, 1, 10)
        // 1/31 已过、2/28 未到 → 1 期
        val r = LiabilityRepayment.settle(h, ms(2026, 2, 1))!!
        assertEquals(4000.0, r.manualValue!!, 0.001)
        assertEquals(202601, r.lastRepaidYearMonth)
    }

    @Test
    fun day31_fallsToLastDayInShortMonth() {
        val h = liability(5000.0, 1000.0, 31, 2026, 1, 10, lastRepaidYearMonth = 202601)
        // 2 月落到 2/28 → 1 期
        val r = LiabilityRepayment.settle(h, ms(2026, 2, 28))!!
        assertEquals(4000.0, r.manualValue!!, 0.001)
        assertEquals(202602, r.lastRepaidYearMonth)
    }

    @Test
    fun nonLiability_returnsNull() {
        val stock = Holding(
            userId = 1, type = AssetType.A_SHARE, name = "茅台", currency = Currency.CNY,
            quantity = 1.0, currentPrice = 100.0, createdAtEpochMs = ms(2026, 1, 1),
        )
        assertNull(LiabilityRepayment.settle(stock, ms(2026, 6, 1)))
    }

    @Test
    fun noMonthlyPrincipal_returnsNull() {
        val h = liability(10000.0, null, 15, 2026, 1, 10)
        assertNull(LiabilityRepayment.settle(h, ms(2026, 6, 1)))
    }
}
