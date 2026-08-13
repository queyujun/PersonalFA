package com.yingjing.pfa.domain.model

/** 持仓录入校验，返回中文错误信息；null 表示通过。 */
object HoldingValidator {

    fun validate(holding: Holding): String? {
        if (holding.name.isBlank()) return "请输入名称"
        return when (holding.type) {
            AssetType.A_SHARE,
            AssetType.HK_STOCK,
            AssetType.US_STOCK,
            AssetType.GOLD_ETF,
            AssetType.BOND_ETF,
            AssetType.CRYPTO -> {
                if (holding.symbol.isNullOrBlank()) "请输入代码 / 名称"
                else if ((holding.quantity ?: 0.0) <= 0.0) "请输入持有数量"
                else null
            }

            AssetType.ACCOUNT_CASH ->
                if ((holding.manualValue ?: -1.0) < 0.0) "请输入现金金额" else null

            AssetType.REAL_ESTATE ->
                if ((holding.manualValue ?: -1.0) < 0.0) "请输入当前估值" else null

            AssetType.EQUITY ->
                if ((holding.manualValue ?: -1.0) < 0.0) "请输入当前估值" else null

            AssetType.DEPOSIT -> when {
                (holding.manualValue ?: -1.0) < 0.0 -> "请输入本金金额"
                (holding.annualRatePercent ?: -1.0) < 0.0 -> "请输入年化利率"
                else -> null
            }

            AssetType.LIABILITY ->
                if ((holding.manualValue ?: -1.0) < 0.0) "请输入负债金额" else null
        }
    }
}
