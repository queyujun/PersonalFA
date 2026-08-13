package com.yingjing.pfa.domain.model

/** 资产在投资组合中的归类（用于分组、饼图、分类合计）。 */
enum class AssetCategory(val displayName: String, val isLiability: Boolean = false) {
    REAL_ESTATE("房产"),
    DEPOSIT("存款"),
    STOCK("股票"),
    GOLD("黄金"),
    BOND("国债"),
    EQUITY("公司股权"),
    CRYPTO("区块链"),
    LIABILITY("负债", isLiability = true),
}

/**
 * 资产类型（录入维度）。
 *
 * - [category] 决定在组合中的归类：A股/港股/美股/账户现金 → 股票；黄金ETF → 黄金；国债ETF → 国债。
 * - [marketPriced] 为 true 表示需要行情价（P3 起每日自动取价）。
 */
enum class AssetType(
    val displayName: String,
    val category: AssetCategory,
    val marketPriced: Boolean,
) {
    REAL_ESTATE("房产", AssetCategory.REAL_ESTATE, false),
    DEPOSIT("存款", AssetCategory.DEPOSIT, false),
    A_SHARE("A股", AssetCategory.STOCK, true),
    HK_STOCK("港股", AssetCategory.STOCK, true),
    US_STOCK("美股", AssetCategory.STOCK, true),
    ACCOUNT_CASH("账户现金", AssetCategory.STOCK, false),
    GOLD_ETF("黄金ETF", AssetCategory.GOLD, true),
    BOND_ETF("国债ETF", AssetCategory.BOND, true),
    EQUITY("公司股权", AssetCategory.EQUITY, false),
    CRYPTO("区块链", AssetCategory.CRYPTO, true),
    LIABILITY("负债", AssetCategory.LIABILITY, false);

    val isLiability: Boolean get() = category.isLiability
}
