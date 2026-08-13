package com.yingjing.pfa.domain.model

/** 持仓估值与收益计算（纯函数，可单元测试）。金额均为持仓自身币种。 */
object HoldingValue {

    private const val MS_PER_DAY = 86_400_000.0
    private const val DAYS_PER_YEAR = 365.0

    /** 当前市值；负债返回负值。 */
    fun currentValue(holding: Holding, nowMs: Long): Double = when (holding.type) {
        AssetType.A_SHARE,
        AssetType.HK_STOCK,
        AssetType.US_STOCK,
        AssetType.GOLD_ETF,
        AssetType.BOND_ETF,
        AssetType.CRYPTO ->
            (holding.quantity ?: 0.0) * (holding.currentPrice ?: holding.costPrice ?: 0.0)

        AssetType.ACCOUNT_CASH,
        AssetType.REAL_ESTATE,
        AssetType.EQUITY ->
            holding.manualValue ?: 0.0

        AssetType.DEPOSIT ->
            depositValue(holding, nowMs)

        AssetType.LIABILITY ->
            -(holding.manualValue ?: 0.0)
    }

    /** 存款按年化利率每日单利计息：本金 + 本金 × 年化 × 天数/365。 */
    fun depositValue(holding: Holding, nowMs: Long): Double {
        val principal = holding.manualValue ?: 0.0
        val rate = (holding.annualRatePercent ?: 0.0) / 100.0
        val start = holding.startDateEpochMs ?: nowMs
        val days = ((nowMs - start).coerceAtLeast(0L)) / MS_PER_DAY
        return principal + principal * rate * (days / DAYS_PER_YEAR)
    }

    /** 持仓收益 = 现值 − 成本；无成本信息返回 null。 */
    fun profit(holding: Holding, nowMs: Long): Double? = when (holding.type) {
        AssetType.A_SHARE,
        AssetType.HK_STOCK,
        AssetType.US_STOCK,
        AssetType.GOLD_ETF,
        AssetType.BOND_ETF,
        AssetType.CRYPTO -> {
            val cost = holding.costPrice ?: return null
            val quantity = holding.quantity ?: 0.0
            currentValue(holding, nowMs) - cost * quantity
        }
        AssetType.EQUITY -> {
            val cost = holding.costPrice ?: return null
            (holding.manualValue ?: 0.0) - cost
        }
        else -> null
    }
}
