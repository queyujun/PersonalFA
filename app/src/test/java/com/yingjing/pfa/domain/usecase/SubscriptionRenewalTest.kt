package com.yingjing.pfa.domain.usecase

import com.yingjing.pfa.domain.model.BillingCycle
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.Subscription
import com.yingjing.pfa.domain.model.SubscriptionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SubscriptionRenewalTest {

    private val day = 86_400_000L

    /** nowMs 固定在 2026-09-03（UTC）。 */
    private val now = Subscription.epochMsOf(LocalDate.of(2026, 9, 3))

    private fun sub(
        id: Long = 1,
        cycle: BillingCycle = BillingCycle.MONTHLY,
        nextRenewalMs: Long,
        active: Boolean = true,
    ) = Subscription(
        id = id, userId = 1, name = "视频会员", category = SubscriptionCategory.VIDEO,
        currency = Currency.CNY, amount = 30.0, cycle = cycle,
        firstBillEpochMs = nextRenewalMs, nextRenewalEpochMs = nextRenewalMs,
        reminderDaysBefore = 3, active = active,
    )

    // ---- 月末钳制（锚定原始日，不漂移）----

    @Test
    fun monthlyAdvance_fromJan31_clampsToFeb28_thenStaysAt28() {
        val jan31 = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), BillingCycle.MONTHLY.advance(jan31))
        // 3 月有 31 天，但不回跳 31 —— 锚定顺延结果而非原始日
        assertEquals(LocalDate.of(2026, 3, 28), BillingCycle.MONTHLY.advance(LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun quarterlyAdvance_clampsToMonthEnd() {
        val may31 = LocalDate.of(2026, 5, 31)
        assertEquals(LocalDate.of(2026, 8, 31), BillingCycle.QUARTERLY.advance(may31))
    }

    @Test
    fun yearlyAdvance_feb29LeapYear_clampsToFeb28NextYear() {
        val feb29 = LocalDate.of(2028, 2, 29)
        assertEquals(LocalDate.of(2029, 2, 28), BillingCycle.YEARLY.advance(feb29))
    }

    @Test
    fun weeklyAdvance_alwaysPlus7Days() {
        assertEquals(LocalDate.of(2026, 9, 10), BillingCycle.WEEKLY.advance(LocalDate.of(2026, 9, 3)))
    }

    // ---- advance：续费日推进 ----

    @Test
    fun advance_futureRenewal_returnsNull() {
        // 续费日在未来 → 无需顺延
        assertNull(SubscriptionRenewal.advance(sub(nextRenewalMs = now + 5 * day), now))
    }

    @Test
    fun advance_inactive_returnsNull() {
        assertNull(SubscriptionRenewal.advance(sub(nextRenewalMs = now - day, active = false), now))
    }

    @Test
    fun advance_renewalToday_advancesOneCycle() {
        // 今天到期 → 顺延到下月同日
        val advanced = SubscriptionRenewal.advance(sub(nextRenewalMs = now), now)!!
        assertEquals(LocalDate.of(2026, 10, 3), Subscription.dateOf(advanced.nextRenewalEpochMs))
    }

    @Test
    fun advance_missedMultipleCycles_catchesUpInOnePass() {
        // 首扣 2026-01-10，八个多月没打开应用 → 一次顺延到 2026-09-10（第一个未来日期，不重复计费）
        val sub = sub(cycle = BillingCycle.MONTHLY, nextRenewalMs = Subscription.epochMsOf(LocalDate.of(2026, 1, 10)))
        val advanced = SubscriptionRenewal.advance(sub, now)!!
        assertEquals(LocalDate.of(2026, 9, 10), Subscription.dateOf(advanced.nextRenewalEpochMs))
    }

    @Test
    fun advance_crossYear_boundariesHandled() {
        // 12 月底跨年：01-31 钳到 02-28 后锚定 28，最终停在 2026-09-28
        val sub = sub(nextRenewalMs = Subscription.epochMsOf(LocalDate.of(2025, 12, 31)))
        val advanced = SubscriptionRenewal.advance(sub, now)!!
        val date = Subscription.dateOf(advanced.nextRenewalEpochMs)
        assertTrue(date.isAfter(LocalDate.of(2026, 9, 3)))
        assertEquals(LocalDate.of(2026, 9, 28), date)
    }

    @Test
    fun advance_preservesOtherFields() {
        val original = sub(id = 7, cycle = BillingCycle.YEARLY, nextRenewalMs = now - day)
        val advanced = SubscriptionRenewal.advance(original, now)!!
        assertEquals(7L, advanced.id)
        assertEquals("视频会员", advanced.name)
        assertEquals(BillingCycle.YEARLY, advanced.cycle)
        assertEquals(original.firstBillEpochMs, advanced.firstBillEpochMs)
        assertTrue(advanced.nextRenewalEpochMs != original.nextRenewalEpochMs)
    }

    // ---- displayRenewal：不动库的展示顺延 ----

    @Test
    fun displayRenewal_futureDate_returnsAsIs() {
        val date = SubscriptionRenewal.displayRenewal(sub(nextRenewalMs = now + 5 * day), now)
        assertEquals(LocalDate.of(2026, 9, 8), date)
    }

    @Test
    fun displayRenewal_pastDate_advancesWithoutMutating() {
        val original = sub(nextRenewalMs = Subscription.epochMsOf(LocalDate.of(2026, 1, 10)))
        val date = SubscriptionRenewal.displayRenewal(original, now)
        assertEquals(LocalDate.of(2026, 9, 10), date)
        // 不动库：原对象 nextRenewalEpochMs 保持不变
        assertEquals(Subscription.epochMsOf(LocalDate.of(2026, 1, 10)), original.nextRenewalEpochMs)
    }

    @Test
    fun displayRenewal_inactiveSameAsActive_displayOnly() {
        // 展示顺延不区分启用状态（停用的也显示其顺延日期）
        val date = SubscriptionRenewal.displayRenewal(sub(nextRenewalMs = now - day, active = false), now)
        assertEquals(LocalDate.of(2026, 10, 2), date)
    }

    // ---- 折算系数 ----

    @Test
    fun monthlyFactor_conversion() {
        assertEquals(52.0 / 12.0, BillingCycle.WEEKLY.monthlyFactor, 1e-9)
        assertEquals(1.0, BillingCycle.MONTHLY.monthlyFactor, 1e-9)
        assertEquals(1.0 / 3.0, BillingCycle.QUARTERLY.monthlyFactor, 1e-9)
        assertEquals(1.0 / 12.0, BillingCycle.YEARLY.monthlyFactor, 1e-9)
    }

    @Test
    fun subscription_monthlyAndYearly_derived() {
        val yearlySub = sub(cycle = BillingCycle.YEARLY, nextRenewalMs = now).copy(amount = 348.0)
        assertEquals(29.0, yearlySub.monthlyAmount, 1e-9)
        assertEquals(348.0, yearlySub.yearlyAmount, 1e-9)

        val weeklySub = sub(cycle = BillingCycle.WEEKLY, nextRenewalMs = now).copy(amount = 15.0)
        assertEquals(15.0 * 52.0 / 12.0, weeklySub.monthlyAmount, 1e-9)
    }

    @Test
    fun epochRoundTrip_sameDate() {
        val date = LocalDate.of(2026, 9, 3)
        assertEquals(date, Subscription.dateOf(Subscription.epochMsOf(date)))
        // UTC 当日零点：整除一天
        assertEquals(0, Subscription.epochMsOf(date) % day)
    }

    @Test
    fun fromName_unknownFallsBack() {
        assertEquals(BillingCycle.MONTHLY, BillingCycle.fromName("NOPE"))
        assertEquals(SubscriptionCategory.OTHER, SubscriptionCategory.fromName("NOPE"))
        assertFalse(BillingCycle.fromName("WEEKLY") == BillingCycle.MONTHLY)
    }
}
