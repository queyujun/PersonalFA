package com.yingjing.pfa.domain.model

import androidx.annotation.StringRes
import com.yingjing.pfa.R

/** 资产在投资组合中的归类（用于分组、饼图、分类合计）。 */
enum class AssetCategory(
    @StringRes val displayRes: Int,
    val isLiability: Boolean = false,
) {
    REAL_ESTATE(R.string.cat_real_estate),
    DEPOSIT(R.string.cat_deposit),
    STOCK(R.string.cat_stock),
    GOLD(R.string.cat_gold),
    BOND(R.string.cat_bond),
    EQUITY(R.string.cat_equity),
    CRYPTO(R.string.cat_crypto),
    OTC_FUND(R.string.cat_otc_fund),
    MISC(R.string.cat_misc),
    LIABILITY(R.string.cat_liability, isLiability = true),
}

/**
 * 资产类型（录入维度）。
 *
 * - [category] 决定在组合中的归类：A股/港股/美股/账户现金 → 股票；黄金ETF/实物金 → 黄金；国债ETF → 国债。
 * - [marketPriced] 为 true 表示需要行情价（P3 起每日自动取价）。
 */
enum class AssetType(
    @StringRes val displayRes: Int,
    val category: AssetCategory,
    val marketPriced: Boolean,
) {
    REAL_ESTATE(R.string.type_real_estate, AssetCategory.REAL_ESTATE, false),
    DEPOSIT(R.string.type_deposit, AssetCategory.DEPOSIT, false),
    A_SHARE(R.string.type_a_share, AssetCategory.STOCK, true),
    HK_STOCK(R.string.type_hk_stock, AssetCategory.STOCK, true),
    US_STOCK(R.string.type_us_stock, AssetCategory.STOCK, true),
    ACCOUNT_CASH(R.string.type_account_cash, AssetCategory.STOCK, false),
    GOLD_ETF(R.string.type_gold_etf, AssetCategory.GOLD, true),
    PHYSICAL_GOLD(R.string.type_physical_gold, AssetCategory.GOLD, true),
    BOND_ETF(R.string.type_bond_etf, AssetCategory.BOND, true),
    EQUITY(R.string.type_equity, AssetCategory.EQUITY, false),
    CRYPTO(R.string.type_crypto, AssetCategory.CRYPTO, true),
    OTC_FUND(R.string.type_otc_fund, AssetCategory.OTC_FUND, false),
    MISC(R.string.type_misc, AssetCategory.MISC, false),
    LIABILITY(R.string.type_liability, AssetCategory.LIABILITY, false);

    val isLiability: Boolean get() = category.isLiability
}
