package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Holding

/** 将持仓映射为新浪行情代码（不支持行情的类型返回 null）。 */
object SinaSymbolMapper {

    fun sinaCode(holding: Holding): String? {
        val symbol = holding.symbol?.trim() ?: return null
        if (symbol.isBlank()) return null
        return when (holding.type) {
            AssetType.A_SHARE, AssetType.GOLD_ETF, AssetType.BOND_ETF -> aShareCode(symbol)
            AssetType.HK_STOCK -> "rt_hk" + symbol.filter { it.isDigit() }.padStart(5, '0')
            AssetType.US_STOCK -> "gb_" + symbol.lowercase()
            else -> null
        }
    }

    private fun aShareCode(code: String): String {
        val digits = code.filter { it.isDigit() }
        val prefix = if (digits.firstOrNull() in listOf('5', '6', '9')) "sh" else "sz"
        return prefix + digits
    }
}
