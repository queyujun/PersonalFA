package com.yingjing.pfa.data.backup

import kotlinx.serialization.Serializable

/** 备份文件的 JSON 结构（明文，加密由 BackupCrypto 负责）。 */
@Serializable
data class BackupData(
    val version: Int = 1,
    val users: List<BackupUser> = emptyList(),
    val holdings: List<BackupHolding> = emptyList(),
    val snapshots: List<BackupSnapshot> = emptyList(),
    val categorySnapshots: List<BackupCategorySnapshot> = emptyList(),
    val alerts: List<BackupAlert> = emptyList(),
    /**
     * 导出时刻的汇率（可选）。恢复时写回 fx DataStore，避免换算退化为不换算。
     * 老备份无此字段 → null → 不写回（向后兼容，靠后台刷新补救）。
     */
    val fxRates: BackupFxRates? = null,
)

/** 备份中的汇率快照（以人民币 CNY 为基准：1 单位外币 = ? 人民币）。 */
@Serializable
data class BackupFxRates(
    val usdToCny: Double,
    val hkdToCny: Double,
)

@Serializable
data class BackupCategorySnapshot(
    val id: Long,
    val userId: Long,
    val dayEpochDay: Long,
    val category: String,
    val amount: Double,
)

@Serializable
data class BackupUser(
    val id: Long,
    val username: String,
    val passwordHash: String,
    val defaultCurrency: String,
    val createdAt: Long,
    val nickname: String? = null,
    val gender: String? = null,
    val age: Int? = null,
)

@Serializable
data class BackupHolding(
    val id: Long,
    val userId: Long,
    val type: String,
    val name: String,
    val currency: String,
    val quantity: Double?,
    val costPrice: Double?,
    val currentPrice: Double?,
    val symbol: String?,
    val manualValue: Double?,
    val city: String?,
    val areaSqm: Double?,
    val annualRatePercent: Double?,
    val startDateEpochMs: Long?,
    val maturityDateEpochMs: Long?,
    val depositType: String?,
    val sharePercent: Double?,
    val liabilityType: String?,
    val monthlyPayment: Double?,
    val repaymentDay: Int? = null,
    val lastRepaidYearMonth: Int? = null,
    val autoEstimate: Boolean? = null,
    val valueBaseDateEpochMs: Long? = null,
    val estimatedValue: Double? = null,
    val note: String? = null,
    val autoFetchNav: Boolean? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupSnapshot(
    val id: Long,
    val userId: Long,
    val dayEpochDay: Long,
    val currency: String,
    val totalAssets: Double,
    val totalLiabilities: Double,
    val netWorth: Double,
    val createdAt: Long,
)

@Serializable
data class BackupAlert(
    val id: Long,
    val userId: Long,
    val category: String,
    val severity: String,
    val title: String,
    val body: String,
    val refHoldingId: Long?,
    val dedupKey: String,
    val createdAt: Long,
    val read: Boolean,
)
