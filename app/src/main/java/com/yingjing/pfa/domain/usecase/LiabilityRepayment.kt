package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Holding
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * 负债自动还款（纯函数，可单测）。
 *
 * 每次结算把「上次扣款年月」之后、到 [nowMs] 为止、所有已过还款日的月份都补扣一遍：
 * - 每期从欠款本金（[Holding.manualValue]）扣除「每月还款本金」（[Holding.monthlyPayment]）；
 * - 还款日 = [Holding.repaymentDay]（1–31，未设为当月最后一天；超过当月天数则落到最后一天）；
 * - 扣到 0 为止（不足一期扣平后结清）；
 * - 用 [Holding.lastRepaidYearMonth]（YYYYMM）记录已结算到的月，避免重复扣。
 *
 * 仅处理负债；无可扣期数返回 null（调用方保留原值）。日期按 UTC 计算，与净值快照口径一致。
 */
object LiabilityRepayment {

    private val ZONE = ZoneOffset.UTC

    fun settle(holding: Holding, nowMs: Long): Holding? {
        if (holding.type != AssetType.LIABILITY) return null
        val principal = holding.manualValue ?: return null
        val monthly = holding.monthlyPayment ?: return null
        if (principal <= 0.0 || monthly <= 0.0) return null

        // 从「已结算到的年月」开始；首次以创建月的上个月为基准，使创建后的第一个还款日能纳入。
        var settledYm = holding.lastRepaidYearMonth ?: prevMonth(yearMonthOf(holding.createdAtEpochMs))
        var ym = settledYm
        var periods = 0
        while (true) {
            ym = nextMonth(ym)
            val dueMs = dueMillis(ym, holding.repaymentDay)
            if (dueMs > nowMs) break
            if (dueMs > holding.createdAtEpochMs) periods++
            settledYm = ym
        }
        if (periods == 0) return null

        val pay = minOf(principal, periods * monthly)
        return holding.copy(manualValue = principal - pay, lastRepaidYearMonth = settledYm)
    }

    private fun yearMonthOf(ms: Long): Int {
        val d = LocalDate.ofInstant(Instant.ofEpochMilli(ms), ZONE)
        return d.year * 100 + d.monthValue
    }

    private fun nextMonth(ym: Int): Int = shift(ym, 1)
    private fun prevMonth(ym: Int): Int = shift(ym, -1)

    private fun shift(ym: Int, months: Long): Int {
        val shifted = YearMonth.of(ym / 100, ym % 100).plusMonths(months)
        return shifted.year * 100 + shifted.monthValue
    }

    private fun dueMillis(ym: Int, repaymentDay: Int?): Long {
        val year = ym / 100
        val month = ym % 100
        val lastDay = YearMonth.of(year, month).lengthOfMonth()
        val day = (repaymentDay ?: lastDay).coerceIn(1, lastDay)
        return LocalDate.of(year, month, day).atStartOfDay(ZONE).toInstant().toEpochMilli()
    }
}
