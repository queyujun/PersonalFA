package com.yingjing.pfa.domain.model

/**
 * 单笔持仓（覆盖全部资产类别的宽模型；各字段按 [type] 取用）。
 *
 * 金额单位为该持仓 [currency] 的主单位（如元 / 港元 / 美元）。跨币种换算在 P3（汇率）后进行。
 */
data class Holding(
    val id: Long = 0,
    val userId: Long,
    val type: AssetType,
    val name: String,
    val currency: Currency,
    // 市场型（股票 / ETF / 代币）：数量 + 成本价 + 现价
    val quantity: Double? = null,
    val costPrice: Double? = null,
    val currentPrice: Double? = null,
    val symbol: String? = null,
    // 手动估值型（房产 / 公司股权 / 账户现金）与存款本金、负债金额
    val manualValue: Double? = null,
    // 房产
    val city: String? = null,
    val areaSqm: Double? = null,
    // 存款
    val annualRatePercent: Double? = null,
    val startDateEpochMs: Long? = null,
    val depositType: String? = null,
    // 公司股权
    val sharePercent: Double? = null,
    // 负债
    val liabilityType: String? = null,
    val monthlyPayment: Double? = null,
    val createdAtEpochMs: Long = 0,
    val updatedAtEpochMs: Long = 0,
) {
    val category: AssetCategory get() = type.category
}
