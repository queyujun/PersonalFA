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
    val maturityDateEpochMs: Long? = null,
    val depositType: String? = null,
    // 公司股权
    val sharePercent: Double? = null,
    // 负债（monthlyPayment = 每月还款本金；repaymentDay 每月还款日 1-31，null=每月最后一天；
    // lastRepaidYearMonth 已补扣到的年月 YYYYMM，防重复扣款）
    val liabilityType: String? = null,
    val monthlyPayment: Double? = null,
    val repaymentDay: Int? = null,
    val lastRepaidYearMonth: Int? = null,
    // 房产指数估算：autoEstimate=是否按 70 城二手住宅指数自动估算；
    // valueBaseDateEpochMs=录入值（manualValue）作为基准的月份时间戳；
    // estimatedValue=sync 派生的估算现值缓存（与 currentPrice 同构，基准不变）。
    val autoEstimate: Boolean? = null,
    val valueBaseDateEpochMs: Long? = null,
    val estimatedValue: Double? = null,
    // 其他(MISC)备注；可为空，向后兼容旧备份（缺省 → null）
    val note: String? = null,
    // 场外基金子分类：autoFetchNav=true=中国大陆（在线抓取净值），null/false=其他（手录净值）。
    // 可为空，向后兼容旧 OTC 持仓（缺省 → null，视为手录）。
    val autoFetchNav: Boolean? = null,
    val createdAtEpochMs: Long = 0,
    val updatedAtEpochMs: Long = 0,
) {
    val category: AssetCategory get() = type.category
}
