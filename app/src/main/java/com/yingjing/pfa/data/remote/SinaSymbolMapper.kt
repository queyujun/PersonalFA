package com.yingjing.pfa.data.remote

import com.yingjing.pfa.domain.model.AssetType
import com.yingjing.pfa.domain.model.Holding

/** 将持仓映射为新浪行情代码（不支持行情的类型返回 null）。 */
object SinaSymbolMapper {

    fun sinaCode(holding: Holding): String? {
        // 实物金无 symbol：固定取新浪伦敦金现货（hf_XAU，USD/盎司），
        // 由 QuoteRepositoryImpl 按汇率 + 克数换算为持仓币种的「每克」价。
        if (holding.type == AssetType.PHYSICAL_GOLD) return SPOT_GOLD
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

    // 新浪伦敦金现货代码（hf_XAU，USD/盎司）
    private const val SPOT_GOLD = "hf_XAU"
}
