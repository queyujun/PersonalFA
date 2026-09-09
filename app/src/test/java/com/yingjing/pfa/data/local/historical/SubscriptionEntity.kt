// Reconstructed from git 395072d (identical local entities at 0999ccc^).
// Test-only historical source; package relocated, DAO accessors omitted, schema export enabled.
package com.yingjing.pfa.data.local.historical

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 订阅宽表：周期性订阅支出（视频/音乐/AI 服务等）。
 * cycle 存 BillingCycle.name，currency 存 Currency.code，category 存 SubscriptionCategory.name。
 */
@Entity(
    tableName = "subscriptions",
    indices = [Index(value = ["userId"])],
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val name: String,
    val category: String,
    val note: String? = null,
    val currency: String,
    /** 每周期费用（主单位），配合 [cycle] 折算月均。 */
    val amount: Double,
    /** 周期（WEEKLY/MONTHLY/QUARTERLY/YEARLY）。 */
    val cycle: String,
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
    val createdAt: Long,
    val updatedAt: Long,
)
