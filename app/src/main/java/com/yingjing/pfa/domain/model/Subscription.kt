package com.yingjing.pfa.domain.model

import androidx.annotation.StringRes
import com.yingjing.pfa.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 订阅分类（预置 10 类，覆盖常见订阅场景）。
 */
enum class SubscriptionCategory(@StringRes val displayRes: Int) {
    VIDEO(R.string.sub_cat_video),
    MUSIC(R.string.sub_cat_music),
    AI(R.string.sub_cat_ai),
    CLOUD(R.string.sub_cat_cloud),
    SOFTWARE(R.string.sub_cat_software),
    SHOPPING(R.string.sub_cat_shopping),
    NEWS(R.string.sub_cat_news),
    GAME(R.string.sub_cat_game),
    FITNESS(R.string.sub_cat_fitness),
    OTHER(R.string.sub_cat_other);

    companion object {
        fun fromName(name: String): SubscriptionCategory =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/**
 * 计费周期。
 *
 * - [monthlyFactor]：折算月均的系数（周 ×52÷12≈4.33、月 ×1、季 ÷3、年 ÷12）。
 * - [advance]：续费日顺延（月末钳制：1 月 31 日 → 2 月 28 日 → 3 月 28 日，锚定原始日不漂移）。
 */
enum class BillingCycle(@StringRes val displayRes: Int, val monthlyFactor: Double) {
    WEEKLY(R.string.sub_cycle_weekly, 52.0 / 12.0),
    MONTHLY(R.string.sub_cycle_monthly, 1.0),
    QUARTERLY(R.string.sub_cycle_quarterly, 1.0 / 3.0),
    YEARLY(R.string.sub_cycle_yearly, 1.0 / 12.0);

    /** 从 [from] 起顺延一个周期，返回新续费日。 */
    fun advance(from: LocalDate): LocalDate = when (this) {
        WEEKLY -> from.plusWeeks(1)
        MONTHLY -> from.plusMonthsClamped(1)
        QUARTERLY -> from.plusMonthsClamped(3)
        YEARLY -> from.plusMonthsClamped(12)
    }

    companion object {
        fun fromName(name: String): BillingCycle =
            entries.firstOrNull { it.name == name } ?: MONTHLY

        /** 加 N 个月并做月末钳制（31 日加 1 月落到 2 月末，而非跳到 3 月初）。 */
        private fun LocalDate.plusMonthsClamped(months: Long): LocalDate {
            val target = plusMonths(months)
            if (dayOfMonth <= target.lengthOfMonth()) return target
            return target.withDayOfMonth(target.lengthOfMonth())
        }
    }
}

/**
 * 一条订阅。
 *
 * 金额单位为 [currency] 主单位；日期均为 UTC 当日零点的 epoch 毫秒，与净值快照口径一致。
 */
data class Subscription(
    val id: Long = 0,
    val userId: Long,
    val name: String,
    val category: SubscriptionCategory,
    val note: String? = null,
    val currency: Currency,
    /** 每周期费用（主单位），配合 [cycle] 折算月均。 */
    val amount: Double,
    val cycle: BillingCycle,
    /** 首次扣费日（推算续费日的锚点）。 */
    val firstBillEpochMs: Long,
    /** 下次续费日（自动顺延，始终指向未来）。 */
    val nextRenewalEpochMs: Long,
    /** 提前提醒天数：0=不提醒 / 1 / 3 / 7。 */
    val reminderDaysBefore: Int,
    /** 支付方式（选填自由文本）。 */
    val paymentMethod: String? = null,
    /** false=已停用（不计入支出统计、不再提醒）。 */
    val active: Boolean = true,
    val createdAtEpochMs: Long = 0,
    val updatedAtEpochMs: Long = 0,
) {
    /** 月均支出（主币种，未换算）。 */
    val monthlyAmount: Double get() = amount * cycle.monthlyFactor

    /** 年化支出（主币种，未换算）＝ 月均 × 12。 */
    val yearlyAmount: Double get() = monthlyAmount * 12.0

    companion object {
        val ZONE: ZoneOffset = ZoneOffset.UTC

        fun epochMsOf(date: LocalDate): Long =
            date.atStartOfDay(ZONE).toInstant().toEpochMilli()

        fun dateOf(epochMs: Long): LocalDate =
            LocalDate.ofInstant(Instant.ofEpochMilli(epochMs), ZONE)
    }
}
