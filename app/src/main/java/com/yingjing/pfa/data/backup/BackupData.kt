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
    /** 订阅（可选）。老备份无此字段 → 空列表（向后兼容）。 */
    val subscriptions: List<BackupSubscription> = emptyList(),
    /**
     * 导出时刻的汇率（可选）。恢复时写回 fx DataStore，避免换算退化为不换算。
     * 老备份无此字段 → null → 不写回（向后兼容，靠后台刷新补救）。
     */
    val fxRates: BackupFxRates? = null,
    /** AI 生成结果记录（可选）。老备份无此字段 → 空列表（向后兼容）。 */
    val aiRecords: List<BackupAiRecord> = emptyList(),
    /**
     * AI 配置（可选，不含 API Key——密钥走加密库，换机恢复失效，须重录）。
     * 老备份无此字段 → null（由 aiProfiles 分支处理，向后兼容）。
     */
    val aiSettings: BackupAiSettings? = null,
    /**
     * AI 多配置档案列表（可选，不含 API Key）。老备份无此字段 → 空列表：
     * 有 aiSettings 时映射为对应档案；两者皆无 → 不覆盖本机配置。
     */
    val aiProfiles: List<BackupAiProfile> = emptyList(),
    /** 导出时刻的生效档案 id（可选）。老备份 → null。 */
    val aiActiveProfileId: String? = null,
    /** 导出时刻的全局隐私同意（可选）。老备份 → null → 不写回。 */
    val aiConsented: Boolean? = null,
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

@Serializable
data class BackupSubscription(
    val id: Long,
    val userId: Long,
    val name: String,
    val category: String,
    val note: String? = null,
    val currency: String,
    val amount: Double,
    val cycle: String,
    val firstBill: Long,
    val nextRenewal: Long,
    val reminderDaysBefore: Int,
    val paymentMethod: String? = null,
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 备份中的 AI 生成结果记录（Markdown 原文，随库加密备份）。 */
@Serializable
data class BackupAiRecord(
    val id: Long,
    val userId: Long,
    /** report = 资产报告；insight = 持仓分析。 */
    val kind: String,
    val title: String,
    val model: String? = null,
    val markdown: String,
    val createdAt: Long,
)

/** 备份中的 AI 配置（老版单配置格式；不含 API Key——密钥须重录）。 */
@Serializable
data class BackupAiSettings(
    val providerId: String,
    val baseUrl: String,
    val model: String,
    /** chat_completions / responses。 */
    val protocol: String,
    val includeDetails: Boolean,
    /** 已确认过隐私提示 → 恢复后免重复弹窗。 */
    val consented: Boolean,
)

/** 备份中的 AI 配置档案（不含 API Key——密钥加密存本机数据库，换机恢复失效，须重录）。 */
@Serializable
data class BackupAiProfile(
    val id: String,
    /** 自定义档案显示名；预设档案留空。 */
    val name: String = "",
    /** 服务商预设 id：预设档案 = 自身；自定义 = 模板预设 id 或 "custom"。 */
    val providerId: String,
    val baseUrl: String = "",
    val model: String = "",
    /** chat_completions / responses。 */
    val protocol: String = "chat_completions",
    val includeDetails: Boolean = true,
    /** 输出语气档 id（analyst / companion）；老备份无此字段 → analyst。 */
    val tone: String = "analyst",
)
