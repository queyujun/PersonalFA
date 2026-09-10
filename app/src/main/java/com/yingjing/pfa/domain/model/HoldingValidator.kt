package com.yingjing.pfa.domain.model

import com.yingjing.pfa.R
import com.yingjing.pfa.core.validation.ValidationFailure

/** 持仓录入校验，返回 [ValidationFailure]；null 表示通过。文案由渲染端经 StringResolver 解析。 */
object HoldingValidator {

    fun validate(holding: Holding): ValidationFailure? {
        if (holding.name.isBlank()) return ValidationFailure(R.string.err_holding_name)
        return when (holding.type) {
            AssetType.A_SHARE,
            AssetType.HK_STOCK,
            AssetType.US_STOCK,
            AssetType.GOLD_ETF,
            AssetType.BOND_ETF,
            AssetType.CRYPTO,
            AssetType.OTC_FUND -> {
                if (holding.symbol.isNullOrBlank()) ValidationFailure(R.string.err_holding_symbol)
                else if ((holding.quantity ?: 0.0) <= 0.0) ValidationFailure(R.string.err_holding_qty)
                else null
            }

            AssetType.PHYSICAL_GOLD -> {
                if ((holding.quantity ?: 0.0) <= 0.0) ValidationFailure(R.string.err_holding_grams)
                else null
            }

            AssetType.MISC ->
                if ((holding.manualValue ?: -1.0) < 0.0) ValidationFailure(R.string.err_holding_value) else null

            AssetType.ACCOUNT_CASH ->
                if ((holding.manualValue ?: -1.0) < 0.0) ValidationFailure(R.string.err_holding_cash) else null

            // 开启指数估算时城市必须是 70 城精确名（SyncManager 按精确匹配收集，
            // 非列表名会被静默跳过估算），在保存口拦截而非等同步时无声失效。
            AssetType.REAL_ESTATE -> when {
                (holding.manualValue ?: -1.0) < 0.0 -> ValidationFailure(R.string.err_holding_value)
                holding.autoEstimate == true && !HousePriceCities.contains(holding.city) ->
                    ValidationFailure(R.string.err_holding_city)
                else -> null
            }

            AssetType.EQUITY ->
                if ((holding.manualValue ?: -1.0) < 0.0) ValidationFailure(R.string.err_holding_value) else null

            AssetType.DEPOSIT -> when {
                (holding.manualValue ?: -1.0) < 0.0 -> ValidationFailure(R.string.err_holding_principal)
                (holding.annualRatePercent ?: -1.0) < 0.0 -> ValidationFailure(R.string.err_holding_rate)
                else -> null
            }

            AssetType.LIABILITY ->
                if ((holding.manualValue ?: -1.0) < 0.0) ValidationFailure(R.string.err_holding_liability) else null
        }
    }
}
